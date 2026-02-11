# 테스트 작성 가이드

구현과 함께 작성해야 할 테스트 패턴.

## 테스트 유형

| 유형 | 범위 | 속도 | 목적 |
|------|------|------|------|
| Unit | 함수/클래스 | 빠름 | 로직 검증 |
| Integration | 모듈 간 | 보통 | 연동 검증 |
| E2E | 전체 시스템 | 느림 | 사용자 시나리오 |

## Unit 테스트

### 기본 구조

```typescript
// src/domain/user/__tests__/User.test.ts
import { describe, it, expect } from 'vitest';
import { User } from '../User';

describe('User', () => {
  describe('create', () => {
    it('should create a new user with valid data', () => {
      const user = User.create({
        email: 'test@example.com',
        name: 'Test User',
      });

      expect(user.email).toBe('test@example.com');
      expect(user.name).toBe('Test User');
      expect(user.id).toBeDefined();
    });

    it('should throw error for invalid email', () => {
      expect(() => {
        User.create({ email: 'invalid', name: 'Test' });
      }).toThrow('Invalid email format');
    });
  });

  describe('changeName', () => {
    it('should return new user with updated name', () => {
      const user = User.create({ email: 'test@example.com', name: 'Old' });
      const updated = user.changeName('New');

      expect(updated.name).toBe('New');
      expect(updated.id).toBe(user.id); // ID 유지
      expect(user.name).toBe('Old'); // 원본 불변
    });
  });
});
```

### Mocking

```typescript
// src/application/user/__tests__/CreateUserUseCase.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { CreateUserUseCase } from '../CreateUserUseCase';
import { UserRepository } from '../../../domain/user/UserRepository';

describe('CreateUserUseCase', () => {
  let useCase: CreateUserUseCase;
  let mockRepository: UserRepository;

  beforeEach(() => {
    mockRepository = {
      save: vi.fn(),
      findById: vi.fn(),
      findByEmail: vi.fn(),
      existsByEmail: vi.fn(),
    };
    useCase = new CreateUserUseCase(mockRepository);
  });

  it('should create user when email is unique', async () => {
    vi.mocked(mockRepository.existsByEmail).mockResolvedValue(false);
    vi.mocked(mockRepository.save).mockImplementation(async (user) => user);

    const result = await useCase.execute({
      email: 'new@example.com',
      name: 'New User',
    });

    expect(result.email).toBe('new@example.com');
    expect(mockRepository.save).toHaveBeenCalledOnce();
  });

  it('should throw error when email exists', async () => {
    vi.mocked(mockRepository.existsByEmail).mockResolvedValue(true);

    await expect(
      useCase.execute({ email: 'exists@example.com', name: 'User' })
    ).rejects.toThrow('Email already exists');

    expect(mockRepository.save).not.toHaveBeenCalled();
  });
});
```

## Integration 테스트

### API 테스트

```typescript
// src/interfaces/http/__tests__/users.test.ts
import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { createTestApp, TestApp } from '../../../test/utils';

describe('Users API', () => {
  let app: TestApp;

  beforeAll(async () => {
    app = await createTestApp();
  });

  afterAll(async () => {
    await app.close();
  });

  describe('POST /users', () => {
    it('should create a new user', async () => {
      const response = await app.request('/users', {
        method: 'POST',
        body: JSON.stringify({
          email: 'test@example.com',
          name: 'Test User',
        }),
      });

      expect(response.status).toBe(201);
      const data = await response.json();
      expect(data.email).toBe('test@example.com');
    });

    it('should return 409 for duplicate email', async () => {
      // First create
      await app.request('/users', {
        method: 'POST',
        body: JSON.stringify({ email: 'dup@example.com', name: 'First' }),
      });

      // Duplicate
      const response = await app.request('/users', {
        method: 'POST',
        body: JSON.stringify({ email: 'dup@example.com', name: 'Second' }),
      });

      expect(response.status).toBe(409);
    });
  });
});
```

### Database 테스트

```typescript
// src/infrastructure/database/__tests__/PrismaUserRepository.test.ts
import { describe, it, expect, beforeEach } from 'vitest';
import { PrismaClient } from '@prisma/client';
import { PrismaUserRepository } from '../repositories/PrismaUserRepository';
import { User } from '../../../domain/user/User';

describe('PrismaUserRepository', () => {
  let prisma: PrismaClient;
  let repository: PrismaUserRepository;

  beforeEach(async () => {
    prisma = new PrismaClient();
    repository = new PrismaUserRepository(prisma);
    // 테스트 데이터 정리
    await prisma.user.deleteMany();
  });

  it('should save and retrieve user', async () => {
    const user = User.create({ email: 'test@example.com', name: 'Test' });

    await repository.save(user);
    const found = await repository.findById(user.id);

    expect(found).not.toBeNull();
    expect(found?.email).toBe('test@example.com');
  });
});
```

## React 컴포넌트 테스트

### Testing Library

```tsx
// src/features/user/__tests__/UserForm.test.tsx
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { UserForm } from '../UserForm';

// Mock server action
vi.mock('../actions', () => ({
  createUserAction: vi.fn(),
}));

import { createUserAction } from '../actions';

describe('UserForm', () => {
  it('should render form fields', () => {
    render(<UserForm />);

    expect(screen.getByPlaceholderText('Email')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Name')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '생성' })).toBeInTheDocument();
  });

  it('should submit form with entered data', async () => {
    const user = userEvent.setup();
    vi.mocked(createUserAction).mockResolvedValue(undefined);

    render(<UserForm />);

    await user.type(screen.getByPlaceholderText('Email'), 'test@example.com');
    await user.type(screen.getByPlaceholderText('Name'), 'Test User');
    await user.click(screen.getByRole('button', { name: '생성' }));

    await waitFor(() => {
      expect(createUserAction).toHaveBeenCalledWith({
        email: 'test@example.com',
        name: 'Test User',
      });
    });
  });

  it('should display error message on failure', async () => {
    const user = userEvent.setup();
    vi.mocked(createUserAction).mockRejectedValue(new Error('Email exists'));

    render(<UserForm />);

    await user.type(screen.getByPlaceholderText('Email'), 'exists@example.com');
    await user.type(screen.getByPlaceholderText('Name'), 'Test');
    await user.click(screen.getByRole('button', { name: '생성' }));

    await waitFor(() => {
      expect(screen.getByText('Email exists')).toBeInTheDocument();
    });
  });
});
```

## 테스트 명명 규칙

### describe 블록
```typescript
describe('UserService', () => {           // 테스트 대상
  describe('createUser', () => {          // 메서드/기능
    it('should ...', () => {});           // 기대 동작
  });
});
```

### it 문장
```typescript
// Good: 명확한 기대 동작
it('should create user when email is unique', () => {});
it('should throw error when email already exists', () => {});
it('should return null when user not found', () => {});

// Bad: 불명확
it('works', () => {});
it('test createUser', () => {});
```

## 테스트 실행

```bash
# 전체 테스트
npm test

# Watch 모드
npm test -- --watch

# 커버리지
npm test -- --coverage

# 특정 파일
npm test -- src/domain/user
```

## 커버리지 기준

| 항목 | 최소 | 권장 |
|------|------|------|
| Statements | 70% | 80% |
| Branches | 70% | 80% |
| Functions | 70% | 80% |
| Lines | 70% | 80% |
