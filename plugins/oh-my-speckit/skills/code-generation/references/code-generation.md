# 코드 생성 가이드

plan.md의 체크리스트를 기반으로 코드를 생성하는 상세 절차.

## 코드 작성 전 필수 체크 (CRITICAL)

### 1. 기존 코드 검색 체크리스트

**유틸 함수 작성 전:**
```bash
# 이미 존재하는지 확인
grep -r "function 함수명" src/
grep -r "export.*함수명" src/
```

**타입 정의 전:**
```bash
# 이미 정의되어 있는지 확인
grep -r "interface 타입명" src/
grep -r "type 타입명" src/
```

**컴포넌트 작성 전:**
```bash
# 유사 컴포넌트 확인
ls src/components/ | grep -i "컴포넌트명"
```

### 2. 재사용 우선순위

1. **직접 Import** (최우선)
   ```typescript
   // 기존 유틸 그대로 사용
   import { validateEmail } from '@/utils/validation';
   ```

2. **확장/래핑** (2순위)
   ```typescript
   // 기존 함수를 확장
   import { baseValidate } from '@/utils/validation';
   export const validateUser = (user: User) => {
     baseValidate(user.email);
     // 추가 검증만
   };
   ```

3. **패턴 복사 후 수정** (3순위)
   ```typescript
   // 기존 패턴 참고하여 작성
   // 참고: src/features/auth/useAuth.ts
   ```

4. **새로 작성** (최후 수단)
   - 위 3가지 모두 불가능할 때만

### 3. 금지 패턴

**절대 하지 말 것:**

```typescript
// ❌ 이미 있는 유틸 재작성
export function formatDate(date: Date) { ... }
// 이미 src/utils/date.ts에 있음!

// ❌ 기존 타입과 중복
interface UserResponse { ... }
// 이미 src/types/user.ts에 있음!

// ❌ 기존 패턴 무시하고 새 패턴
// 기존: src/api/users.ts - React Query 사용
// 새로: fetch로 직접 호출 ← 금지!
```

---

## 생성 원칙

### 1. plan.md 체크리스트 순서 준수

plan.md의 Phase별 체크박스를 **순서대로** 구현:

```markdown
### Phase 1: 도메인 모델
- [ ] User 엔티티 생성        ← 1번째
- [ ] UserRepository 인터페이스 ← 2번째
- [ ] CreateUserUseCase       ← 3번째
```

### 2. 완료 시 체크박스 업데이트

각 항목 완료 후 plan.md 수정:
```markdown
- [x] User 엔티티 생성  ← 완료 표시
```

### 3. 점진적 구현

한 번에 모든 코드를 생성하지 않고, 파일 단위로 점진적 구현:
1. 파일 생성/수정
2. 관련 import 확인
3. 타입 체크 (필요시)
4. 다음 파일로 이동

## 생성 패턴

### TypeScript/JavaScript

#### 서비스 클래스
```typescript
// src/application/user/CreateUserService.ts
import { Injectable } from '@nestjs/common';
import { UserRepository } from '../../domain/user/UserRepository';
import { User } from '../../domain/user/User';
import { CreateUserDto } from './dto/CreateUserDto';

@Injectable()
export class CreateUserService {
  constructor(private readonly userRepository: UserRepository) {}

  async execute(dto: CreateUserDto): Promise<User> {
    // 1. 유효성 검사
    await this.validateEmail(dto.email);

    // 2. 엔티티 생성
    const user = User.create({
      email: dto.email,
      name: dto.name,
    });

    // 3. 저장
    return this.userRepository.save(user);
  }

  private async validateEmail(email: string): Promise<void> {
    const exists = await this.userRepository.existsByEmail(email);
    if (exists) {
      throw new ConflictException('Email already exists');
    }
  }
}
```

#### React 컴포넌트 (Server Component)
```tsx
// src/app/users/page.tsx
import { UserList } from '@/features/user/UserList';
import { getUsersAction } from '@/features/user/actions';

export default async function UsersPage() {
  const users = await getUsersAction();

  return (
    <main className="container mx-auto py-8">
      <h1 className="text-2xl font-bold mb-4">사용자 목록</h1>
      <UserList users={users} />
    </main>
  );
}
```

