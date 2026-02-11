# 실전 예시: API V2 마이그레이션

## 시나리오

기존 `getUsers()` API의 응답 구조 변경:
- **Before**: `{ name: "John Doe" }`
- **After**: `{ firstName: "John", lastName: "Doe" }`

이는 **Breaking Change**이므로 V2 API 생성 필요.

## 마이그레이션 단계

```
1. V2 DTO 생성
2. V2 UseCase 생성
3. 기존 코드에 @deprecated 추가
4. V2 Controller 추가
5. 마이그레이션 가이드 작성
6. 테스트 추가
```

## Step 1: V2 DTO 생성

```typescript
// src/domain/dto/user.dto.ts (기존)
export interface UserDto {
  id: string;
  name: string;  // 기존 형식
  email: string;
  createdAt: Date;
}

// src/domain/dto/user-v2.dto.ts (신규)
export interface UserDtoV2 {
  id: string;
  firstName: string;  // 분리됨
  lastName: string;   // 분리됨
  email: string;
  createdAt: Date;
}
```

## Step 2: V2 UseCase 생성

```typescript
// src/application/use-cases/get-users.use-case.ts (기존 - 수정하지 않음!)
import { Injectable } from '@nestjs/common';
import { UserRepository } from '../../domain/user/UserRepository';
import { UserDto } from '../../domain/dto/user.dto';

/**
 * @deprecated Use GetUsersV2UseCase instead. Will be removed in v3.0.0
 * @see GetUsersV2UseCase
 */
@Injectable()
export class GetUsersUseCase {
  constructor(private readonly userRepository: UserRepository) {}

  async execute(): Promise<UserDto[]> {
    const users = await this.userRepository.findAll();
    return users.map(user => ({
      id: user.id,
      name: user.name,  // 기존 로직 그대로 유지
      email: user.email,
      createdAt: user.createdAt,
    }));
  }
}

// src/application/use-cases/get-users-v2.use-case.ts (신규)
import { Injectable } from '@nestjs/common';
import { UserRepository } from '../../domain/user/UserRepository';
import { UserDtoV2 } from '../../domain/dto/user-v2.dto';

@Injectable()
export class GetUsersV2UseCase {
  constructor(private readonly userRepository: UserRepository) {}

  async execute(): Promise<UserDtoV2[]> {
    const users = await this.userRepository.findAll();
    return users.map(user => {
      // name을 firstName, lastName으로 분리
      const [firstName, ...lastNameParts] = user.name.split(' ');
      const lastName = lastNameParts.join(' ') || '';

      return {
        id: user.id,
        firstName,
        lastName,
        email: user.email,
        createdAt: user.createdAt,
      };
    });
  }
}
```

## Step 3: 기존 Controller에 @deprecated 추가

```typescript
// src/interfaces/http/controllers/users.controller.ts (기존)
import { Controller, Get } from '@nestjs/common';
import { ApiTags, ApiOperation, ApiResponse } from '@nestjs/swagger';
import { GetUsersUseCase } from '../../../application/use-cases/get-users.use-case';
import { UserDto } from '../../../domain/dto/user.dto';

@ApiTags('users')
@Controller('users')
export class UsersController {
  constructor(private readonly getUsersUseCase: GetUsersUseCase) {}

  /**
   * @deprecated Use GET /v2/users instead. Will be removed in v3.0.0
   */
  @Get()
  @ApiOperation({
    summary: '사용자 목록 조회',
    deprecated: true,
    description: 'Deprecated: Use GET /v2/users instead',
  })
  @ApiResponse({ status: 200, type: [UserDto] })
  async list(): Promise<UserDto[]> {
    return this.getUsersUseCase.execute();
  }
}
```

## Step 4: V2 Controller 추가

```typescript
// src/interfaces/http/v2/users.controller.ts (신규)
import { Controller, Get } from '@nestjs/common';
import { ApiTags, ApiOperation, ApiResponse } from '@nestjs/swagger';
import { GetUsersV2UseCase } from '../../../application/use-cases/get-users-v2.use-case';
import { UserDtoV2 } from '../../../domain/dto/user-v2.dto';

@ApiTags('users')
@Controller('v2/users')
export class UsersV2Controller {
  constructor(private readonly getUsersV2UseCase: GetUsersV2UseCase) {}

  @Get()
  @ApiOperation({ summary: '사용자 목록 조회 (V2)' })
  @ApiResponse({ status: 200, type: [UserDtoV2] })
  async list(): Promise<UserDtoV2[]> {
    return this.getUsersV2UseCase.execute();
  }
}
```

## Step 5: 모듈 업데이트

```typescript
// src/modules/users.module.ts
import { Module } from '@nestjs/common';
import { UsersController } from '../interfaces/http/controllers/users.controller';
import { UsersV2Controller } from '../interfaces/http/v2/users.controller';
import { GetUsersUseCase } from '../application/use-cases/get-users.use-case';
import { GetUsersV2UseCase } from '../application/use-cases/get-users-v2.use-case';
import { PrismaUserRepository } from '../infrastructure/repositories/PrismaUserRepository';
import { UserRepository } from '../domain/user/UserRepository';

@Module({
  controllers: [
    UsersController,      // 기존 (deprecated)
    UsersV2Controller,    // 신규
  ],
  providers: [
    GetUsersUseCase,      // 기존 (deprecated)
    GetUsersV2UseCase,    // 신규
    { provide: UserRepository, useClass: PrismaUserRepository },
  ],
})
export class UsersModule {}
```

