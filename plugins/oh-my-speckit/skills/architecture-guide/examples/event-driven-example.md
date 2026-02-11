# 실전 예시: Event-Driven Architecture

## 시나리오: 주문 처리 시스템

주문 → 결제 → 재고 → 배송 흐름을 이벤트 기반으로 구현.

## 아키텍처 개요

```
┌──────────────────────────────────────────────────────────────────────┐
│                           Event Bus (Kafka)                          │
│                                                                      │
│    order-events    payment-events    inventory-events    shipping-   │
│        │                │                  │             events      │
└────────┼────────────────┼──────────────────┼───────────────┼─────────┘
         │                │                  │               │
    ┌────┴────┐      ┌────┴────┐       ┌────┴────┐     ┌────┴────┐
    │  Order  │      │ Payment │       │Inventory│     │Shipping │
    │ Service │      │ Service │       │ Service │     │ Service │
    └─────────┘      └─────────┘       └─────────┘     └─────────┘
```

## 디렉토리 구조

```
src/
├── modules/
│   ├── order/
│   │   ├── domain/
│   │   │   ├── Order.ts
│   │   │   ├── OrderStatus.ts
│   │   │   └── events/
│   │   │       ├── OrderCreatedEvent.ts
│   │   │       ├── OrderConfirmedEvent.ts
│   │   │       └── OrderCancelledEvent.ts
│   │   ├── application/
│   │   │   ├── commands/
│   │   │   │   ├── CreateOrderCommand.ts
│   │   │   │   └── CreateOrderHandler.ts
│   │   │   └── event-handlers/
│   │   │       ├── PaymentCompletedHandler.ts
│   │   │       └── PaymentFailedHandler.ts
│   │   └── infrastructure/
│   │       ├── outbox/
│   │       │   └── OrderOutboxProcessor.ts
│   │       └── kafka/
│   │           └── OrderEventPublisher.ts
│   │
│   ├── payment/
│   │   ├── domain/
│   │   │   ├── Payment.ts
│   │   │   └── events/
│   │   │       ├── PaymentCompletedEvent.ts
│   │   │       └── PaymentFailedEvent.ts
│   │   ├── application/
│   │   │   ├── commands/
│   │   │   └── event-handlers/
│   │   │       └── OrderCreatedHandler.ts
│   │   └── infrastructure/
│   │
│   ├── inventory/
│   │   └── ...
│   │
│   └── shipping/
│       └── ...
│
└── shared/
    ├── events/
    │   ├── DomainEvent.ts
    │   └── EventBus.ts
    └── outbox/
        └── OutboxService.ts
```

## 1. 도메인 이벤트 정의

```typescript
// src/shared/events/DomainEvent.ts
export interface DomainEvent {
  readonly eventId: string;
  readonly eventType: string;
  readonly aggregateId: string;
  readonly aggregateType: string;
  readonly occurredAt: Date;
  readonly version: number;
  readonly payload: Record<string, unknown>;
}

export abstract class BaseDomainEvent implements DomainEvent {
  readonly eventId = crypto.randomUUID();
  readonly occurredAt = new Date();
  abstract readonly eventType: string;
  abstract readonly aggregateId: string;
  abstract readonly aggregateType: string;
  abstract readonly version: number;
  abstract readonly payload: Record<string, unknown>;
}

// src/modules/order/domain/events/OrderCreatedEvent.ts
export class OrderCreatedEvent extends BaseDomainEvent {
  readonly eventType = 'order.created';
  readonly aggregateType = 'Order';
  readonly version = 1;

  constructor(
    public readonly aggregateId: string,
    public readonly payload: {
      customerId: string;
      items: Array<{
        productId: string;
        quantity: number;
        price: number;
      }>;
      totalAmount: number;
    },
  ) {
    super();
  }
}

// src/modules/payment/domain/events/PaymentCompletedEvent.ts
export class PaymentCompletedEvent extends BaseDomainEvent {
  readonly eventType = 'payment.completed';
  readonly aggregateType = 'Payment';
  readonly version = 1;

  constructor(
    public readonly aggregateId: string,
    public readonly payload: {
      orderId: string;
      amount: number;
      transactionId: string;
    },
  ) {
    super();
  }
}
```

