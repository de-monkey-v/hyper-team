# API 설계 (REST, GraphQL, gRPC)

## 프로토콜 비교

| 항목 | REST | GraphQL | gRPC |
|------|------|---------|------|
| **프로토콜** | HTTP/1.1, HTTP/2 | HTTP/1.1, HTTP/2 | HTTP/2 (필수) |
| **데이터 형식** | JSON, XML | JSON | Protocol Buffers |
| **타입 안전성** | 낮음 (OpenAPI로 보완) | 높음 (스키마) | 높음 (Proto) |
| **성능** | 기준 | 비슷 | 10배 빠름 |
| **페이로드 크기** | 기준 | 비슷 | 3-10배 작음 |
| **학습 곡선** | 낮음 | 중간 | 높음 |
| **브라우저 지원** | 완벽 | 완벽 | 제한적 (grpc-web) |

---

## 선택 기준

```
┌─────────────────────────────────────────────────────────────────┐
│                        API 프로토콜 선택                         │
├─────────────────────────────────────────────────────────────────┤
│  Public API, 단순 CRUD           → REST                         │
│  복잡한 데이터 요구, 모바일 앱   → GraphQL                       │
│  마이크로서비스 간 통신          → gRPC                          │
│  실시간 스트리밍                 → gRPC (양방향), GraphQL (구독) │
│  브라우저 직접 호출              → REST 또는 GraphQL             │
└─────────────────────────────────────────────────────────────────┘
```

---

## REST Best Practices

### 리소스 네이밍

```typescript
// 리소스 기반 URL (명사 사용)
GET    /api/users              // 목록 조회
GET    /api/users/:id          // 단일 조회
POST   /api/users              // 생성
PUT    /api/users/:id          // 전체 수정
PATCH  /api/users/:id          // 부분 수정
DELETE /api/users/:id          // 삭제

// 중첩 리소스
GET    /api/users/:id/orders   // 특정 사용자의 주문 목록
POST   /api/users/:id/orders   // 특정 사용자의 주문 생성

// 필터링, 정렬, 페이지네이션
GET /api/products?category=electronics&sort=-price&page=1&limit=20
```

### 응답 형식 표준화

```typescript
// 성공 응답
{
  "data": { ... },
  "meta": {
    "page": 1,
    "limit": 20,
    "total": 100,
    "totalPages": 5
  }
}

// 목록 응답
{
  "data": [...],
  "meta": { ... }
}

// 에러 응답
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "입력값이 유효하지 않습니다",
    "details": [
      { "field": "email", "message": "유효한 이메일 형식이 아닙니다" }
    ]
  }
}
```

### HTTP 상태 코드

| 코드 | 의미 | 사용 |
|------|------|------|
| 200 | OK | 성공 (GET, PUT, PATCH) |
| 201 | Created | 생성 성공 (POST) |
| 204 | No Content | 삭제 성공 (DELETE) |
| 400 | Bad Request | 잘못된 요청 |
| 401 | Unauthorized | 인증 필요 |
| 403 | Forbidden | 권한 없음 |
| 404 | Not Found | 리소스 없음 |
| 409 | Conflict | 충돌 (중복 등) |
| 422 | Unprocessable Entity | 검증 실패 |
| 500 | Internal Server Error | 서버 오류 |

### REST 구현 예시

```typescript
@Controller('api/users')
export class UserController {
  constructor(private readonly userService: UserService) {}

  @Get()
  @ApiOperation({ summary: '사용자 목록 조회' })
  @ApiQuery({ name: 'page', required: false, type: Number })
  @ApiQuery({ name: 'limit', required: false, type: Number })
  async list(
    @Query('page', new DefaultValuePipe(1), ParseIntPipe) page: number,
    @Query('limit', new DefaultValuePipe(20), ParseIntPipe) limit: number,
  ): Promise<PaginatedResponse<UserDto>> {
    const { data, total } = await this.userService.findAll({ page, limit });
    return {
      data,
      meta: {
        page,
        limit,
        total,
        totalPages: Math.ceil(total / limit),
      },
    };
  }

  @Post()
  @HttpCode(201)
  @ApiOperation({ summary: '사용자 생성' })
  async create(@Body() dto: CreateUserDto): Promise<UserDto> {
    return this.userService.create(dto);
  }

  @Get(':id')
  @ApiOperation({ summary: '사용자 상세 조회' })
  async get(@Param('id') id: string): Promise<UserDto> {
    const user = await this.userService.findById(id);
    if (!user) throw new NotFoundException();
    return user;
  }

  @Patch(':id')
  @ApiOperation({ summary: '사용자 부분 수정' })
  async update(
    @Param('id') id: string,
    @Body() dto: UpdateUserDto,
  ): Promise<UserDto> {
    return this.userService.update(id, dto);
  }

  @Delete(':id')
  @HttpCode(204)
  @ApiOperation({ summary: '사용자 삭제' })
  async delete(@Param('id') id: string): Promise<void> {
    await this.userService.delete(id);
  }
}
```