## Step 6: 디렉토리 구조

```
src/
├── interfaces/
│   └── http/
│       ├── controllers/
│       │   └── users.controller.ts     # 기존 (deprecated)
│       └── v2/
│           └── users.controller.ts     # 신규
├── application/
│   └── use-cases/
│       ├── get-users.use-case.ts       # 기존 (deprecated)
│       └── get-users-v2.use-case.ts    # 신규
└── domain/
    └── dto/
        ├── user.dto.ts                 # 기존
        └── user-v2.dto.ts              # 신규
```

## Step 7: 마이그레이션 가이드 문서화

```markdown
# Migration Guide: /api/users → /api/v2/users

## Breaking Changes

- `name` 필드가 `firstName`, `lastName`으로 분리됨

## Endpoints

| Version | Endpoint | Status |
|---------|----------|--------|
| V1 | GET /api/users | Deprecated |
| V2 | GET /api/v2/users | Current |

## Response Comparison

### V1 (Deprecated)

```json
GET /api/users

[
  {
    "id": "123",
    "name": "John Doe",
    "email": "john@example.com",
    "createdAt": "2024-01-01T00:00:00Z"
  }
]
```

### V2 (Current)

```json
GET /api/v2/users

[
  {
    "id": "123",
    "firstName": "John",
    "lastName": "Doe",
    "email": "john@example.com",
    "createdAt": "2024-01-01T00:00:00Z"
  }
]
```

## Migration Steps

### 1. Update API Endpoint

```typescript
// Before
const response = await fetch('/api/users');

// After
const response = await fetch('/api/v2/users');
```

### 2. Update Response Handling

```typescript
// Before
interface User {
  id: string;
  name: string;
  email: string;
}

const displayName = user.name;

// After
interface UserV2 {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
}

const displayName = `${user.firstName} ${user.lastName}`;
```

### 3. Update Type Definitions

```typescript
// Before
type User = {
  name: string;
  // ...
}

// After
type User = {
  firstName: string;
  lastName: string;
  // ...
}
```

## Timeline

| Date | Action |
|------|--------|
| 2024-01-01 | V2 API 출시 |
| 2024-01-01 | V1 API Deprecated 표시 |
| 2024-06-01 | V1 API 제거 예정 (v3.0.0) |

## Support

마이그레이션 관련 문의: api-support@example.com
```

## Step 8: 테스트 추가

```typescript
// src/application/use-cases/__tests__/get-users-v2.use-case.test.ts
import { Test } from '@nestjs/testing';
import { GetUsersV2UseCase } from '../get-users-v2.use-case';
import { UserRepository } from '../../../domain/user/UserRepository';
import { User } from '../../../domain/user/User';

describe('GetUsersV2UseCase', () => {
  let useCase: GetUsersV2UseCase;
  let userRepository: jest.Mocked<UserRepository>;

  beforeEach(async () => {
    const module = await Test.createTestingModule({
      providers: [
        GetUsersV2UseCase,
        {
          provide: UserRepository,
          useValue: { findAll: jest.fn() },
        },
      ],
    }).compile();

    useCase = module.get(GetUsersV2UseCase);
    userRepository = module.get(UserRepository);
  });

  it('should split name into firstName and lastName', async () => {
    const mockUser = User.reconstitute({
      id: '1',
      name: 'John Doe',
      email: 'john@example.com',
      createdAt: new Date(),
    });

    userRepository.findAll.mockResolvedValue([mockUser]);

    const result = await useCase.execute();

    expect(result[0].firstName).toBe('John');
    expect(result[0].lastName).toBe('Doe');
  });

  it('should handle single name (no lastName)', async () => {
    const mockUser = User.reconstitute({
      id: '1',
      name: 'Madonna',
      email: 'madonna@example.com',
      createdAt: new Date(),
    });

    userRepository.findAll.mockResolvedValue([mockUser]);

    const result = await useCase.execute();

    expect(result[0].firstName).toBe('Madonna');
    expect(result[0].lastName).toBe('');
  });

  it('should handle multiple middle names', async () => {
    const mockUser = User.reconstitute({
      id: '1',
      name: 'John Paul Jones Smith',
      email: 'john@example.com',
      createdAt: new Date(),
    });

    userRepository.findAll.mockResolvedValue([mockUser]);

    const result = await useCase.execute();

    expect(result[0].firstName).toBe('John');
    expect(result[0].lastName).toBe('Paul Jones Smith');
  });
});
```

## 체크리스트

- [ ] V2 DTO 생성 (`user-v2.dto.ts`)
- [ ] V2 UseCase 생성 (`get-users-v2.use-case.ts`)
- [ ] 기존 UseCase에 `@deprecated` 추가
- [ ] 기존 Controller에 `@deprecated` 추가
- [ ] V2 Controller 생성 (`v2/users.controller.ts`)
- [ ] 모듈에 V2 컴포넌트 등록
- [ ] Swagger 문서 업데이트 (`deprecated: true`)
- [ ] 마이그레이션 가이드 작성
- [ ] V2 테스트 추가
- [ ] V1/V2 병행 운영 확인

## 핵심 원칙

1. **기존 코드 수정 금지**: V1 로직은 그대로 유지
2. **명확한 Deprecated 표시**: JSDoc + Swagger 모두
3. **충분한 전환 기간**: 최소 3-6개월 병행 운영
4. **마이그레이션 가이드 제공**: 클라이언트 개발자를 위한 문서
5. **테스트 필수**: V2 로직에 대한 단위 테스트
