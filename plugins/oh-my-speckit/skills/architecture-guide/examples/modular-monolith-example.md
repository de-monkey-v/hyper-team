# 실전 예시: Modular Monolith

## 시나리오: 이커머스 플랫폼

이커머스 플랫폼을 모듈러 모놀리스로 구현.

## 모듈 구조

```
src/
├── modules/
│   ├── user/                    # 사용자 관리 모듈
│   │   ├── domain/
│   │   │   ├── User.ts
│   │   │   ├── UserRepository.ts
│   │   │   └── events/
│   │   │       └── UserCreatedEvent.ts
│   │   ├── application/
│   │   │   ├── CreateUserUseCase.ts
│   │   │   └── GetUserUseCase.ts
│   │   ├── infrastructure/
│   │   │   └── PrismaUserRepository.ts
│   │   ├── interfaces/
│   │   │   └── http/
│   │   │       └── UserController.ts
│   │   ├── user.module.ts
│   │   └── index.ts             # Public API
│   │
│   ├── catalog/                 # 상품 카탈로그 모듈
│   │   ├── domain/
│   │   │   ├── Product.ts
│   │   │   ├── Category.ts
│   │   │   └── ProductRepository.ts
│   │   ├── application/
│   │   ├── infrastructure/
│   │   ├── interfaces/
│   │   ├── catalog.module.ts
│   │   └── index.ts
│   │
│   ├── order/                   # 주문 모듈
│   │   ├── domain/
│   │   │   ├── Order.ts
│   │   │   ├── OrderItem.ts
│   │   │   ├── OrderRepository.ts
│   │   │   └── events/
│   │   │       ├── OrderCreatedEvent.ts
│   │   │       └── OrderCompletedEvent.ts
│   │   ├── application/
│   │   │   ├── CreateOrderUseCase.ts
│   │   │   └── CompleteOrderUseCase.ts
│   │   ├── infrastructure/
│   │   ├── interfaces/
│   │   ├── listeners/           # 다른 모듈 이벤트 리스너
│   │   │   └── PaymentEventListener.ts
│   │   ├── order.module.ts
│   │   └── index.ts
│   │
│   └── payment/                 # 결제 모듈
│       ├── domain/
│       │   ├── Payment.ts
│       │   └── events/
│       │       ├── PaymentCompletedEvent.ts
│       │       └── PaymentFailedEvent.ts
│       ├── application/
│       ├── infrastructure/
│       ├── interfaces/
│       ├── payment.module.ts
│       └── index.ts
│
├── shared/                      # 공유 인프라
│   ├── database/
│   │   └── prisma.service.ts
│   ├── events/
│   │   └── event-bus.service.ts
│   └── common/
│       ├── BaseEntity.ts
│       └── Money.ts             # 공유 Value Object
│
└── app.module.ts
```

## 1. 모듈 Public API (index.ts)

```typescript
// src/modules/user/index.ts
// 외부에서 접근 가능한 API만 export

// Services (다른 모듈에서 사용 가능)
export { UserService } from './application/UserService';

// DTOs
export { UserDto } from './application/dto/UserDto';

// Events (다른 모듈에서 구독 가능)
export { UserCreatedEvent } from './domain/events/UserCreatedEvent';

// ❌ 내부 구현은 export하지 않음
// export { User } from './domain/User';
// export { UserRepository } from './domain/UserRepository';
// export { PrismaUserRepository } from './infrastructure/PrismaUserRepository';
```

## 2. 모듈 정의

```typescript
// src/modules/user/user.module.ts
import { Module } from '@nestjs/common';
import { UserController } from './interfaces/http/UserController';
import { CreateUserUseCase } from './application/CreateUserUseCase';
import { GetUserUseCase } from './application/GetUserUseCase';
import { UserService } from './application/UserService';
import { PrismaUserRepository } from './infrastructure/PrismaUserRepository';
import { UserRepository } from './domain/UserRepository';
import { SharedModule } from '../shared/shared.module';

@Module({
  imports: [SharedModule],
  controllers: [UserController],
  providers: [
    CreateUserUseCase,
    GetUserUseCase,
    UserService,
    {
      provide: UserRepository,
      useClass: PrismaUserRepository,
    },
  ],
  exports: [UserService],  // 다른 모듈에서 사용 가능
})
export class UserModule {}
```

## 3. 모듈 간 통신 (이벤트)