---

## GraphQL 패턴

### 스키마 정의

```graphql
# schema.graphql
type Query {
  user(id: ID!): User
  users(filter: UserFilter, pagination: Pagination): UserConnection!
  order(id: ID!): Order
  orders(customerId: ID, pagination: Pagination): OrderConnection!
}

type Mutation {
  createUser(input: CreateUserInput!): User!
  updateUser(id: ID!, input: UpdateUserInput!): User!
  deleteUser(id: ID!): Boolean!
  createOrder(input: CreateOrderInput!): Order!
}

type Subscription {
  orderStatusChanged(orderId: ID!): Order!
  newNotification(userId: ID!): Notification!
}

# 타입 정의
type User {
  id: ID!
  email: String!
  name: String!
  orders: [Order!]!
  createdAt: DateTime!
}

type Order {
  id: ID!
  customer: User!
  items: [OrderItem!]!
  status: OrderStatus!
  totalAmount: Float!
  createdAt: DateTime!
}

# 페이지네이션 (Relay Cursor 스타일)
type UserConnection {
  edges: [UserEdge!]!
  pageInfo: PageInfo!
  totalCount: Int!
}

type UserEdge {
  node: User!
  cursor: String!
}

type PageInfo {
  hasNextPage: Boolean!
  hasPreviousPage: Boolean!
  startCursor: String
  endCursor: String
}

# Input 타입
input CreateUserInput {
  email: String!
  name: String!
  password: String!
}

input UserFilter {
  email: String
  name: String
  createdAfter: DateTime
}

input Pagination {
  first: Int
  after: String
  last: Int
  before: String
}
```

### N+1 문제 해결: DataLoader

```typescript
// DataLoader 정의
@Injectable({ scope: Scope.REQUEST })
export class OrderDataLoader {
  private loader: DataLoader<string, Order[]>;

  constructor(private readonly prisma: PrismaClient) {
    this.loader = new DataLoader(async (userIds: string[]) => {
      const orders = await this.prisma.order.findMany({
        where: { customerId: { in: userIds } },
      });

      // userId 순서에 맞게 그룹화
      const orderMap = new Map<string, Order[]>();
      for (const order of orders) {
        const existing = orderMap.get(order.customerId) || [];
        orderMap.set(order.customerId, [...existing, order]);
      }

      return userIds.map(id => orderMap.get(id) || []);
    });
  }

  load(userId: string): Promise<Order[]> {
    return this.loader.load(userId);
  }
}

// Resolver에서 사용
@Resolver(() => User)
export class UserResolver {
  constructor(private readonly orderLoader: OrderDataLoader) {}

  @ResolveField(() => [Order])
  async orders(@Parent() user: User): Promise<Order[]> {
    return this.orderLoader.load(user.id);
  }
}
```

### Query Complexity 제한

```typescript
// 복잡도 계산 플러그인
import { createComplexityPlugin, simpleEstimator } from 'graphql-query-complexity';

@Module({
  imports: [
    GraphQLModule.forRoot<ApolloDriverConfig>({
      driver: ApolloDriver,
      plugins: [
        createComplexityPlugin({
          estimators: [simpleEstimator({ defaultComplexity: 1 })],
          maximumComplexity: 100,
          onComplete: (complexity) => {
            console.log('Query Complexity:', complexity);
          },
        }),
      ],
    }),
  ],
})
export class AppModule {}
```

---

## gRPC 패턴

### Proto 정의