## 2. Outbox Pattern 구현

```typescript
// src/shared/outbox/OutboxService.ts
import { Injectable } from '@nestjs/common';
import { PrismaService } from '../database/PrismaService';
import { DomainEvent } from '../events/DomainEvent';

@Injectable()
export class OutboxService {
  constructor(private readonly prisma: PrismaService) {}

  // 트랜잭션 내에서 이벤트 저장
  async saveEvent(
    tx: PrismaTransactionClient,
    event: DomainEvent,
  ): Promise<void> {
    await tx.outbox.create({
      data: {
        eventId: event.eventId,
        eventType: event.eventType,
        aggregateId: event.aggregateId,
        aggregateType: event.aggregateType,
        payload: JSON.stringify(event.payload),
        occurredAt: event.occurredAt,
        version: event.version,
      },
    });
  }

  // 여러 이벤트 저장
  async saveEvents(
    tx: PrismaTransactionClient,
    events: DomainEvent[],
  ): Promise<void> {
    await tx.outbox.createMany({
      data: events.map(event => ({
        eventId: event.eventId,
        eventType: event.eventType,
        aggregateId: event.aggregateId,
        aggregateType: event.aggregateType,
        payload: JSON.stringify(event.payload),
        occurredAt: event.occurredAt,
        version: event.version,
      })),
    });
  }
}

// src/modules/order/infrastructure/outbox/OrderOutboxProcessor.ts
import { Injectable, Logger } from '@nestjs/common';
import { Cron } from '@nestjs/schedule';
import { PrismaService } from '../../../shared/database/PrismaService';
import { KafkaProducerService } from '../../../shared/kafka/KafkaProducerService';

@Injectable()
export class OrderOutboxProcessor {
  private readonly logger = new Logger(OrderOutboxProcessor.name);
  private isProcessing = false;

  constructor(
    private readonly prisma: PrismaService,
    private readonly kafka: KafkaProducerService,
  ) {}

  @Cron('*/5 * * * * *')  // 5초마다
  async processOutbox(): Promise<void> {
    if (this.isProcessing) return;

    this.isProcessing = true;

    try {
      const events = await this.prisma.outbox.findMany({
        where: {
          processedAt: null,
          aggregateType: 'Order',
        },
        orderBy: { occurredAt: 'asc' },
        take: 100,
      });

      for (const event of events) {
        try {
          // Kafka로 발행
          await this.kafka.send({
            topic: 'order-events',
            messages: [{
              key: event.aggregateId,
              value: JSON.stringify({
                eventId: event.eventId,
                eventType: event.eventType,
                aggregateId: event.aggregateId,
                payload: JSON.parse(event.payload),
                occurredAt: event.occurredAt,
                version: event.version,
              }),
              headers: {
                'event-type': event.eventType,
                'aggregate-id': event.aggregateId,
              },
            }],
          });

          // 처리 완료 표시
          await this.prisma.outbox.update({
            where: { id: event.id },
            data: { processedAt: new Date() },
          });
        } catch (error) {
          this.logger.error(`Failed to process event ${event.eventId}`, error);
          // 실패한 이벤트는 다음 처리 주기에 재시도
        }
      }
    } finally {
      this.isProcessing = false;
    }
  }
}
```

## 3. 주문 생성 (이벤트 발행)

