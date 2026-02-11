# Event-Driven Architecture (EDA)

이벤트 기반으로 서비스 간 통신하는 비동기 아키텍처.

## EDA 패턴 종류

| 패턴 | 설명 | 사용 사례 |
|------|------|----------|
| **Event Notification** | 이벤트 발생 알림만 전달 | 상태 변경 알림 |
| **Event-Carried State Transfer** | 이벤트에 전체 상태 포함 | 서비스 간 데이터 복제 |
| **Event Sourcing** | 상태를 이벤트 시퀀스로 저장 | 감사 로그, 시간 여행 |
| **CQRS** | 읽기/쓰기 모델 분리 | 고성능 읽기/쓰기 최적화 |

---

## 메시지 브로커 비교

| 브로커 | 처리량 | 지연시간 | 순서 보장 | 사용 사례 |
|--------|--------|----------|-----------|----------|
| **Kafka** | 매우 높음 | 낮음 | 파티션 내 보장 | 대용량 이벤트 스트림 |
| **RabbitMQ** | 높음 | 매우 낮음 | 큐 내 보장 | 복잡한 라우팅 |
| **Redis Streams** | 높음 | 매우 낮음 | 스트림 내 보장 | 간단한 이벤트 처리 |
| **AWS SQS/SNS** | 높음 | 중간 | FIFO 선택 가능 | AWS 통합 |

---

## Kafka 기반 이벤트 발행/구독

### Producer (이벤트 발행)

```typescript
@Injectable()
export class OrderEventPublisher {
  constructor(
    @InjectKafka() private readonly kafka: KafkaClient,
  ) {}

  async publishOrderCreated(order: Order): Promise<void> {
    await this.kafka.send({
      topic: 'order-events',
      messages: [{
        key: order.id,  // 같은 주문은 같은 파티션으로
        value: JSON.stringify({
          type: 'ORDER_CREATED',
          payload: {
            orderId: order.id,
            customerId: order.customerId,
            items: order.items,
            totalAmount: order.totalAmount,
          },
          metadata: {
            timestamp: new Date().toISOString(),
            version: 1,
            correlationId: order.correlationId,
          },
        }),
      }],
    });
  }
}
```

### Consumer (이벤트 구독)

```typescript
@Controller()
export class OrderEventConsumer {
  constructor(private readonly inventoryService: InventoryService) {}

  @EventPattern('order-events')
  async handleOrderEvent(event: OrderEvent): Promise<void> {
    try {
      switch (event.type) {
        case 'ORDER_CREATED':
          await this.inventoryService.reserveStock(event.payload.items);
          break;
        case 'ORDER_CANCELLED':
          await this.inventoryService.releaseStock(event.payload.items);
          break;
        default:
          console.log('Unknown event type:', event.type);
      }
    } catch (error) {
      // DLQ로 전송 또는 재시도 로직
      throw error;
    }
  }
}
```

---

## Outbox Pattern (트랜잭션 보장)

DB 트랜잭션과 이벤트 발행의 원자성 보장:

### 1. Outbox 테이블 스키마

```sql
CREATE TABLE outbox (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  aggregate_type VARCHAR(255) NOT NULL,
  aggregate_id VARCHAR(255) NOT NULL,
  event_type VARCHAR(255) NOT NULL,
  payload JSONB NOT NULL,
  created_at TIMESTAMP DEFAULT NOW(),
  processed_at TIMESTAMP NULL,
  INDEX idx_outbox_unprocessed (processed_at) WHERE processed_at IS NULL
);
```

### 2. 비즈니스 로직 + Outbox 저장

