# Serverless Architecture

서버 관리 없이 코드 실행에 집중하는 아키텍처.

## Serverless 구성 요소

| 구성 요소 | AWS | Vercel | GCP |
|----------|-----|--------|-----|
| **Functions** | Lambda | Serverless Functions | Cloud Functions |
| **API Gateway** | API Gateway | Built-in | API Gateway |
| **Database** | DynamoDB | Vercel KV/Postgres | Firestore |
| **Storage** | S3 | Vercel Blob | Cloud Storage |
| **Queue** | SQS | - | Cloud Tasks |

---

## AWS Lambda 구조

```
project/
├── src/
│   ├── functions/
│   │   ├── createOrder/
│   │   │   ├── handler.ts
│   │   │   └── schema.ts
│   │   ├── getOrder/
│   │   │   └── handler.ts
│   │   └── processPayment/
│   │       └── handler.ts
│   ├── shared/
│   │   ├── database.ts
│   │   └── utils.ts
│   └── types/
│       └── index.ts
├── serverless.yml
└── package.json
```

---

## Lambda 핸들러 패턴

```typescript
// src/functions/createOrder/handler.ts
import { APIGatewayProxyHandler } from 'aws-lambda';
import { z } from 'zod';
import { prisma } from '../../shared/database';

const createOrderSchema = z.object({
  customerId: z.string().uuid(),
  items: z.array(z.object({
    productId: z.string().uuid(),
    quantity: z.number().positive(),
  })),
});

export const handler: APIGatewayProxyHandler = async (event) => {
  try {
    // 1. 입력 검증
    const body = JSON.parse(event.body || '{}');
    const data = createOrderSchema.parse(body);

    // 2. 비즈니스 로직
    const order = await prisma.order.create({
      data: {
        customerId: data.customerId,
        items: { create: data.items },
        status: 'PENDING',
      },
      include: { items: true },
    });

    // 3. 응답
    return {
      statusCode: 201,
      headers: {
        'Content-Type': 'application/json',
        'Access-Control-Allow-Origin': '*',
      },
      body: JSON.stringify(order),
    };
  } catch (error) {
    if (error instanceof z.ZodError) {
      return {
        statusCode: 400,
        body: JSON.stringify({ errors: error.errors }),
      };
    }

    console.error(error);
    return {
      statusCode: 500,
      body: JSON.stringify({ message: 'Internal Server Error' }),
    };
  }
};
```

---

## Serverless Framework 설정

```yaml
# serverless.yml
service: order-service
frameworkVersion: '3'

provider:
  name: aws
  runtime: nodejs20.x
  region: ap-northeast-2
  memorySize: 256
  timeout: 30
  environment:
    DATABASE_URL: ${env:DATABASE_URL}
    NODE_ENV: ${opt:stage, 'dev'}

functions:
  createOrder:
    handler: src/functions/createOrder/handler.handler
    events:
      - http:
          path: orders
          method: post
          cors: true

  getOrder:
    handler: src/functions/getOrder/handler.handler
    events:
      - http:
          path: orders/{id}
          method: get
          cors: true
          request:
            parameters:
              paths:
                id: true

  processPayment:
    handler: src/functions/processPayment/handler.handler
    events:
      - sqs:
          arn: !GetAtt PaymentQueue.Arn
          batchSize: 10
          maximumBatchingWindow: 5

  scheduledTask:
    handler: src/functions/scheduledTask/handler.handler
    events:
      - schedule: rate(5 minutes)

resources:
  Resources:
    PaymentQueue:
      Type: AWS::SQS::Queue
      Properties:
        QueueName: ${self:service}-payment-queue-${opt:stage, 'dev'}
        VisibilityTimeout: 180
        RedrivePolicy:
          deadLetterTargetArn: !GetAtt PaymentDLQ.Arn
          maxReceiveCount: 3

    PaymentDLQ:
      Type: AWS::SQS::Queue
      Properties:
        QueueName: ${self:service}-payment-dlq-${opt:stage, 'dev'}

custom:
  esbuild:
    bundle: true
    minify: true
    sourcemap: true
    exclude:
      - '@aws-sdk/*'
```

---

## Vercel Edge Functions

```typescript
// app/api/orders/route.ts (Next.js App Router)
import { NextRequest, NextResponse } from 'next/server';
import { prisma } from '@/lib/prisma';
import { z } from 'zod';

export const runtime = 'edge';  // Edge Runtime 사용

const createOrderSchema = z.object({
  customerId: z.string().uuid(),
  items: z.array(z.object({
    productId: z.string().uuid(),
    quantity: z.number().positive(),
  })).min(1),
});

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const data = createOrderSchema.parse(body);

    const order = await prisma.order.create({
      data: {
        customerId: data.customerId,
        items: { create: data.items },
      },
    });

    return NextResponse.json(order, { status: 201 });
  } catch (error) {
    if (error instanceof z.ZodError) {
      return NextResponse.json(
        { errors: error.errors },
        { status: 400 },
      );
    }

    return NextResponse.json(
      { message: 'Internal Server Error' },
      { status: 500 },
    );
  }
}

export async function GET(request: NextRequest) {
  const { searchParams } = new URL(request.url);
  const customerId = searchParams.get('customerId');

  const orders = await prisma.order.findMany({
    where: customerId ? { customerId } : undefined,
    orderBy: { createdAt: 'desc' },
    take: 50,
  });

  return NextResponse.json(orders);
}
```

---

## Cold Start 최적화

### 1. 연결 재사용

