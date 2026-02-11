# Vertical Slice Architecture (VSA)

기술 계층 대신 기능(Feature) 단위로 코드를 구성하는 아키텍처.

## 계층형 vs 수직 슬라이스

```
계층형 (Traditional)              수직 슬라이스 (VSA)
src/                              src/
├── controllers/                  ├── features/
│   ├── UserController.ts         │   ├── create-user/
│   ├── OrderController.ts        │   │   ├── CreateUserCommand.ts
│   └── ProductController.ts      │   │   ├── CreateUserHandler.ts
├── services/                     │   │   └── CreateUserEndpoint.ts
│   ├── UserService.ts            │   ├── get-user/
│   ├── OrderService.ts           │   │   ├── GetUserQuery.ts
│   └── ProductService.ts         │   │   ├── GetUserHandler.ts
├── repositories/                 │   │   └── GetUserEndpoint.ts
│   └── ...                       │   └── delete-user/
└── entities/                     │       └── ...
    └── ...                       └── shared/
                                      └── ...
```

---

## 핵심 원칙

| 원칙 | 설명 |
|------|------|
| **기능 격리** | 각 슬라이스는 독립적으로 동작 |
| **슬라이스 간 결합 최소화** | 다른 슬라이스 직접 참조 금지 |
| **슬라이스 내 결합 최대화** | 관련 코드는 같은 폴더에 배치 |
| **CQRS 자연 통합** | 각 슬라이스가 Command 또는 Query |

---

## VSA + CQRS 구조

```
src/features/
├── orders/
│   ├── commands/
│   │   ├── create-order/
│   │   │   ├── CreateOrderCommand.ts
│   │   │   ├── CreateOrderHandler.ts
│   │   │   ├── CreateOrderValidator.ts
│   │   │   └── CreateOrderEndpoint.ts
│   │   └── cancel-order/
│   │       ├── CancelOrderCommand.ts
│   │       ├── CancelOrderHandler.ts
│   │       └── CancelOrderEndpoint.ts
│   ├── queries/
│   │   ├── get-order/
│   │   │   ├── GetOrderQuery.ts
│   │   │   ├── GetOrderHandler.ts
│   │   │   └── GetOrderEndpoint.ts
│   │   └── list-orders/
│   │       ├── ListOrdersQuery.ts
│   │       ├── ListOrdersHandler.ts
│   │       └── ListOrdersEndpoint.ts
│   └── shared/                   # 이 기능 내 공유 코드
│       ├── Order.ts              # 엔티티
│       └── OrderRepository.ts
└── shared/                       # 전역 공유 코드
    ├── database/
    └── middleware/
```

---

## 슬라이스 구현 예시

### Command 슬라이스

```typescript
// src/features/orders/commands/create-order/CreateOrderCommand.ts
export class CreateOrderCommand {
  constructor(
    public readonly customerId: string,
    public readonly items: Array<{ productId: string; quantity: number }>,
  ) {}
}

// src/features/orders/commands/create-order/CreateOrderValidator.ts
import { z } from 'zod';

export const createOrderSchema = z.object({
  customerId: z.string().uuid(),
  items: z.array(z.object({
    productId: z.string().uuid(),
    quantity: z.number().positive(),
  })).min(1),
});

export type CreateOrderDto = z.infer<typeof createOrderSchema>;

// src/features/orders/commands/create-order/CreateOrderHandler.ts
@Injectable()
export class CreateOrderHandler {
  constructor(
    private readonly orderRepository: OrderRepository,
    private readonly productService: ProductService,
    private readonly eventBus: EventBus,
  ) {}

  async execute(command: CreateOrderCommand): Promise<Order> {
    // 1. 상품 검증
    const products = await this.productService.validateProducts(command.items);

    // 2. 주문 생성
    const order = Order.create({
      customerId: command.customerId,
      items: products.map(p => ({
        productId: p.id,
        price: p.price,
        quantity: command.items.find(i => i.productId === p.id)!.quantity,
      })),
    });

    // 3. 저장
    await this.orderRepository.save(order);

    // 4. 이벤트 발행
    this.eventBus.publish(new OrderCreatedEvent(order));

    return order;
  }
}

// src/features/orders/commands/create-order/CreateOrderEndpoint.ts
@Controller('orders')
export class CreateOrderEndpoint {
  constructor(private readonly handler: CreateOrderHandler) {}

  @Post()
  @ApiOperation({ summary: '주문 생성' })
  @ApiResponse({ status: 201, type: OrderResponseDto })
  async create(@Body() dto: CreateOrderDto): Promise<OrderResponseDto> {
    const validatedDto = createOrderSchema.parse(dto);
    const command = new CreateOrderCommand(
      validatedDto.customerId,
      validatedDto.items,
    );
    const order = await this.handler.execute(command);
    return OrderResponseDto.from(order);
  }
}
```

### Query 슬라이스

```typescript
// src/features/orders/queries/get-order/GetOrderQuery.ts
export class GetOrderQuery {
  constructor(public readonly orderId: string) {}
}

// src/features/orders/queries/get-order/GetOrderHandler.ts
@Injectable()
export class GetOrderHandler {
  constructor(private readonly prisma: PrismaClient) {}

  async execute(query: GetOrderQuery): Promise<OrderDetailDto | null> {
    // Query는 ORM 직접 사용 가능 (Read Model)
    const order = await this.prisma.order.findUnique({
      where: { id: query.orderId },
      include: {
        items: {
          include: { product: true },
        },
        customer: true,
      },
    });

    if (!order) return null;

    return OrderDetailDto.from(order);
  }
}

// src/features/orders/queries/get-order/GetOrderEndpoint.ts
@Controller('orders')
export class GetOrderEndpoint {
  constructor(private readonly handler: GetOrderHandler) {}

  @Get(':id')
  @ApiOperation({ summary: '주문 상세 조회' })
  async get(@Param('id') id: string): Promise<OrderDetailDto> {
    const query = new GetOrderQuery(id);
    const order = await this.handler.execute(query);

    if (!order) {
      throw new NotFoundException('주문을 찾을 수 없습니다');
    }

    return order;
  }
}
```