```typescript
@Injectable()
export class OrderService {
  constructor(private readonly prisma: PrismaService) {}

  async createOrder(dto: CreateOrderDto): Promise<Order> {
    return this.prisma.$transaction(async (tx) => {
      // 1. 주문 생성
      const order = await tx.order.create({
        data: {
          customerId: dto.customerId,
          items: { create: dto.items },
          status: 'PENDING',
        },
        include: { items: true },
      });

      // 2. Outbox에 이벤트 저장 (같은 트랜잭션)
      await tx.outbox.create({
        data: {
          aggregateType: 'Order',
          aggregateId: order.id,
          eventType: 'ORDER_CREATED',
          payload: {
            orderId: order.id,
            customerId: order.customerId,
            items: order.items,
            totalAmount: this.calculateTotal(order.items),
          },
        },
      });

      return order;
    });
  }
}
```

### 3. Outbox Processor

```typescript
@Injectable()
export class OutboxProcessor {
  constructor(
    private readonly prisma: PrismaService,
    private readonly kafka: KafkaClient,
  ) {}

  @Cron('*/5 * * * * *')  // 5초마다 실행
  async processOutbox(): Promise<void> {
    const events = await this.prisma.outbox.findMany({
      where: { processedAt: null },
      orderBy: { createdAt: 'asc' },
      take: 100,
    });

    for (const event of events) {
      try {
        // Kafka로 이벤트 발행
        await this.kafka.send({
          topic: `${event.aggregateType.toLowerCase()}-events`,
          messages: [{
            key: event.aggregateId,
            value: JSON.stringify({
              type: event.eventType,
              payload: event.payload,
              metadata: {
                eventId: event.id,
                timestamp: event.createdAt.toISOString(),
              },
            }),
          }],
        });

        // 처리 완료 표시
        await this.prisma.outbox.update({
          where: { id: event.id },
          data: { processedAt: new Date() },
        });
      } catch (error) {
        console.error(`Failed to process outbox event ${event.id}:`, error);
        // 실패 시 다음 실행에서 재시도
      }
    }
  }
}
```

---

## Saga Pattern (분산 트랜잭션)

여러 서비스에 걸친 비즈니스 트랜잭션 관리.

### Choreography 방식 (이벤트 체인)

```
Order Service → Payment Service → Inventory Service → Shipping Service
     │                │                  │                  │
     │  OrderCreated  │                  │                  │
     └────────────────►                  │                  │
                      │  PaymentCompleted│                  │
                      └──────────────────►                  │
                                         │ StockReserved    │
                                         └──────────────────►
                                                            │
                                                     ShipmentScheduled
```

```typescript
// Order Service
@OnEvent('payment.completed')
async handlePaymentCompleted(event: PaymentCompletedEvent) {
  await this.orderRepository.updateStatus(event.orderId, 'PAID');
  this.eventEmitter.emit('order.paid', new OrderPaidEvent(event.orderId));
}

@OnEvent('payment.failed')
async handlePaymentFailed(event: PaymentFailedEvent) {
  // 보상 트랜잭션
  await this.orderRepository.updateStatus(event.orderId, 'CANCELLED');
  this.eventEmitter.emit('order.cancelled', new OrderCancelledEvent(
    event.orderId,
    'Payment failed',
  ));
}

// Inventory Service
@OnEvent('order.paid')
async handleOrderPaid(event: OrderPaidEvent) {
  try {
    await this.inventoryService.reserveStock(event.orderId);
    this.eventEmitter.emit('inventory.reserved', new InventoryReservedEvent(event.orderId));
  } catch (error) {
    this.eventEmitter.emit('inventory.failed', new InventoryFailedEvent(
      event.orderId,
      error.message,
    ));
  }
}
```

### Orchestration 방식 (중앙 조정자)

