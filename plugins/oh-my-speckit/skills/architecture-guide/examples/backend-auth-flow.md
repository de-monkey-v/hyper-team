# 실전 예시: 로그인 기능 (Clean Architecture)

## 전체 흐름

```
[HTTP Request]
    ↓
[Controller] → LoginDto 검증
    ↓
[UseCase] → 비즈니스 로직
    ↓
[Repository Interface] ← (Port)
    ↓
[Repository Implementation] → DB 조회
    ↓
[Domain Entity] → 비밀번호 검증
    ↓
[UseCase] → JWT 생성
    ↓
[Controller] → Response 반환
```

## 디렉토리 구조

```
src/
├── domain/
│   └── user/
│       ├── User.ts              # 엔티티
│       └── UserRepository.ts    # 포트 (인터페이스)
├── application/
│   └── auth/
│       ├── LoginUseCase.ts      # 유스케이스
│       ├── dto/
│       │   ├── LoginDto.ts      # 입력 DTO
│       │   └── TokenDto.ts      # 출력 DTO
│       └── ports/
│           └── PasswordHasher.ts # 포트
├── infrastructure/
│   ├── repositories/
│   │   └── PrismaUserRepository.ts
│   └── services/
│       └── BcryptPasswordHasher.ts
└── interfaces/
    └── http/
        └── controllers/
            └── AuthController.ts
```

## 1. Domain Layer

### User.ts (엔티티)

```typescript
// src/domain/user/User.ts
export class User {
  private constructor(
    public readonly id: string,
    public readonly email: string,
    public readonly passwordHash: string,
    public readonly createdAt: Date,
  ) {}

  static create(props: { email: string; passwordHash: string }): User {
    return new User(
      crypto.randomUUID(),
      props.email,
      props.passwordHash,
      new Date(),
    );
  }

  static reconstitute(props: {
    id: string;
    email: string;
    passwordHash: string;
    createdAt: Date;
  }): User {
    return new User(props.id, props.email, props.passwordHash, props.createdAt);
  }
}
```

### UserRepository.ts (포트)

```typescript
// src/domain/user/UserRepository.ts
import { User } from './User';

export interface UserRepository {
  findByEmail(email: string): Promise<User | null>;
  findById(id: string): Promise<User | null>;
  save(user: User): Promise<User>;
  existsByEmail(email: string): Promise<boolean>;
}
```

## 2. Application Layer

### LoginUseCase.ts

```typescript
// src/application/auth/LoginUseCase.ts
import { Injectable, UnauthorizedException } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { UserRepository } from '../../domain/user/UserRepository';
import { PasswordHasher } from './ports/PasswordHasher';
import { LoginDto } from './dto/LoginDto';
import { TokenDto } from './dto/TokenDto';

@Injectable()
export class LoginUseCase {
  constructor(
    private readonly userRepository: UserRepository,
    private readonly passwordHasher: PasswordHasher,
    private readonly jwtService: JwtService,
  ) {}

  async execute(dto: LoginDto): Promise<TokenDto> {
    // 1. 사용자 조회
    const user = await this.userRepository.findByEmail(dto.email);
    if (!user) {
      throw new UnauthorizedException('Invalid credentials');
    }

    // 2. 비밀번호 검증
    const isValid = await this.passwordHasher.verify(
      dto.password,
      user.passwordHash,
    );
    if (!isValid) {
      throw new UnauthorizedException('Invalid credentials');
    }

    // 3. 토큰 생성
    const accessToken = this.jwtService.sign(
      { sub: user.id, email: user.email },
      { expiresIn: '15m' },
    );
    const refreshToken = this.jwtService.sign(
      { sub: user.id, type: 'refresh' },
      { expiresIn: '7d' },
    );

    return { accessToken, refreshToken };
  }
}
```

### DTO 정의

```typescript
// src/application/auth/dto/LoginDto.ts
import { IsEmail, IsString, MinLength } from 'class-validator';

export class LoginDto {
  @IsEmail()
  email: string;

  @IsString()
  @MinLength(8)
  password: string;
}

// src/application/auth/dto/TokenDto.ts
export class TokenDto {
  accessToken: string;
  refreshToken: string;
}
```

### PasswordHasher (포트)

```typescript
// src/application/auth/ports/PasswordHasher.ts
export interface PasswordHasher {
  hash(password: string): Promise<string>;
  verify(password: string, hash: string): Promise<boolean>;
}
```

## 3. Infrastructure Layer

### PrismaUserRepository.ts

