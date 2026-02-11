# 아키텍처 패턴 가이드

구현 시 준수해야 할 아키텍처 패턴과 원칙.

> 📖 **상세 가이드**: `skills/architecture-guide/SKILL.md` 참조
> - Modular Monolith, Vertical Slice Architecture
> - REST/GraphQL/gRPC 비교
> - Event-Driven Architecture, Serverless
> - AI/ML 통합 아키텍처

## 백엔드 아키텍처

### Clean Architecture 레이어

```
src/
├── domain/           # 핵심 비즈니스 로직 (의존성 없음)
│   ├── user/
│   │   ├── User.ts           # 엔티티
│   │   ├── UserRepository.ts # 인터페이스
│   │   └── UserService.ts    # 도메인 서비스
│   └── order/
├── application/      # 유스케이스 (도메인만 의존)
│   ├── user/
│   │   ├── CreateUserUseCase.ts
│   │   ├── GetUserUseCase.ts
│   │   └── dto/
│   └── order/
├── infrastructure/   # 외부 시스템 (모든 레이어 의존 가능)
│   ├── database/
│   │   ├── prisma/
│   │   └── repositories/
│   │       └── PrismaUserRepository.ts
│   ├── external/
│   │   └── EmailService.ts
│   └── config/
└── interfaces/       # 진입점
    ├── http/
    │   ├── controllers/
    │   └── middlewares/
    └── cli/
```

### 의존성 규칙

```
interfaces → application → domain
     ↓            ↓
infrastructure ───┘
```

**핵심 규칙:**
- Domain은 아무것도 의존하지 않음
- Application은 Domain만 의존
- Infrastructure는 Domain, Application 의존 가능
- Interfaces는 모든 레이어 의존 가능

### 코드 예시

#### Domain 레이어
```typescript
// src/domain/user/User.ts
export class User {
  private constructor(
    public readonly id: string,
    public readonly email: string,
    public readonly name: string,
    public readonly createdAt: Date
  ) {}

  static create(props: { email: string; name: string }): User {
    return new User(
      crypto.randomUUID(),
      props.email,
      props.name,
      new Date()
    );
  }

  changeName(newName: string): User {
    return new User(this.id, this.email, newName, this.createdAt);
  }
}

// src/domain/user/UserRepository.ts
export interface UserRepository {
  save(user: User): Promise<User>;
  findById(id: string): Promise<User | null>;
  findByEmail(email: string): Promise<User | null>;
  existsByEmail(email: string): Promise<boolean>;
}
```

#### Application 레이어
```typescript
// src/application/user/CreateUserUseCase.ts
import { User } from '../../domain/user/User';
import { UserRepository } from '../../domain/user/UserRepository';
import { CreateUserDto } from './dto/CreateUserDto';

export class CreateUserUseCase {
  constructor(private readonly userRepository: UserRepository) {}

  async execute(dto: CreateUserDto): Promise<User> {
    const exists = await this.userRepository.existsByEmail(dto.email);
    if (exists) {
      throw new Error('Email already exists');
    }

    const user = User.create({
      email: dto.email,
      name: dto.name,
    });

    return this.userRepository.save(user);
  }
}
```

#### Infrastructure 레이어
```typescript
// src/infrastructure/database/repositories/PrismaUserRepository.ts
import { PrismaClient } from '@prisma/client';
import { User } from '../../../domain/user/User';
import { UserRepository } from '../../../domain/user/UserRepository';

export class PrismaUserRepository implements UserRepository {
  constructor(private readonly prisma: PrismaClient) {}

  async save(user: User): Promise<User> {
    const data = await this.prisma.user.create({
      data: {
        id: user.id,
        email: user.email,
        name: user.name,
        createdAt: user.createdAt,
      },
    });
    return this.toDomain(data);
  }

  private toDomain(data: { id: string; email: string; name: string; createdAt: Date }): User {
    return User.reconstitute(data);
  }
}
```

## 프론트엔드 아키텍처

### Feature-Sliced Design