```typescript
@Injectable()
export class OrderSagaOrchestrator {
  constructor(
    private readonly orderService: OrderService,
    private readonly paymentService: PaymentService,
    private readonly inventoryService: InventoryService,
    private readonly shippingService: ShippingService,
  ) {}

  async execute(orderId: string): Promise<SagaResult> {
    const compensations: Array<() => Promise<void>> = [];

    try {
      // Step 1: 결제
      await this.paymentService.processPayment(orderId);
      compensations.push(() => this.paymentService.refund(orderId));

      // Step 2: 재고 예약
      await this.inventoryService.reserveStock(orderId);
      compensations.push(() => this.inventoryService.releaseStock(orderId));

      // Step 3: 배송 예약
      await this.shippingService.scheduleShipment(orderId);
      compensations.push(() => this.shippingService.cancelShipment(orderId));

      // 성공
      await this.orderService.complete(orderId);
      return { success: true, orderId };

    } catch (error) {
      // 보상 트랜잭션 (역순 실행)
      await this.compensate(compensations);
      await this.orderService.fail(orderId, error.message);
      return { success: false, orderId, error: error.message };
    }
  }

  private async compensate(compensations: Array<() => Promise<void>>): Promise<void> {
    for (const compensation of compensations.reverse()) {
      try {
        await compensation();
      } catch (error) {
        // 보상 실패 로깅 (수동 개입 필요)
        console.error('Compensation failed:', error);
      }
    }
  }
}
```

---

## 이벤트 스키마 버전 관리

### 버전별 스키마 정의

```typescript
// V1 스키마
interface OrderCreatedEventV1 {
  version: 1;
  type: 'ORDER_CREATED';
  payload: {
    orderId: string;
    customerId: string;
    amount: number;  // 단일 금액
  };
}

// V2 스키마 (Breaking Change)
interface OrderCreatedEventV2 {
  version: 2;
  type: 'ORDER_CREATED';
  payload: {
    orderId: string;
    customerId: string;
    subtotal: number;     // 세분화
    tax: number;
    shippingFee: number;
    totalAmount: number;
  };
}
```

### 이벤트 업캐스터

```typescript
@Injectable()
export class OrderEventUpcaster {
  upcast(event: OrderCreatedEventV1 | OrderCreatedEventV2): OrderCreatedEventV2 {
    if (event.version === 2) return event;

    // V1 → V2 변환
    return {
      version: 2,
      type: 'ORDER_CREATED',
      payload: {
        orderId: event.payload.orderId,
        customerId: event.payload.customerId,
        subtotal: event.payload.amount,
        tax: 0,
        shippingFee: 0,
        totalAmount: event.payload.amount,
      },
    };
  }
}

// Consumer에서 사용
@EventPattern('order-events')
async handleOrderEvent(rawEvent: unknown): Promise<void> {
  const event = this.upcaster.upcast(rawEvent as OrderCreatedEventV1);
  // 항상 V2 형식으로 처리
}
```

---

## 멱등성 처리

같은 이벤트가 여러 번 처리되어도 결과가 동일하도록:

```typescript
@Injectable()
export class IdempotentEventHandler {
  constructor(
    private readonly redis: Redis,
    private readonly inventoryService: InventoryService,
  ) {}

  async handleOrderCreated(event: OrderCreatedEvent): Promise<void> {
    const eventKey = `processed:${event.metadata.eventId}`;

    // 이미 처리된 이벤트인지 확인
    const alreadyProcessed = await this.redis.get(eventKey);
    if (alreadyProcessed) {
      console.log(`Event ${event.metadata.eventId} already processed, skipping`);
      return;
    }

    // 이벤트 처리
    await this.inventoryService.reserveStock(event.payload.items);

    // 처리 완료 표시 (7일 TTL)
    await this.redis.set(eventKey, 'processed', 'EX', 604800);
  }
}
```

---

## EDA 이점

| 이점 | 설명 |
|------|------|
| **느슨한 결합** | 서비스 간 직접 의존성 제거 |
| **확장성** | 컨슈머 독립적 확장 가능 |
| **탄력성** | 일시적 장애에 강함 (재시도) |
| **실시간 반응성** | 변경 즉시 전파 |
| **감사 추적** | 모든 이벤트 기록 보존 |

---

## 실전 예시

상세 구현 예시: `examples/event-driven-example.md` 참조