```typescript
// src/modules/order/application/commands/CreateOrderHandler.ts
import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../../shared/database/PrismaService';
import { OutboxService } from '../../../shared/outbox/OutboxService';
import { Order } from '../../domain/Order';
import { OrderCreatedEvent } from '../../domain/events/OrderCreatedEvent';
import { CreateOrderCommand } from './CreateOrderCommand';

@Injectable()
export class CreateOrderHandler {
  constructor(
    private readonly prisma: PrismaService,
    private readonly outboxService: OutboxService,
  ) {}

  async execute(command: CreateOrderCommand): Promise<Order> {
    return this.prisma.$transaction(async (tx) => {
      // 1. 주문 생성
      const order = Order.create({
        customerId: command.customerId,
        items: command.items,
      });

      // 2. DB에 저장
      await tx.order.create({
        data: {
          id: order.id,
          customerId: order.customerId,
          status: order.status,
          totalAmount: order.totalAmount,
          items: {
            create: order.items.map(item => ({
              productId: item.productId,
              quantity: item.quantity,
              price: item.price,
            })),
          },
        },
      });

      // 3. Outbox에 이벤트 저장 (같은 트랜잭션)
      const event = new OrderCreatedEvent(order.id, {
        customerId: order.customerId,
        items: order.items,
        totalAmount: order.totalAmount,
      });

      await this.outboxService.saveEvent(tx, event);

      return order;
    });
  }
}
```

## 4. 결제 서비스 (이벤트 구독)

```typescript
// src/modules/payment/application/event-handlers/OrderCreatedHandler.ts
import { Injectable, Logger } from '@nestjs/common';
import { OnEvent } from '@nestjs/event-emitter';
import { KafkaConsumer } from '../../../shared/kafka/KafkaConsumer';
import { ProcessPaymentUseCase } from '../commands/ProcessPaymentUseCase';

@Injectable()
export class OrderCreatedHandler {
  private readonly logger = new Logger(OrderCreatedHandler.name);

  constructor(
    private readonly processPayment: ProcessPaymentUseCase,
  ) {}

  @KafkaConsumer({
    topic: 'order-events',
    groupId: 'payment-service',
  })
  async handle(event: {
    eventType: string;
    aggregateId: string;
    payload: {
      customerId: string;
      totalAmount: number;
    };
  }): Promise<void> {
    if (event.eventType !== 'order.created') return;

    this.logger.log(`Processing order ${event.aggregateId}`);

    try {
      await this.processPayment.execute({
        orderId: event.aggregateId,
        customerId: event.payload.customerId,
        amount: event.payload.totalAmount,
      });
    } catch (error) {
      this.logger.error(`Payment failed for order ${event.aggregateId}`, error);
      // 실패 이벤트 발행 (별도 로직)
    }
  }
}

// src/modules/payment/application/commands/ProcessPaymentUseCase.ts
import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../../shared/database/PrismaService';
import { OutboxService } from '../../../shared/outbox/OutboxService';
import { PaymentCompletedEvent } from '../../domain/events/PaymentCompletedEvent';
import { PaymentFailedEvent } from '../../domain/events/PaymentFailedEvent';
import { PaymentGateway } from '../../infrastructure/PaymentGateway';

@Injectable()
export class ProcessPaymentUseCase {
  constructor(
    private readonly prisma: PrismaService,
    private readonly outboxService: OutboxService,
    private readonly paymentGateway: PaymentGateway,
  ) {}

  async execute(input: {
    orderId: string;
    customerId: string;
    amount: number;
  }): Promise<void> {
    return this.prisma.$transaction(async (tx) => {
      try {
        // 1. 외부 결제 게이트웨이 호출
        const result = await this.paymentGateway.charge({
          customerId: input.customerId,
          amount: input.amount,
        });

        // 2. 결제 정보 저장
        const payment = await tx.payment.create({
          data: {
            orderId: input.orderId,
            amount: input.amount,
            status: 'COMPLETED',
            transactionId: result.transactionId,
          },
        });

        // 3. 성공 이벤트 저장
        await this.outboxService.saveEvent(
          tx,
          new PaymentCompletedEvent(payment.id, {
            orderId: input.orderId,
            amount: input.amount,
            transactionId: result.transactionId,
          }),
        );
      } catch (error) {
        // 실패 시 실패 이벤트 저장
        const payment = await tx.payment.create({
          data: {
            orderId: input.orderId,
            amount: input.amount,
            status: 'FAILED',
            failureReason: error.message,
          },
        });

        await this.outboxService.saveEvent(
          tx,
          new PaymentFailedEvent(payment.id, {
            orderId: input.orderId,
            reason: error.message,
          }),
        );
      }
    });
  }
}
```