```typescript
// src/shared/events/event-bus.service.ts
import { Injectable } from '@nestjs/common';
import { EventEmitter2 } from '@nestjs/event-emitter';

export interface DomainEvent {
  readonly eventType: string;
  readonly occurredAt: Date;
  readonly aggregateId: string;
}

@Injectable()
export class EventBusService {
  constructor(private readonly eventEmitter: EventEmitter2) {}

  publish(event: DomainEvent): void {
    this.eventEmitter.emit(event.eventType, event);
  }

  publishAll(events: DomainEvent[]): void {
    events.forEach(event => this.publish(event));
  }
}

// src/modules/order/domain/events/OrderCreatedEvent.ts
export class OrderCreatedEvent implements DomainEvent {
  readonly eventType = 'order.created';
  readonly occurredAt = new Date();

  constructor(
    public readonly aggregateId: string,
    public readonly customerId: string,
    public readonly totalAmount: number,
    public readonly items: { productId: string; quantity: number }[],
  ) {}
}

// src/modules/order/application/CreateOrderUseCase.ts
@Injectable()
export class CreateOrderUseCase {
  constructor(
    private readonly orderRepository: OrderRepository,
    private readonly eventBus: EventBusService,
    private readonly catalogService: CatalogService,  // 다른 모듈 public API
  ) {}

  async execute(dto: CreateOrderDto): Promise<Order> {
    // 1. 상품 정보 조회 (Catalog 모듈 Public API 사용)
    const products = await this.catalogService.getProductsByIds(
      dto.items.map(i => i.productId),
    );

    // 2. 주문 생성
    const order = Order.create({
      customerId: dto.customerId,
      items: dto.items.map(item => ({
        productId: item.productId,
        quantity: item.quantity,
        price: products.find(p => p.id === item.productId)!.price,
      })),
    });

    // 3. 저장
    await this.orderRepository.save(order);

    // 4. 이벤트 발행
    this.eventBus.publish(new OrderCreatedEvent(
      order.id,
      order.customerId,
      order.totalAmount,
      order.items,
    ));

    return order;
  }
}
```

## 4. 이벤트 리스너 (다른 모듈 이벤트 구독)

```typescript
// src/modules/payment/listeners/OrderEventListener.ts
import { Injectable } from '@nestjs/common';
import { OnEvent } from '@nestjs/event-emitter';
import { OrderCreatedEvent } from '../../order';
import { ProcessPaymentUseCase } from '../application/ProcessPaymentUseCase';

@Injectable()
export class OrderEventListener {
  constructor(
    private readonly processPayment: ProcessPaymentUseCase,
  ) {}

  @OnEvent('order.created')
  async handleOrderCreated(event: OrderCreatedEvent): Promise<void> {
    await this.processPayment.execute({
      orderId: event.aggregateId,
      customerId: event.customerId,
      amount: event.totalAmount,
    });
  }
}

// src/modules/order/listeners/PaymentEventListener.ts
import { Injectable } from '@nestjs/common';
import { OnEvent } from '@nestjs/event-emitter';
import { PaymentCompletedEvent, PaymentFailedEvent } from '../../payment';
import { CompleteOrderUseCase } from '../application/CompleteOrderUseCase';
import { CancelOrderUseCase } from '../application/CancelOrderUseCase';

@Injectable()
export class PaymentEventListener {
  constructor(
    private readonly completeOrder: CompleteOrderUseCase,
    private readonly cancelOrder: CancelOrderUseCase,
  ) {}

  @OnEvent('payment.completed')
  async handlePaymentCompleted(event: PaymentCompletedEvent): Promise<void> {
    await this.completeOrder.execute(event.orderId);
  }

  @OnEvent('payment.failed')
  async handlePaymentFailed(event: PaymentFailedEvent): Promise<void> {
    await this.cancelOrder.execute(event.orderId, event.reason);
  }
}
```

## 5. 모듈 경계 강제 (ESLint)

```javascript
// eslint.config.js
export default [
  {
    plugins: {
      'import': importPlugin,
    },
    rules: {
      'import/no-restricted-paths': ['error', {
        zones: [
          // User 모듈은 Order 모듈 내부 직접 접근 불가
          {
            target: './src/modules/user/**/*',
            from: './src/modules/order/**/*',
            except: ['./index.ts'],
          },
          // Order 모듈은 User 모듈 내부 직접 접근 불가
          {
            target: './src/modules/order/**/*',
            from: './src/modules/user/**/*',
            except: ['./index.ts'],
          },
          // Payment 모듈은 다른 모듈 내부 직접 접근 불가
          {
            target: './src/modules/payment/**/*',
            from: './src/modules/!(payment)/**/*',
            except: ['./index.ts'],
          },
          // Catalog 모듈은 다른 모듈 내부 직접 접근 불가
          {
            target: './src/modules/catalog/**/*',
            from: './src/modules/!(catalog)/**/*',
            except: ['./index.ts'],
          },
        ],
      }],
    },
  },
];
```