```typescript
// src/shared/database.ts
import { PrismaClient } from '@prisma/client';

declare global {
  var prisma: PrismaClient | undefined;
}

// Lambda 인스턴스 간 연결 재사용
export const prisma = global.prisma || new PrismaClient({
  datasources: {
    db: {
      url: process.env.DATABASE_URL,
    },
  },
});

if (process.env.NODE_ENV !== 'production') {
  global.prisma = prisma;
}
```

### 2. 번들 크기 최소화

```yaml
# serverless.yml
custom:
  esbuild:
    bundle: true
    minify: true
    treeShaking: true
    exclude:
      - '@aws-sdk/*'  # Lambda에 포함된 SDK 제외
    external:
      - 'pg-native'   # 네이티브 모듈 제외
```

### 3. Provisioned Concurrency

```yaml
# serverless.yml
functions:
  createOrder:
    handler: handler.handler
    provisionedConcurrency: 5  # 항상 5개 인스턴스 대기
```

### 4. Lambda SnapStart (Java)

```yaml
# AWS SAM template
Resources:
  MyFunction:
    Type: AWS::Serverless::Function
    Properties:
      SnapStart:
        ApplyOn: PublishedVersions
```

---

## Serverless 패턴

```
┌─────────────────────────────────────────────────────────────────┐
│                        API Gateway                               │
│                            │                                     │
│      ┌────────────────────┼────────────────────┐                │
│      │                    │                    │                │
│      ▼                    ▼                    ▼                │
│  [Lambda A]          [Lambda B]          [Lambda C]             │
│  (주문 생성)          (주문 조회)          (결제 처리)            │
│      │                    │                    │                │
│      │                    │                    │                │
│      ▼                    ▼                    ▼                │
│  ┌──────┐            ┌──────┐            ┌──────┐              │
│  │ SQS  │            │  DB  │            │ SNS  │              │
│  │Queue │            │      │            │Topic │              │
│  └──────┘            └──────┘            └──────┘              │
│      │                                        │                 │
│      ▼                                        ▼                 │
│  [Lambda D]                              [Lambda E]             │
│  (알림 발송)                              (Webhook)             │
└─────────────────────────────────────────────────────────────────┘
```

### Fan-out 패턴

```typescript
// SNS → 여러 Lambda 동시 실행
// sns-publisher.ts
import { SNSClient, PublishCommand } from '@aws-sdk/client-sns';

const sns = new SNSClient({});

export async function publishOrderEvent(order: Order): Promise<void> {
  await sns.send(new PublishCommand({
    TopicArn: process.env.ORDER_TOPIC_ARN,
    Message: JSON.stringify({
      type: 'ORDER_CREATED',
      payload: order,
    }),
    MessageAttributes: {
      eventType: {
        DataType: 'String',
        StringValue: 'ORDER_CREATED',
      },
    },
  }));
}

// 여러 Lambda가 구독
// - inventory-handler: 재고 예약
// - notification-handler: 고객 알림
// - analytics-handler: 분석 데이터 수집
```

---

## Step Functions (워크플로우)

```json
{
  "Comment": "Order Processing Workflow",
  "StartAt": "ValidateOrder",
  "States": {
    "ValidateOrder": {
      "Type": "Task",
      "Resource": "arn:aws:lambda:...:validateOrder",
      "Next": "ProcessPayment",
      "Catch": [{
        "ErrorEquals": ["ValidationError"],
        "Next": "OrderFailed"
      }]
    },
    "ProcessPayment": {
      "Type": "Task",
      "Resource": "arn:aws:lambda:...:processPayment",
      "Next": "ReserveInventory",
      "Catch": [{
        "ErrorEquals": ["PaymentError"],
        "Next": "RefundPayment"
      }]
    },
    "ReserveInventory": {
      "Type": "Task",
      "Resource": "arn:aws:lambda:...:reserveInventory",
      "Next": "OrderComplete",
      "Catch": [{
        "ErrorEquals": ["InventoryError"],
        "Next": "RefundPayment"
      }]
    },
    "RefundPayment": {
      "Type": "Task",
      "Resource": "arn:aws:lambda:...:refundPayment",
      "Next": "OrderFailed"
    },
    "OrderComplete": {
      "Type": "Succeed"
    },
    "OrderFailed": {
      "Type": "Fail",
      "Error": "OrderProcessingFailed"
    }
  }
}
```

---

## Serverless 적합 사례

| 적합 | 부적합 |
|------|--------|
| ✅ API 백엔드 | ❌ 장시간 실행 작업 (>15분) |
| ✅ 이벤트 처리 | ❌ WebSocket (상태 유지) |
| ✅ 예약 작업 (Cron) | ❌ 고빈도 실시간 처리 |
| ✅ 파일 처리 | ❌ 대용량 메모리 필요 (>10GB) |
| ✅ Webhook 처리 | ❌ 예측 가능한 고부하 |
| ✅ 간헐적 트래픽 | ❌ 지속적 고부하 |

---

## 비용 최적화

### 1. 메모리 튜닝

```bash
# AWS Lambda Power Tuning 도구 사용
# 최적 메모리 설정 자동 탐색
```

### 2. Reserved Concurrency

```yaml
functions:
  heavyFunction:
    handler: handler.handler
    reservedConcurrency: 100  # 최대 동시 실행 제한
```

### 3. 적절한 타임아웃

```yaml
functions:
  quickFunction:
    handler: handler.handler
    timeout: 10  # 빠른 함수는 짧은 타임아웃
  slowFunction:
    handler: handler.handler
    timeout: 300  # 느린 함수만 긴 타임아웃
```