## 5. 주문 서비스 - 결제 결과 처리

```typescript
// src/modules/order/application/event-handlers/PaymentCompletedHandler.ts
import { Injectable, Logger } from '@nestjs/common';
import { KafkaConsumer } from '../../../shared/kafka/KafkaConsumer';
import { PrismaService } from '../../../shared/database/PrismaService';
import { OutboxService } from '../../../shared/outbox/OutboxService';
import { OrderConfirmedEvent } from '../../domain/events/OrderConfirmedEvent';

@Injectable()
export class PaymentCompletedHandler {
  private readonly logger = new Logger(PaymentCompletedHandler.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly outboxService: OutboxService,
  ) {}

  @KafkaConsumer({
    topic: 'payment-events',
    groupId: 'order-service',
  })
  async handle(event: {
    eventType: string;
    payload: {
      orderId: string;
      transactionId: string;
    };
  }): Promise<void> {
    if (event.eventType !== 'payment.completed') return;

    const { orderId, transactionId } = event.payload;

    await this.prisma.$transaction(async (tx) => {
      // 주문 상태 업데이트
      await tx.order.update({
        where: { id: orderId },
        data: {
          status: 'CONFIRMED',
          paymentTransactionId: transactionId,
        },
      });

      // 확정 이벤트 발행 (재고 차감 트리거)
      await this.outboxService.saveEvent(
        tx,
        new OrderConfirmedEvent(orderId, { transactionId }),
      );
    });

    this.logger.log(`Order ${orderId} confirmed`);
  }
}

// src/modules/order/application/event-handlers/PaymentFailedHandler.ts
@Injectable()
export class PaymentFailedHandler {
  @KafkaConsumer({
    topic: 'payment-events',
    groupId: 'order-service',
  })
  async handle(event: {
    eventType: string;
    payload: {
      orderId: string;
      reason: string;
    };
  }): Promise<void> {
    if (event.eventType !== 'payment.failed') return;

    await this.prisma.$transaction(async (tx) => {
      await tx.order.update({
        where: { id: event.payload.orderId },
        data: {
          status: 'CANCELLED',
          cancellationReason: event.payload.reason,
        },
      });

      await this.outboxService.saveEvent(
        tx,
        new OrderCancelledEvent(event.payload.orderId, {
          reason: event.payload.reason,
        }),
      );
    });
  }
}
```

## 6. Saga 패턴 (보상 트랜잭션)