```
src/
├── app/              # 앱 설정, 라우팅
│   ├── layout.tsx
│   ├── page.tsx
│   └── providers.tsx
├── pages/            # 페이지 컴포넌트 (라우트별)
│   └── users/
│       ├── page.tsx
│       └── [id]/page.tsx
├── widgets/          # 독립적인 UI 블록
│   ├── Header/
│   └── Sidebar/
├── features/         # 사용자 시나리오
│   ├── auth/
│   │   ├── LoginForm.tsx
│   │   ├── useAuth.ts
│   │   └── actions.ts
│   └── user/
│       ├── UserList.tsx
│       ├── UserForm.tsx
│       └── actions.ts
├── entities/         # 비즈니스 엔티티
│   ├── user/
│   │   ├── model.ts
│   │   └── ui/UserCard.tsx
│   └── order/
└── shared/           # 공용 유틸, UI
    ├── ui/
    │   ├── Button.tsx
    │   └── Input.tsx
    ├── lib/
    │   └── api.ts
    └── config/
```

### 의존성 규칙

```
app → pages → widgets → features → entities → shared
```

**핵심 규칙:**
- 상위 레이어는 하위 레이어만 의존
- 같은 레이어 간 의존 금지
- shared는 어디서든 사용 가능

### 코드 예시

#### Feature 레이어
```tsx
// src/features/user/UserForm.tsx
'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { Input } from '@/shared/ui/Input';
import { createUserAction } from './actions';

export function UserForm() {
  const [formData, setFormData] = useState({ email: '', name: '' });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    await createUserAction(formData);
  };

  return (
    <form onSubmit={handleSubmit}>
      <Input
        value={formData.email}
        onChange={(e) => setFormData({ ...formData, email: e.target.value })}
        placeholder="Email"
      />
      <Input
        value={formData.name}
        onChange={(e) => setFormData({ ...formData, name: e.target.value })}
        placeholder="Name"
      />
      <Button type="submit">생성</Button>
    </form>
  );
}
```

#### Server Actions
```typescript
// src/features/user/actions.ts
'use server';

import { revalidatePath } from 'next/cache';
import { prisma } from '@/shared/lib/prisma';

export async function createUserAction(data: { email: string; name: string }) {
  await prisma.user.create({ data });
  revalidatePath('/users');
}

export async function getUsersAction() {
  return prisma.user.findMany({
    orderBy: { createdAt: 'desc' },
  });
}
```

## SOLID 원칙

### S - 단일 책임 원칙
```typescript
// Bad: 여러 책임
class UserService {
  createUser() { /* ... */ }
  sendEmail() { /* ... */ }
  generateReport() { /* ... */ }
}

// Good: 단일 책임
class UserService { createUser() { /* ... */ } }
class EmailService { sendEmail() { /* ... */ } }
class ReportService { generateReport() { /* ... */ } }
```

### O - 개방-폐쇄 원칙
```typescript
// 확장에 열림, 수정에 닫힘
interface PaymentStrategy {
  pay(amount: number): Promise<void>;
}

class CreditCardPayment implements PaymentStrategy { /* ... */ }
class PayPalPayment implements PaymentStrategy { /* ... */ }
// 새 결제 방식 추가 시 기존 코드 수정 불필요
```

### L - 리스코프 치환 원칙
```typescript
// 하위 타입은 상위 타입을 대체 가능해야 함
class Bird { fly() { /* ... */ } }
class Penguin extends Bird { fly() { throw new Error(); } } // Bad!

// Good: 인터페이스 분리
interface Flyable { fly(): void; }
interface Swimmable { swim(): void; }
class Sparrow implements Flyable { /* ... */ }
class Penguin implements Swimmable { /* ... */ }
```

### I - 인터페이스 분리 원칙
```typescript
// Bad: 큰 인터페이스
interface Worker {
  work(): void;
  eat(): void;
  sleep(): void;
}

// Good: 분리된 인터페이스
interface Workable { work(): void; }
interface Eatable { eat(): void; }
```

### D - 의존성 역전 원칙
```typescript
// Bad: 구체 클래스 의존
class UserService {
  private db = new MySQLDatabase();
}

// Good: 추상화 의존
class UserService {
  constructor(private db: Database) {}
}
```