```typescript
// src/infrastructure/repositories/PrismaUserRepository.ts
import { Injectable } from '@nestjs/common';
import { PrismaService } from '../database/PrismaService';
import { User } from '../../domain/user/User';
import { UserRepository } from '../../domain/user/UserRepository';

@Injectable()
export class PrismaUserRepository implements UserRepository {
  constructor(private readonly prisma: PrismaService) {}

  async findByEmail(email: string): Promise<User | null> {
    const data = await this.prisma.user.findUnique({ where: { email } });
    return data ? User.reconstitute(data) : null;
  }

  async findById(id: string): Promise<User | null> {
    const data = await this.prisma.user.findUnique({ where: { id } });
    return data ? User.reconstitute(data) : null;
  }

  async save(user: User): Promise<User> {
    const data = await this.prisma.user.upsert({
      where: { id: user.id },
      update: { email: user.email, passwordHash: user.passwordHash },
      create: {
        id: user.id,
        email: user.email,
        passwordHash: user.passwordHash,
        createdAt: user.createdAt,
      },
    });
    return User.reconstitute(data);
  }

  async existsByEmail(email: string): Promise<boolean> {
    const count = await this.prisma.user.count({ where: { email } });
    return count > 0;
  }
}
```

### BcryptPasswordHasher.ts

```typescript
// src/infrastructure/services/BcryptPasswordHasher.ts
import { Injectable } from '@nestjs/common';
import * as bcrypt from 'bcrypt';
import { PasswordHasher } from '../../application/auth/ports/PasswordHasher';

@Injectable()
export class BcryptPasswordHasher implements PasswordHasher {
  private readonly saltRounds = 10;

  async hash(password: string): Promise<string> {
    return bcrypt.hash(password, this.saltRounds);
  }

  async verify(password: string, hash: string): Promise<boolean> {
    return bcrypt.compare(password, hash);
  }
}
```

## 4. Interface Layer

### AuthController.ts

```typescript
// src/interfaces/http/controllers/AuthController.ts
import { Controller, Post, Body, HttpCode, HttpStatus } from '@nestjs/common';
import { ApiTags, ApiOperation, ApiResponse } from '@nestjs/swagger';
import { LoginUseCase } from '../../../application/auth/LoginUseCase';
import { LoginDto } from '../../../application/auth/dto/LoginDto';
import { TokenDto } from '../../../application/auth/dto/TokenDto';

@ApiTags('auth')
@Controller('auth')
export class AuthController {
  constructor(private readonly loginUseCase: LoginUseCase) {}

  @Post('login')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: '로그인' })
  @ApiResponse({ status: 200, type: TokenDto })
  @ApiResponse({ status: 401, description: 'Invalid credentials' })
  async login(@Body() dto: LoginDto): Promise<TokenDto> {
    return this.loginUseCase.execute(dto);
  }
}
```

## 5. 모듈 구성 (DI)

```typescript
// src/modules/auth.module.ts
import { Module } from '@nestjs/common';
import { JwtModule } from '@nestjs/jwt';
import { ConfigService } from '@nestjs/config';
import { AuthController } from '../interfaces/http/controllers/AuthController';
import { LoginUseCase } from '../application/auth/LoginUseCase';
import { PrismaUserRepository } from '../infrastructure/repositories/PrismaUserRepository';
import { BcryptPasswordHasher } from '../infrastructure/services/BcryptPasswordHasher';
import { UserRepository } from '../domain/user/UserRepository';
import { PasswordHasher } from '../application/auth/ports/PasswordHasher';
import { DatabaseModule } from './database.module';

@Module({
  imports: [
    DatabaseModule,
    JwtModule.registerAsync({
      inject: [ConfigService],
      useFactory: (config: ConfigService) => ({
        secret: config.get('JWT_SECRET'),
        signOptions: { expiresIn: '15m' },
      }),
    }),
  ],
  controllers: [AuthController],
  providers: [
    LoginUseCase,
    { provide: UserRepository, useClass: PrismaUserRepository },
    { provide: PasswordHasher, useClass: BcryptPasswordHasher },
  ],
  exports: [LoginUseCase],
})
export class AuthModule {}
```

## 핵심 포인트

| 레이어 | 역할 | 의존성 |
|--------|------|--------|
| **Domain** | 순수 비즈니스 엔티티 | 없음 (순수) |
| **Application** | 유스케이스 오케스트레이션 | Domain만 |
| **Infrastructure** | 외부 시스템 구현체 | Domain, Application |
| **Interface** | HTTP/이벤트 진입점 | Application |

**의존성 방향**: Interface → Application → Domain ← Infrastructure
