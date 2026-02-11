# 전술적 DDD (Tactical DDD)

도메인 모델의 구체적인 구현 패턴.

## Aggregate 설계 원칙

### Aggregate란

- 하나의 단위로 취급되는 도메인 객체 클러스터
- 정확히 하나의 Entity가 Aggregate Root가 됨
- 외부에서는 Root를 통해서만 접근

### 설계 원칙

| 원칙 | 설명 |
|------|------|
| **작은 Aggregate 선호** | 트랜잭션 범위 최소화 |
| **불변식(Invariant) 보호** | 비즈니스 규칙 강제 |
| **트랜잭션 경계** | 하나의 트랜잭션 = 하나의 Aggregate |
| **ID 참조** | 다른 Aggregate는 ID로만 참조 |

### 예시

```typescript
// Aggregate Root
class Order {
  private id: OrderId;
  private customerId: CustomerId; // ID 참조 (다른 Aggregate)
  private items: OrderItem[];     // 포함된 Entity
  private status: OrderStatus;    // Value Object
  private domainEvents: DomainEvent[] = [];

  // 불변식: 주문 항목은 최소 1개
  addItem(product: ProductId, quantity: number): void {
    if (this.status !== OrderStatus.DRAFT) {
      throw new Error('승인된 주문은 수정할 수 없습니다');
    }
    this.items.push(new OrderItem(product, quantity));
  }

  confirm(): void {
    if (this.items.length === 0) {
      throw new Error('주문 항목이 없습니다');
    }
    this.status = OrderStatus.CONFIRMED;
    this.addDomainEvent(new OrderConfirmedEvent(this.id));
  }

  private addDomainEvent(event: DomainEvent): void {
    this.domainEvents.push(event);
  }

  pullDomainEvents(): DomainEvent[] {
    const events = [...this.domainEvents];
    this.domainEvents = [];
    return events;
  }
}
```

### Aggregate 경계 결정

```
✅ 좋은 Aggregate 설계:
┌─────────────────────────┐
│        Order            │ ← Aggregate Root
├─────────────────────────┤
│  - OrderItem (Entity)   │ ← Order 없이 존재 불가
│  - ShippingAddress (VO) │
│  - OrderStatus (VO)     │
└─────────────────────────┘

❌ 나쁜 Aggregate 설계:
┌─────────────────────────┐
│        Order            │
├─────────────────────────┤
│  - Customer (Entity)    │ ← 별도 Aggregate여야 함
│  - Product (Entity)     │ ← 별도 Aggregate여야 함
│  - PaymentInfo          │ ← 별도 Aggregate여야 함
└─────────────────────────┘
```

---

## Entity vs Value Object

| 구분 | Entity | Value Object |
|------|--------|--------------|
| **식별자** | 고유 ID 보유 | 없음 (값으로 비교) |
| **변경** | 변경 가능 | 불변 (새 인스턴스 생성) |
| **생명주기** | 추적 필요 | 교체 가능 |
| **예시** | User, Order | Money, Email, Address |

### Entity 예시

```typescript
class User {
  constructor(
    private readonly id: UserId,
    private email: Email,
    private name: string,
  ) {}

  // 동등성: ID로 비교
  equals(other: User): boolean {
    return this.id.equals(other.id);
  }

  // 상태 변경 가능
  changeEmail(newEmail: Email): void {
    this.email = newEmail;
  }
}
```

### Value Object 예시

```typescript
class Money {
  constructor(
    private readonly amount: number,
    private readonly currency: Currency
  ) {
    if (amount < 0) throw new Error('금액은 음수 불가');
  }

  // 불변: 새 인스턴스 반환
  add(other: Money): Money {
    if (!this.currency.equals(other.currency)) {
      throw new Error('통화 불일치');
    }
    return new Money(this.amount + other.amount, this.currency);
  }

  // 동등성: 값으로 비교
  equals(other: Money): boolean {
    return this.amount === other.amount &&
           this.currency.equals(other.currency);
  }
}

class Email {
  private readonly value: string;

  constructor(value: string) {
    if (!this.isValid(value)) {
      throw new Error('유효하지 않은 이메일');
    }
    this.value = value;
  }

  private isValid(email: string): boolean {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
  }

  toString(): string {
    return this.value;
  }

  equals(other: Email): boolean {
    return this.value === other.value;
  }
}

class Address {
  constructor(
    private readonly street: string,
    private readonly city: string,
    private readonly zipCode: string,
    private readonly country: string,
  ) {}

  equals(other: Address): boolean {
    return this.street === other.street &&
           this.city === other.city &&
           this.zipCode === other.zipCode &&
           this.country === other.country;
  }
}
```

---

## Domain Event 패턴

### Domain Event란

- 도메인에서 발생한 의미 있는 사실
- 과거형으로 명명 (OrderPlaced, PaymentCompleted)
- 불변 객체, 타임스탬프 포함

### 활용

- Aggregate 간 느슨한 결합
- Eventual Consistency 구현
- 감사(Audit) 로그

### 구현

```typescript
// Domain Event 기본 인터페이스
interface DomainEvent {
  readonly eventId: string;
  readonly occurredOn: Date;
  readonly aggregateId: string;
}

// 구체적인 이벤트
class OrderPlacedEvent implements DomainEvent {
  readonly eventId = crypto.randomUUID();
  readonly occurredOn = new Date();

  constructor(
    readonly aggregateId: string,  // orderId
    readonly customerId: string,
    readonly totalAmount: number,
    readonly items: Array<{ productId: string; quantity: number }>,
  ) {}
}

class OrderCancelledEvent implements DomainEvent {
  readonly eventId = crypto.randomUUID();
  readonly occurredOn = new Date();

  constructor(
    readonly aggregateId: string,
    readonly reason: string,
  ) {}
}
```

