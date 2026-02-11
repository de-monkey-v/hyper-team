# Modular Monolith

마이크로서비스의 복잡성 없이 모듈화의 이점을 얻는 아키텍처.

## 마이크로서비스 vs 모듈러 모놀리스

| 항목 | 마이크로서비스 | 모듈러 모놀리스 |
|------|---------------|----------------|
| **배포** | 서비스별 독립 배포 | 단일 배포 단위 |
| **통신** | 네트워크 (HTTP, gRPC) | 프로세스 내 호출 |
| **데이터** | 서비스별 DB 분리 | 스키마 분리 또는 공유 |
| **트랜잭션** | 분산 트랜잭션 필요 | 로컬 트랜잭션 가능 |
| **복잡성** | 높음 (운영, 모니터링) | 낮음 |
| **팀 규모** | 10명+ 권장 | 제한 없음 |

## 팀 규모별 선택 기준

```
┌─────────────────────────────────────────────────────────────┐
│                      팀 규모별 권장                          │
├─────────────────────────────────────────────────────────────┤
│  1-5명   │ 단순 레이어드 또는 모듈러 모놀리스 (라이트)       │
│  5-10명  │ 모듈러 모놀리스                                   │
│  10-20명 │ 모듈러 모놀리스 또는 마이크로서비스 시작          │
│  20명+   │ 마이크로서비스 (팀별 서비스 소유)                 │
└─────────────────────────────────────────────────────────────┘
```

### 10명 미만 팀에서 마이크로서비스 문제점

- 디버깅에 35% 더 많은 시간 소요
- 운영 엔지니어 2-4명 추가 필요
- Docker/K8s 복잡성이 이점보다 큼
- 배포 파이프라인 관리 오버헤드

---

## 모듈 구조

```
src/
├── modules/
│   ├── user/                    # User 모듈
│   │   ├── domain/
│   │   │   ├── User.ts
│   │   │   └── UserRepository.ts
│   │   ├── application/
│   │   │   ├── CreateUserUseCase.ts
│   │   │   └── GetUserUseCase.ts
│   │   ├── infrastructure/
│   │   │   └── PrismaUserRepository.ts
│   │   ├── interfaces/
│   │   │   └── UserController.ts
│   │   ├── user.module.ts
│   │   └── index.ts             # Public API (export)
│   │
│   ├── order/                   # Order 모듈
│   │   ├── domain/
│   │   ├── application/
│   │   ├── infrastructure/
│   │   ├── interfaces/
│   │   ├── order.module.ts
│   │   └── index.ts
│   │
│   └── payment/                 # Payment 모듈
│       └── ...
│
├── shared/                      # 공유 코드
│   ├── domain/                  # 공유 Value Objects
│   │   ├── Money.ts
│   │   └── Email.ts
│   ├── infrastructure/          # 공유 인프라 (DB, 캐시)
│   │   └── database.ts
│   └── interfaces/              # 공유 미들웨어
│       └── auth.middleware.ts
│
└── app.module.ts                # 루트 모듈
```

---

## 모듈 간 통신 규칙

### 잘못된 방식: 내부 직접 접근

```typescript
// ❌ 다른 모듈 내부 직접 접근
import { UserRepository } from '../user/infrastructure/UserRepository';
import { User } from '../user/domain/User';

@Injectable()
export class OrderService {
  constructor(private readonly userRepo: UserRepository) {}

  async createOrder(userId: string) {
    // 다른 모듈 내부 구현에 의존
    const user = await this.userRepo.findById(userId);
  }
}
```

### 올바른 방식 1: 공개된 인터페이스 사용

```typescript
// modules/user/index.ts - Public API
export { UserService } from './application/UserService';
export { UserDto } from './application/dto/UserDto';
// 내부 구현은 export하지 않음

// modules/order/application/OrderService.ts
import { UserService } from '../../user';  // Public API만 사용

@Injectable()
export class OrderService {
  constructor(private readonly userService: UserService) {}

  async createOrder(userId: string) {
    const user = await this.userService.getUser(userId);
    // ...
  }
}
```

### 올바른 방식 2: 이벤트 기반 통신

```typescript
// Order 모듈: 이벤트 발행
@Injectable()
export class OrderService {
  constructor(private eventEmitter: EventEmitter2) {}

  async createOrder(dto: CreateOrderDto) {
    const order = await this.orderRepository.save(order);

    // 다른 모듈에 이벤트 발행
    this.eventEmitter.emit('order.created', new OrderCreatedEvent({
      orderId: order.id,
      userId: order.userId,
      totalAmount: order.totalAmount,
    }));

    return order;
  }
}

// User 모듈: 이벤트 구독
@Injectable()
export class UserEventHandler {
  constructor(private readonly userService: UserService) {}

  @OnEvent('order.created')
  async handleOrderCreated(event: OrderCreatedEvent) {
    await this.userService.incrementOrderCount(event.userId);
  }
}
```