```typescript
// src/modules/order/application/sagas/OrderSaga.ts
import { Injectable, Logger } from '@nestjs/common';

interface SagaStep<T> {
  name: string;
  execute: (context: T) => Promise<void>;
  compensate: (context: T) => Promise<void>;
}

@Injectable()
export class OrderSaga {
  private readonly logger = new Logger(OrderSaga.name);

  private steps: SagaStep<OrderSagaContext>[] = [
    {
      name: 'reserveInventory',
      execute: async (ctx) => {
        ctx.inventoryReservationId = await this.inventoryService.reserve(
          ctx.orderId,
          ctx.items,
        );
      },
      compensate: async (ctx) => {
        if (ctx.inventoryReservationId) {
          await this.inventoryService.releaseReservation(
            ctx.inventoryReservationId,
          );
        }
      },
    },
    {
      name: 'processPayment',
      execute: async (ctx) => {
        ctx.paymentId = await this.paymentService.process(
          ctx.orderId,
          ctx.customerId,
          ctx.totalAmount,
        );
      },
      compensate: async (ctx) => {
        if (ctx.paymentId) {
          await this.paymentService.refund(ctx.paymentId);
        }
      },
    },
    {
      name: 'confirmInventory',
      execute: async (ctx) => {
        await this.inventoryService.confirmReservation(
          ctx.inventoryReservationId!,
        );
      },
      compensate: async (ctx) => {
        // 이미 확정된 재고는 반환 처리
        await this.inventoryService.returnItems(ctx.orderId, ctx.items);
      },
    },
    {
      name: 'scheduleShipping',
      execute: async (ctx) => {
        ctx.shippingId = await this.shippingService.schedule(
          ctx.orderId,
          ctx.shippingAddress,
        );
      },
      compensate: async (ctx) => {
        if (ctx.shippingId) {
          await this.shippingService.cancel(ctx.shippingId);
        }
      },
    },
  ];

  constructor(
    private readonly inventoryService: InventoryService,
    private readonly paymentService: PaymentService,
    private readonly shippingService: ShippingService,
  ) {}

  async execute(context: OrderSagaContext): Promise<void> {
    const completedSteps: SagaStep<OrderSagaContext>[] = [];

    try {
      for (const step of this.steps) {
        this.logger.log(`Executing step: ${step.name}`);
        await step.execute(context);
        completedSteps.push(step);
      }

      this.logger.log(`Saga completed for order ${context.orderId}`);
    } catch (error) {
      this.logger.error(`Saga failed at step, starting compensation`, error);

      // 역순으로 보상 트랜잭션 실행
      for (const step of completedSteps.reverse()) {
        try {
          this.logger.log(`Compensating step: ${step.name}`);
          await step.compensate(context);
        } catch (compensateError) {
          this.logger.error(
            `Compensation failed for step ${step.name}`,
            compensateError,
          );
          // 보상 실패는 별도 처리 필요 (수동 개입, 알림 등)
        }
      }

      throw error;
    }
  }
}

interface OrderSagaContext {
  orderId: string;
  customerId: string;
  items: Array<{ productId: string; quantity: number }>;
  totalAmount: number;
  shippingAddress: string;
  inventoryReservationId?: string;
  paymentId?: string;
  shippingId?: string;
}
```

## 이벤트 흐름 요약

```
[1] 주문 생성
    Order Service
         │
         ▼
    OrderCreatedEvent ─────────────►  Kafka
                                        │
    ┌───────────────────────────────────┘
    │
    ▼
[2] 결제 처리
    Payment Service
         │
    ┌────┴────┐
    ▼         ▼
 성공      실패
    │         │
    ▼         ▼
PaymentCompletedEvent  PaymentFailedEvent
    │                        │
    └────────────────────────┘
              │
              ▼
[3] 주문 상태 업데이트
    Order Service
         │
    ┌────┴────┐
    ▼         ▼
 확정      취소
    │         │
    ▼         ▼
OrderConfirmedEvent  OrderCancelledEvent
    │
    ▼
[4] 재고 차감
    Inventory Service
         │
         ▼
    InventoryDeductedEvent
         │
         ▼
[5] 배송 예약
    Shipping Service
```

## 핵심 포인트

| 패턴 | 목적 |
|------|------|
| **Outbox Pattern** | DB 트랜잭션과 이벤트 발행의 원자성 보장 |
| **Idempotency** | 이벤트 중복 처리 방지 (eventId 기반) |
| **Saga Pattern** | 분산 트랜잭션의 일관성 보장 (보상 트랜잭션) |
| **Dead Letter Queue** | 처리 실패 이벤트 별도 관리 |
| **Event Versioning** | 스키마 변경에 대한 하위 호환성 |