#### React 컴포넌트 (Client Component)
```tsx
// src/features/user/UserForm.tsx
'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { createUserAction } from './actions';

interface Props {
  onSuccess?: () => void;
}

export function UserForm({ onSuccess }: Props) {
  const [email, setEmail] = useState('');
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const router = useRouter();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setIsLoading(true);

    try {
      await createUserAction({ email, name });
      router.refresh();
      onSuccess?.();
    } catch (err) {
      setError(err instanceof Error ? err.message : '오류가 발생했습니다');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      {error && <div className="text-red-500">{error}</div>}
      {/* form fields */}
    </form>
  );
}
```

### Python

#### FastAPI 엔드포인트
```python
# src/api/users.py
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from src.database import get_db
from src.schemas.user import UserCreate, UserResponse
from src.services.user_service import UserService

router = APIRouter(prefix="/users", tags=["users"])

@router.post("/", response_model=UserResponse)
async def create_user(
    user_data: UserCreate,
    db: Session = Depends(get_db)
) -> UserResponse:
    service = UserService(db)

    if await service.email_exists(user_data.email):
        raise HTTPException(status_code=409, detail="Email already exists")

    user = await service.create(user_data)
    return UserResponse.from_orm(user)
```

#### Pydantic 스키마
```python
# src/schemas/user.py
from pydantic import BaseModel, EmailStr
from datetime import datetime

class UserCreate(BaseModel):
    email: EmailStr
    name: str

class UserResponse(BaseModel):
    id: int
    email: str
    name: str
    created_at: datetime

    class Config:
        from_attributes = True
```

## 네이밍 컨벤션

### 파일명

| 유형 | 패턴 | 예시 |
|------|------|------|
| 서비스 | `{Action}{Entity}Service` | `CreateUserService.ts` |
| 컨트롤러 | `{Entity}Controller` | `UserController.ts` |
| 리포지토리 | `{Entity}Repository` | `UserRepository.ts` |
| DTO | `{Action}{Entity}Dto` | `CreateUserDto.ts` |
| 컴포넌트 | `{Entity}{Type}` | `UserForm.tsx`, `UserList.tsx` |
| 훅 | `use{Feature}` | `useUsers.ts` |

### 변수/함수명

| 유형 | 패턴 | 예시 |
|------|------|------|
| 비동기 함수 | 동사 + 명사 | `fetchUsers`, `createUser` |
| 이벤트 핸들러 | `handle{Event}` | `handleSubmit`, `handleClick` |
| Boolean | `is/has/can` + 형용사 | `isLoading`, `hasError` |
| 배열 | 복수형 | `users`, `items` |

## Import 정리

### 순서
1. 외부 라이브러리 (react, next, lodash 등)
2. 내부 절대 경로 (@/features, @/components)
3. 상대 경로 (./utils, ../types)
4. 타입 import (type-only imports)

### 예시
```typescript
// 외부 라이브러리
import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';

// 내부 절대 경로
import { Button } from '@/components/ui/Button';
import { useAuth } from '@/features/auth/useAuth';

// 상대 경로
import { validateEmail } from './utils';

// 타입
import type { User } from '@/types';
```

## 에러 처리

### 일관된 에러 패턴

```typescript
// 커스텀 에러 클래스
export class DomainError extends Error {
  constructor(
    message: string,
    public readonly code: string,
    public readonly statusCode: number = 400
  ) {
    super(message);
    this.name = 'DomainError';
  }
}

// 사용
throw new DomainError(
  'Email already exists',
  'USER_EMAIL_DUPLICATE',
  409
);
```

### Try-Catch 패턴

```typescript
try {
  const result = await riskyOperation();
  return { success: true, data: result };
} catch (error) {
  if (error instanceof DomainError) {
    return { success: false, error: error.message, code: error.code };
  }
  // 예상치 못한 에러는 로깅 후 일반 메시지
  console.error('Unexpected error:', error);
  return { success: false, error: '서버 오류가 발생했습니다' };
}
```