### 올바른 방식 3: 인터페이스 기반 의존성 주입

```typescript
// shared/interfaces/IUserService.ts
export interface IUserService {
  getUser(id: string): Promise<UserDto | null>;
  validateUser(id: string): Promise<boolean>;
}

// modules/user/application/UserService.ts
@Injectable()
export class UserService implements IUserService {
  async getUser(id: string): Promise<UserDto | null> {
    // 구현
  }
}

// modules/order/order.module.ts
@Module({
  imports: [UserModule],
  providers: [
    OrderService,
    {
      provide: 'IUserService',
      useExisting: UserService,
    },
  ],
})
export class OrderModule {}

// modules/order/application/OrderService.ts
@Injectable()
export class OrderService {
  constructor(
    @Inject('IUserService')
    private readonly userService: IUserService,
  ) {}
}
```

---

## 모듈 경계 강제

### ESLint 규칙

```typescript
// eslint.config.js
export default [
  {
    rules: {
      'import/no-restricted-paths': ['error', {
        zones: [
          // user 모듈은 order 내부 직접 접근 불가
          {
            target: './src/modules/user',
            from: './src/modules/order',
            except: ['./index.ts'],  // public API만 허용
          },
          // order 모듈은 user 내부 직접 접근 불가
          {
            target: './src/modules/order',
            from: './src/modules/user',
            except: ['./index.ts'],
          },
          // 모든 모듈은 다른 모듈의 infrastructure 접근 불가
          {
            target: './src/modules/*',
            from: './src/modules/*/infrastructure',
          },
        ],
      }],
    },
  },
];
```

### ArchUnit 스타일 테스트 (Jest)

```typescript
// architecture.spec.ts
import { glob } from 'glob';
import * as fs from 'fs';

describe('Module Architecture', () => {
  const modules = ['user', 'order', 'payment'];

  modules.forEach(moduleName => {
    it(`${moduleName} 모듈은 다른 모듈 내부에 직접 접근하지 않음`, () => {
      const files = glob.sync(`src/modules/${moduleName}/**/*.ts`);

      files.forEach(file => {
        const content = fs.readFileSync(file, 'utf-8');

        modules.filter(m => m !== moduleName).forEach(otherModule => {
          // index.ts를 통한 import만 허용
          const badImport = new RegExp(
            `from ['"].*/${otherModule}/(?!index)`,
          );
          expect(content).not.toMatch(badImport);
        });
      });
    });
  });
});
```

---

## 데이터베이스 분리 전략

### 옵션 1: 스키마 분리 (권장)

```sql
-- user 스키마
CREATE SCHEMA user_schema;
CREATE TABLE user_schema.users (...);
CREATE TABLE user_schema.user_preferences (...);

-- order 스키마
CREATE SCHEMA order_schema;
CREATE TABLE order_schema.orders (...);
CREATE TABLE order_schema.order_items (...);

-- 스키마 간 참조 시 ID만 저장
CREATE TABLE order_schema.orders (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,  -- user_schema.users.id 참조 (FK 없음)
  ...
);
```

### 옵션 2: 테이블 프리픽스

```typescript
// Prisma 설정
// prisma/schema.prisma
model User {
  @@map("user_users")
}

model Order {
  @@map("order_orders")
}
```

---

## 마이크로서비스 전환 전략

### Phase 1: 모듈러 모놀리스

```
┌─────────────────────────────────────┐
│  [User] ──── [Order] ──── [Payment] │
│            (프로세스 내)             │
└─────────────────────────────────────┘
```

### Phase 2: 이벤트 기반 분리 준비

```
┌─────────────────────────────────────────────────┐
│  [User] ←─Event─→ [Order] ←─Event─→ [Payment]  │
│               (이벤트 버스)                      │
└─────────────────────────────────────────────────┘
```

### Phase 3: 서비스 추출 (필요한 것만)

```
┌───────────┐     ┌───────────────────┐
│ [Payment] │ ←── │ [User] ── [Order] │
│ (별도 서비스)│    │    (모놀리스)       │
└───────────┘     └───────────────────┘
```

### 전환 체크리스트

- [ ] 모듈 간 통신이 이벤트 기반으로 전환되었는가?
- [ ] 데이터베이스 스키마가 분리되어 있는가?
- [ ] 모듈 간 동기 호출이 최소화되었는가?
- [ ] 각 모듈이 독립적으로 테스트 가능한가?
- [ ] 추출할 서비스의 비즈니스 가치가 명확한가?

---

## 실전 예시

상세 구현 예시: `examples/modular-monolith-example.md` 참조