---

## Clean Architecture vs VSA 선택

| 상황 | 권장 아키텍처 |
|------|--------------|
| 복잡한 도메인 로직 | Clean Architecture |
| 빠른 기능 개발 필요 | Vertical Slice |
| 여러 팀 협업 | Clean Architecture (명확한 계층) |
| 스타트업/MVP | Vertical Slice (빠른 이터레이션) |
| CRUD 중심 | Vertical Slice |
| 비즈니스 규칙 복잡 | Clean Architecture + DDD |

---

## 하이브리드 접근

복잡한 도메인과 빠른 개발이 모두 필요한 경우:

```
src/
├── domain/                      # 핵심 도메인 (Clean Architecture)
│   ├── entities/
│   │   ├── Order.ts
│   │   └── Product.ts
│   ├── value-objects/
│   │   ├── Money.ts
│   │   └── OrderStatus.ts
│   └── events/
│       └── OrderEvents.ts
│
├── features/                    # 기능별 슬라이스 (VSA)
│   ├── orders/
│   │   ├── commands/
│   │   │   └── create-order/
│   │   └── queries/
│   │       └── get-order/
│   └── products/
│       ├── commands/
│       └── queries/
│
└── infrastructure/              # 공유 인프라
    ├── database/
    ├── messaging/
    └── external/
```

### 하이브리드 예시

```typescript
// domain/entities/Order.ts - Clean Architecture 스타일
export class Order {
  private items: OrderItem[] = [];
  private status: OrderStatus = OrderStatus.DRAFT;

  addItem(productId: ProductId, quantity: number, price: Money): void {
    if (this.status !== OrderStatus.DRAFT) {
      throw new InvalidOperationError('확정된 주문은 수정할 수 없습니다');
    }
    this.items.push(new OrderItem(productId, quantity, price));
  }

  confirm(): void {
    if (this.items.length === 0) {
      throw new InvalidOperationError('주문 항목이 없습니다');
    }
    this.status = OrderStatus.CONFIRMED;
  }
}

// features/orders/commands/create-order/CreateOrderHandler.ts - VSA 스타일
@Injectable()
export class CreateOrderHandler {
  constructor(
    private readonly orderRepository: OrderRepository,
  ) {}

  async execute(command: CreateOrderCommand): Promise<Order> {
    // 도메인 엔티티 사용
    const order = new Order(command.customerId);

    for (const item of command.items) {
      order.addItem(item.productId, item.quantity, item.price);
    }

    order.confirm();

    await this.orderRepository.save(order);
    return order;
  }
}
```

---

## 슬라이스 간 통신

### 이벤트 기반 (권장)

```typescript
// create-order 슬라이스에서 이벤트 발행
@Injectable()
export class CreateOrderHandler {
  constructor(private readonly eventBus: EventBus) {}

  async execute(command: CreateOrderCommand): Promise<Order> {
    const order = /* ... */;
    await this.orderRepository.save(order);

    // 이벤트 발행
    this.eventBus.publish(new OrderCreatedEvent(order.id, order.items));

    return order;
  }
}

// inventory 슬라이스에서 이벤트 구독
@Injectable()
export class ReserveInventoryHandler {
  constructor(private readonly inventoryRepository: InventoryRepository) {}

  @OnEvent(OrderCreatedEvent)
  async handle(event: OrderCreatedEvent): Promise<void> {
    for (const item of event.items) {
      await this.inventoryRepository.reserve(item.productId, item.quantity);
    }
  }
}
```

### 공유 서비스 (필요 시)

```typescript
// shared/services/ProductService.ts
@Injectable()
export class ProductService {
  async validateProducts(
    items: Array<{ productId: string; quantity: number }>,
  ): Promise<ProductInfo[]> {
    // 검증 로직
  }
}

// 여러 슬라이스에서 주입받아 사용
@Injectable()
export class CreateOrderHandler {
  constructor(private readonly productService: ProductService) {}
}
```

---

## 테스트 전략

### 슬라이스 단위 테스트

```typescript
// create-order/CreateOrderHandler.spec.ts
describe('CreateOrderHandler', () => {
  let handler: CreateOrderHandler;
  let orderRepository: MockOrderRepository;
  let productService: MockProductService;

  beforeEach(() => {
    orderRepository = new MockOrderRepository();
    productService = new MockProductService();
    handler = new CreateOrderHandler(orderRepository, productService);
  });

  it('주문을 생성한다', async () => {
    // Given
    productService.validateProducts.mockResolvedValue([
      { id: 'p1', price: 10000 },
    ]);

    // When
    const command = new CreateOrderCommand('customer1', [
      { productId: 'p1', quantity: 2 },
    ]);
    const result = await handler.execute(command);

    // Then
    expect(result.items).toHaveLength(1);
    expect(orderRepository.save).toHaveBeenCalled();
  });
});
```

---

## 실전 예시

상세 구현 예시: `examples/vertical-slice-example.md` 참조