```protobuf
// order.proto
syntax = "proto3";
package order;

import "google/protobuf/timestamp.proto";

service OrderService {
  // 단순 RPC
  rpc GetOrder(GetOrderRequest) returns (Order);
  rpc CreateOrder(CreateOrderRequest) returns (Order);

  // 서버 스트리밍
  rpc ListOrders(ListOrdersRequest) returns (stream Order);

  // 클라이언트 스트리밍
  rpc CreateBulkOrders(stream CreateOrderRequest) returns (BulkOrderResponse);

  // 양방향 스트리밍
  rpc OrderChat(stream OrderMessage) returns (stream OrderMessage);
}

message Order {
  string id = 1;
  string customer_id = 2;
  repeated OrderItem items = 3;
  OrderStatus status = 4;
  double total_amount = 5;
  google.protobuf.Timestamp created_at = 6;
}

message OrderItem {
  string product_id = 1;
  int32 quantity = 2;
  double price = 3;
}

enum OrderStatus {
  ORDER_STATUS_UNSPECIFIED = 0;
  ORDER_STATUS_PENDING = 1;
  ORDER_STATUS_CONFIRMED = 2;
  ORDER_STATUS_SHIPPED = 3;
  ORDER_STATUS_DELIVERED = 4;
  ORDER_STATUS_CANCELLED = 5;
}

message GetOrderRequest {
  string id = 1;
}

message ListOrdersRequest {
  optional string customer_id = 1;
  optional OrderStatus status = 2;
  int32 page_size = 3;
  string page_token = 4;
}

message CreateOrderRequest {
  string customer_id = 1;
  repeated OrderItemInput items = 2;
}

message OrderItemInput {
  string product_id = 1;
  int32 quantity = 2;
}

message BulkOrderResponse {
  int32 success_count = 1;
  int32 failure_count = 2;
  repeated string created_ids = 3;
}

message OrderMessage {
  string order_id = 1;
  string message = 2;
  google.protobuf.Timestamp timestamp = 3;
}
```

### NestJS gRPC 구현

```typescript
// order.controller.ts
@Controller()
export class OrderController {
  constructor(private readonly orderService: OrderService) {}

  @GrpcMethod('OrderService', 'GetOrder')
  async getOrder(data: GetOrderRequest): Promise<Order> {
    return this.orderService.findById(data.id);
  }

  @GrpcMethod('OrderService', 'CreateOrder')
  async createOrder(data: CreateOrderRequest): Promise<Order> {
    return this.orderService.create({
      customerId: data.customerId,
      items: data.items,
    });
  }

  // 서버 스트리밍
  @GrpcStreamMethod('OrderService', 'ListOrders')
  listOrders(
    data: Observable<ListOrdersRequest>,
  ): Observable<Order> {
    return data.pipe(
      switchMap(request => {
        return from(this.orderService.findAll(request)).pipe(
          concatMap(orders => from(orders)),
        );
      }),
    );
  }
}
```

---

## 하이브리드 API 전략

여러 프로토콜을 목적에 맞게 조합:

```
┌─────────────────────────────────────────────────────────────┐
│                      클라이언트                              │
│                          │                                  │
│    ┌─────────┬───────────┴───────────┬─────────┐           │
│    │         │                       │         │           │
│    ▼         ▼                       ▼         ▼           │
│  [REST]  [GraphQL]              [gRPC-Web]  [SSE]          │
│  Public   Frontend               Admin     Realtime        │
│  API      BFF                    Tools     Events          │
│    │         │                       │         │           │
├────┴─────────┴───────────────────────┴─────────┴───────────┤
│                      API Gateway                            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   ┌──────────┐  gRPC  ┌──────────┐  gRPC  ┌──────────┐    │
│   │ User     │◄──────►│ Order    │◄──────►│ Payment  │    │
│   │ Service  │        │ Service  │        │ Service  │    │
│   └──────────┘        └──────────┘        └──────────┘    │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 사용 예시

| 클라이언트 | 프로토콜 | 이유 |
|-----------|---------|------|
| 웹 프론트엔드 | GraphQL | 유연한 데이터 요청 |
| 모바일 앱 | GraphQL | 네트워크 효율 |
| Public API | REST | 표준화, 캐싱 용이 |
| 마이크로서비스 간 | gRPC | 성능, 타입 안전 |
| 관리 도구 | gRPC-Web | 빠른 대용량 처리 |
| 실시간 알림 | SSE/WebSocket | 양방향 통신 |