## 6. 루트 모듈 구성

```typescript
// src/app.module.ts
import { Module } from '@nestjs/common';
import { EventEmitterModule } from '@nestjs/event-emitter';
import { UserModule } from './modules/user/user.module';
import { CatalogModule } from './modules/catalog/catalog.module';
import { OrderModule } from './modules/order/order.module';
import { PaymentModule } from './modules/payment/payment.module';
import { SharedModule } from './shared/shared.module';

@Module({
  imports: [
    // 이벤트 버스 설정
    EventEmitterModule.forRoot({
      wildcard: false,
      delimiter: '.',
      newListener: false,
      removeListener: false,
      maxListeners: 10,
      verboseMemoryLeak: false,
      ignoreErrors: false,
    }),

    // 공유 모듈
    SharedModule,

    // 비즈니스 모듈
    UserModule,
    CatalogModule,
    OrderModule,
    PaymentModule,
  ],
})
export class AppModule {}
```

## 7. 데이터베이스 스키마 분리

```prisma
// prisma/schema.prisma
// 모듈별 스키마 분리 (하나의 DB, 논리적 분리)

// === User Module ===
model User {
  id        String   @id @default(uuid())
  email     String   @unique
  name      String
  createdAt DateTime @default(now())

  // 다른 모듈 참조는 ID만
  // ❌ orders Order[]  // 직접 관계 X
}

// === Catalog Module ===
model Product {
  id          String   @id @default(uuid())
  name        String
  description String
  price       Decimal
  stock       Int
  categoryId  String
  category    Category @relation(fields: [categoryId], references: [id])
  createdAt   DateTime @default(now())

  @@map("catalog_products")
}

model Category {
  id       String    @id @default(uuid())
  name     String
  products Product[]

  @@map("catalog_categories")
}

// === Order Module ===
model Order {
  id         String      @id @default(uuid())
  customerId String      // User ID 참조 (FK 없음)
  status     OrderStatus
  totalAmount Decimal
  items      OrderItem[]
  createdAt  DateTime    @default(now())

  @@map("order_orders")
}

model OrderItem {
  id        String  @id @default(uuid())
  orderId   String
  order     Order   @relation(fields: [orderId], references: [id])
  productId String  // Product ID 참조 (FK 없음)
  quantity  Int
  price     Decimal

  @@map("order_items")
}

enum OrderStatus {
  PENDING
  PAID
  COMPLETED
  CANCELLED
}

// === Payment Module ===
model Payment {
  id        String        @id @default(uuid())
  orderId   String        // Order ID 참조 (FK 없음)
  amount    Decimal
  status    PaymentStatus
  method    String
  createdAt DateTime      @default(now())

  @@map("payment_payments")
}

enum PaymentStatus {
  PENDING
  COMPLETED
  FAILED
  REFUNDED
}
```

## 이벤트 흐름

```
[1] 주문 생성 요청
         │
         ▼
┌─────────────────┐
│   Order Module  │
│ CreateOrderUse  │──────► OrderCreatedEvent
└─────────────────┘                │
                                   │
         ┌─────────────────────────┘
         │
         ▼
┌─────────────────┐
│  Payment Module │
│ ProcessPayment  │──────► PaymentCompletedEvent
└─────────────────┘         또는 PaymentFailedEvent
                                   │
         ┌─────────────────────────┘
         │
         ▼
┌─────────────────┐
│   Order Module  │
│ CompleteOrder   │──────► 주문 상태 변경
│ 또는 CancelOrder│
└─────────────────┘
```

## 핵심 포인트

| 원칙 | 설명 |
|------|------|
| **모듈 독립성** | 각 모듈은 자체 도메인, 애플리케이션, 인프라 계층 보유 |
| **Public API** | index.ts로 외부 노출 API 명시적 관리 |
| **이벤트 통신** | 모듈 간 직접 의존 대신 이벤트로 느슨한 결합 |
| **경계 강제** | ESLint로 모듈 경계 위반 검출 |
| **스키마 분리** | 같은 DB지만 테이블 prefix로 논리적 분리 |