### 이벤트 발행

```typescript
// Aggregate에서 이벤트 수집
class Order {
  private domainEvents: DomainEvent[] = [];

  place(): void {
    this.status = OrderStatus.PLACED;
    this.addDomainEvent(new OrderPlacedEvent(
      this.id,
      this.customerId,
      this.totalAmount,
      this.items.map(i => ({ productId: i.productId, quantity: i.quantity })),
    ));
  }

  private addDomainEvent(event: DomainEvent): void {
    this.domainEvents.push(event);
  }

  pullDomainEvents(): DomainEvent[] {
    const events = [...this.domainEvents];
    this.domainEvents = [];
    return events;
  }
}

// Repository에서 이벤트 발행
class OrderRepository {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly eventPublisher: EventPublisher,
  ) {}

  async save(order: Order): Promise<void> {
    await this.prisma.order.upsert({
      where: { id: order.id },
      create: this.toPersistence(order),
      update: this.toPersistence(order),
    });

    // 저장 후 이벤트 발행
    const events = order.pullDomainEvents();
    for (const event of events) {
      await this.eventPublisher.publish(event);
    }
  }
}
```

---

## Repository 패턴

### 원칙

- Aggregate Root당 하나의 Repository
- 도메인 계층에 인터페이스, 인프라 계층에 구현
- 컬렉션과 유사한 인터페이스 제공

### 인터페이스 (Domain Layer)

```typescript
// domain/order/OrderRepository.ts
interface OrderRepository {
  findById(id: OrderId): Promise<Order | null>;
  findByCustomerId(customerId: CustomerId): Promise<Order[]>;
  save(order: Order): Promise<void>;
  delete(order: Order): Promise<void>;
}
```

### 구현 (Infrastructure Layer)

```typescript
// infrastructure/persistence/PrismaOrderRepository.ts
@Injectable()
class PrismaOrderRepository implements OrderRepository {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly eventPublisher: EventPublisher,
  ) {}

  async findById(id: OrderId): Promise<Order | null> {
    const data = await this.prisma.order.findUnique({
      where: { id: id.value },
      include: { items: true },
    });

    if (!data) return null;
    return this.toDomain(data);
  }

  async save(order: Order): Promise<void> {
    const data = this.toPersistence(order);

    await this.prisma.$transaction(async (tx) => {
      await tx.order.upsert({
        where: { id: data.id },
        create: data,
        update: data,
      });

      // 주문 항목 동기화
      await tx.orderItem.deleteMany({ where: { orderId: data.id } });
      await tx.orderItem.createMany({ data: data.items });
    });

    // 도메인 이벤트 발행
    const events = order.pullDomainEvents();
    await this.eventPublisher.publishAll(events);
  }

  private toDomain(data: OrderWithItems): Order {
    return Order.reconstitute({
      id: new OrderId(data.id),
      customerId: new CustomerId(data.customerId),
      items: data.items.map(i => new OrderItem(
        new ProductId(i.productId),
        i.quantity,
        new Money(i.price, Currency.KRW),
      )),
      status: OrderStatus[data.status],
      createdAt: data.createdAt,
    });
  }

  private toPersistence(order: Order) {
    return {
      id: order.id.value,
      customerId: order.customerId.value,
      status: order.status.toString(),
      totalAmount: order.totalAmount.value,
      items: order.items.map(i => ({
        productId: i.productId.value,
        quantity: i.quantity,
        price: i.price.value,
      })),
    };
  }
}
```

---

## Domain Service

Entity나 Value Object에 속하지 않는 도메인 로직:

```typescript
// 여러 Aggregate에 걸친 로직
@Injectable()
class OrderPricingService {
  constructor(
    private readonly productRepository: ProductRepository,
    private readonly discountService: DiscountService,
  ) {}

  async calculateTotal(
    items: Array<{ productId: ProductId; quantity: number }>,
    customerId: CustomerId,
  ): Promise<Money> {
    let total = Money.zero(Currency.KRW);

    for (const item of items) {
      const product = await this.productRepository.findById(item.productId);
      if (!product) throw new ProductNotFoundError(item.productId);

      const subtotal = product.price.multiply(item.quantity);
      total = total.add(subtotal);
    }

    // 고객별 할인 적용
    const discount = await this.discountService.getDiscount(customerId);
    return total.applyDiscount(discount);
  }
}
```

---

## 실전 체크리스트

### Aggregate 설계

- [ ] 불변식이 Aggregate 내에서 보호되는가?
- [ ] 트랜잭션 경계가 하나의 Aggregate인가?
- [ ] 다른 Aggregate는 ID로만 참조하는가?
- [ ] Aggregate가 너무 크지 않은가?

### Value Object 사용

- [ ] 원시 타입 대신 VO를 사용했는가? (string → Email)
- [ ] VO는 불변인가?
- [ ] 값 검증이 생성자에서 이루어지는가?

### Domain Event

- [ ] 이벤트 이름이 과거형인가?
- [ ] 이벤트에 필요한 모든 정보가 포함되어 있는가?
- [ ] 이벤트 발행이 트랜잭션과 분리되어 있는가?
