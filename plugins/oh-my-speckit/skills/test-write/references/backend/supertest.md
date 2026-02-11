# Supertest + Node.js 통합 테스트 가이드

Supertest를 사용한 Node.js/Express/NestJS API 통합 테스트 작성 가이드.

## 설정

### 의존성 설치

```bash
# Express
npm install -D supertest @types/supertest jest @types/jest ts-jest

# NestJS (기본 포함)
npm install -D @nestjs/testing supertest @types/supertest
```

### Jest 설정

```javascript
// jest.config.js
module.exports = {
  preset: 'ts-jest',
  testEnvironment: 'node',
  testMatch: ['**/*.test.ts', '**/*.spec.ts'],
  moduleFileExtensions: ['ts', 'js', 'json'],
  rootDir: 'src',
  coverageDirectory: '../coverage',
};
```

### 프로젝트 구조

```
src/
├── app.ts                    # Express 앱
├── routes/
│   └── users.ts
└── __tests__/
    ├── integration/
    │   ├── users.test.ts     # API 통합 테스트
    │   └── auth.test.ts
    └── setup.ts              # 테스트 설정
```

## Express 통합 테스트

### 기본 구조

```typescript
// src/__tests__/integration/users.test.ts
import request from 'supertest';
import app from '../../app';
import { prisma } from '../../lib/prisma';

describe('Users API', () => {
  beforeAll(async () => {
    // DB 연결 등 초기화
  });

  afterAll(async () => {
    await prisma.$disconnect();
  });

  beforeEach(async () => {
    // 각 테스트 전 데이터 초기화
    await prisma.user.deleteMany();
  });

  describe('POST /api/users', () => {
    it('유효한 데이터로 사용자를 생성하면 201을 반환한다', async () => {
      // Arrange
      const userData = {
        name: '홍길동',
        email: 'hong@example.com',
        password: 'SecurePass123!',
      };

      // Act
      const response = await request(app)
        .post('/api/users')
        .send(userData)
        .expect('Content-Type', /json/);

      // Assert
      expect(response.status).toBe(201);
      expect(response.body).toHaveProperty('id');
      expect(response.body.name).toBe('홍길동');
      expect(response.body.email).toBe('hong@example.com');
      expect(response.body).not.toHaveProperty('password');
    });

    it('이메일 형식이 잘못되면 400을 반환한다', async () => {
      const response = await request(app)
        .post('/api/users')
        .send({
          name: '홍길동',
          email: 'invalid-email',
          password: 'SecurePass123!',
        });

      expect(response.status).toBe(400);
      expect(response.body.errors).toHaveProperty('email');
    });

    it('중복 이메일이면 409를 반환한다', async () => {
      // Arrange - 기존 사용자 생성
      await prisma.user.create({
        data: { name: '기존', email: 'existing@example.com', password: 'hash' },
      });

      // Act
      const response = await request(app)
        .post('/api/users')
        .send({
          name: '새사용자',
          email: 'existing@example.com',
          password: 'SecurePass123!',
        });

      // Assert
      expect(response.status).toBe(409);
    });
  });

  describe('GET /api/users/:id', () => {
    it('존재하는 사용자를 조회하면 200을 반환한다', async () => {
      // Arrange
      const user = await prisma.user.create({
        data: { name: '홍길동', email: 'hong@example.com', password: 'hash' },
      });

      // Act & Assert
      const response = await request(app)
        .get(`/api/users/${user.id}`)
        .expect(200);

      expect(response.body.id).toBe(user.id);
      expect(response.body.name).toBe('홍길동');
    });

    it('존재하지 않는 사용자를 조회하면 404를 반환한다', async () => {
      await request(app)
        .get('/api/users/nonexistent-id')
        .expect(404);
    });
  });
});
```

### 인증 테스트

```typescript
// src/__tests__/integration/auth.test.ts
import request from 'supertest';
import app from '../../app';
import { prisma } from '../../lib/prisma';
import bcrypt from 'bcrypt';

describe('Auth API', () => {
  beforeEach(async () => {
    await prisma.user.deleteMany();

    // 테스트 사용자 생성
    const hashedPassword = await bcrypt.hash('password123', 10);
    await prisma.user.create({
      data: {
        name: '홍길동',
        email: 'hong@example.com',
        password: hashedPassword,
      },
    });
  });

  describe('POST /api/auth/login', () => {
    it('유효한 자격증명으로 로그인하면 토큰을 반환한다', async () => {
      const response = await request(app)
        .post('/api/auth/login')
        .send({
          email: 'hong@example.com',
          password: 'password123',
        })
        .expect(200);

      expect(response.body).toHaveProperty('token');
      expect(response.body).toHaveProperty('user');
      expect(response.body.user.email).toBe('hong@example.com');
    });

    it('잘못된 비밀번호로 로그인하면 401을 반환한다', async () => {
      const response = await request(app)
        .post('/api/auth/login')
        .send({
          email: 'hong@example.com',
          password: 'wrongpassword',
        })
        .expect(401);

      expect(response.body.message).toBe('비밀번호가 일치하지 않습니다');
    });
  });

  describe('인증이 필요한 엔드포인트', () => {
    let authToken: string;

    beforeEach(async () => {
      // 로그인하여 토큰 획득
      const response = await request(app)
        .post('/api/auth/login')
        .send({ email: 'hong@example.com', password: 'password123' });

      authToken = response.body.token;
    });

    it('토큰으로 보호된 리소스에 접근할 수 있다', async () => {
      await request(app)
        .get('/api/users/me')
        .set('Authorization', `Bearer ${authToken}`)
        .expect(200);
    });

    it('토큰 없이 접근하면 401을 반환한다', async () => {
      await request(app)
        .get('/api/users/me')
        .expect(401);
    });
  });
});
```

## NestJS 통합 테스트

### 기본 구조

```typescript
// src/users/__tests__/users.integration.spec.ts
import { Test, TestingModule } from '@nestjs/testing';
import { INestApplication, ValidationPipe } from '@nestjs/common';
import * as request from 'supertest';
import { AppModule } from '../../app.module';
import { PrismaService } from '../../prisma/prisma.service';

describe('UsersController (Integration)', () => {
  let app: INestApplication;
  let prisma: PrismaService;

  beforeAll(async () => {
    const moduleFixture: TestingModule = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();

    app = moduleFixture.createNestApplication();
    app.useGlobalPipes(new ValidationPipe());
    await app.init();

    prisma = moduleFixture.get<PrismaService>(PrismaService);
  });

  afterAll(async () => {
    await app.close();
  });

  beforeEach(async () => {
    await prisma.user.deleteMany();
  });

  describe('POST /users', () => {
    it('사용자를 생성한다', () => {
      return request(app.getHttpServer())
        .post('/users')
        .send({
          name: '홍길동',
          email: 'hong@example.com',
          password: 'SecurePass123!',
        })
        .expect(201)
        .expect((res) => {
          expect(res.body.id).toBeDefined();
          expect(res.body.name).toBe('홍길동');
        });
    });
  });

  describe('GET /users/:id', () => {
    it('사용자를 조회한다', async () => {
      const user = await prisma.user.create({
        data: { name: '홍길동', email: 'hong@example.com', password: 'hash' },
      });

      return request(app.getHttpServer())
        .get(`/users/${user.id}`)
        .expect(200)
        .expect((res) => {
          expect(res.body.name).toBe('홍길동');
        });
    });
  });
});
```

### 모킹을 사용한 테스트

```typescript
// 외부 서비스 모킹
import { Test, TestingModule } from '@nestjs/testing';
import { INestApplication } from '@nestjs/common';
import * as request from 'supertest';
import { AppModule } from '../../app.module';
import { EmailService } from '../../email/email.service';

describe('Registration with Email', () => {
  let app: INestApplication;
  let emailService: EmailService;

  beforeAll(async () => {
    const moduleFixture: TestingModule = await Test.createTestingModule({
      imports: [AppModule],
    })
      .overrideProvider(EmailService)
      .useValue({
        sendWelcomeEmail: jest.fn().mockResolvedValue(true),
      })
      .compile();

    app = moduleFixture.createNestApplication();
    await app.init();

    emailService = moduleFixture.get<EmailService>(EmailService);
  });

  it('회원가입 시 환영 이메일을 발송한다', async () => {
    await request(app.getHttpServer())
      .post('/users')
      .send({
        name: '홍길동',
        email: 'hong@example.com',
        password: 'SecurePass123!',
      })
      .expect(201);

    expect(emailService.sendWelcomeEmail).toHaveBeenCalledWith('hong@example.com');
  });
});
```

## 테스트 유틸리티

### 테스트 헬퍼

```typescript
// src/__tests__/helpers/auth.helper.ts
import request from 'supertest';
import app from '../../app';

export async function getAuthToken(
  email: string = 'test@example.com',
  password: string = 'password123'
): Promise<string> {
  const response = await request(app)
    .post('/api/auth/login')
    .send({ email, password });

  return response.body.token;
}

export function authRequest(token: string) {
  return {
    get: (url: string) =>
      request(app).get(url).set('Authorization', `Bearer ${token}`),
    post: (url: string) =>
      request(app).post(url).set('Authorization', `Bearer ${token}`),
    put: (url: string) =>
      request(app).put(url).set('Authorization', `Bearer ${token}`),
    delete: (url: string) =>
      request(app).delete(url).set('Authorization', `Bearer ${token}`),
  };
}

// 사용 예시
describe('Protected Routes', () => {
  let auth: ReturnType<typeof authRequest>;

  beforeAll(async () => {
    const token = await getAuthToken();
    auth = authRequest(token);
  });

  it('프로필을 조회한다', async () => {
    const response = await auth.get('/api/users/me');
    expect(response.status).toBe(200);
  });
});
```

### 테스트 데이터 팩토리

```typescript
// src/__tests__/factories/user.factory.ts
import { prisma } from '../../lib/prisma';
import bcrypt from 'bcrypt';

interface CreateUserOptions {
  name?: string;
  email?: string;
  password?: string;
}

export async function createUser(options: CreateUserOptions = {}) {
  const {
    name = '테스트 사용자',
    email = `test-${Date.now()}@example.com`,
    password = 'password123',
  } = options;

  const hashedPassword = await bcrypt.hash(password, 10);

  return prisma.user.create({
    data: { name, email, password: hashedPassword },
  });
}

export async function createUsers(count: number) {
  const users = [];
  for (let i = 0; i < count; i++) {
    users.push(
      await createUser({
        name: `User ${i + 1}`,
        email: `user${i + 1}@example.com`,
      })
    );
  }
  return users;
}

// 사용 예시
describe('Users List', () => {
  beforeEach(async () => {
    await createUsers(5);
  });

  it('사용자 목록을 반환한다', async () => {
    const response = await request(app).get('/api/users');
    expect(response.body).toHaveLength(5);
  });
});
```

## 데이터베이스 설정

### 테스트용 DB 분리

```bash
# .env.test
DATABASE_URL="postgresql://localhost:5432/myapp_test"
```

```typescript
// src/__tests__/setup.ts
import { prisma } from '../lib/prisma';

beforeAll(async () => {
  // 테스트 DB 연결
  await prisma.$connect();
});

afterAll(async () => {
  await prisma.$disconnect();
});

beforeEach(async () => {
  // 모든 테이블 초기화
  const tables = await prisma.$queryRaw<{ tablename: string }[]>`
    SELECT tablename FROM pg_tables WHERE schemaname = 'public'
  `;

  for (const { tablename } of tables) {
    if (tablename !== '_prisma_migrations') {
      await prisma.$executeRawUnsafe(`TRUNCATE TABLE "${tablename}" CASCADE`);
    }
  }
});
```

### Docker Compose (테스트 DB)

```yaml
# docker-compose.test.yml
version: '3.8'
services:
  test-db:
    image: postgres:15
    environment:
      POSTGRES_USER: test
      POSTGRES_PASSWORD: test
      POSTGRES_DB: myapp_test
    ports:
      - '5433:5432'
```

## 실행 명령어

```bash
# 전체 테스트
npm test

# 통합 테스트만
npm test -- --testPathPattern=integration

# 특정 파일
npm test -- users.test.ts

# 커버리지
npm test -- --coverage

# watch 모드
npm test -- --watch
```

## CI/CD 설정

### GitHub Actions

```yaml
name: API Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest

    services:
      postgres:
        image: postgres:15
        env:
          POSTGRES_USER: test
          POSTGRES_PASSWORD: test
          POSTGRES_DB: test
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

    steps:
      - uses: actions/checkout@v4

      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'

      - name: Install dependencies
        run: npm ci

      - name: Run migrations
        run: npx prisma migrate deploy
        env:
          DATABASE_URL: postgresql://test:test@localhost:5432/test

      - name: Run tests
        run: npm test
        env:
          DATABASE_URL: postgresql://test:test@localhost:5432/test
```

## 테스트 로깅

테스트 실행 시 디버그 및 성능 분석을 위한 로깅 가이드.

### Logger 설정

```typescript
// tests/utils/test-logger.ts
import winston from 'winston';

export const testLogger = winston.createLogger({
  level: process.env.TEST_LOG_LEVEL || 'info',
  format: winston.format.combine(
    winston.format.timestamp(),
    winston.format.printf(({ timestamp, level, message, ...meta }) =>
      `[${timestamp}] [TEST] ${level.toUpperCase()}: ${message} ${
        Object.keys(meta).length ? JSON.stringify(meta) : ''
      }`
    )
  ),
  transports: [new winston.transports.Console()],
});
```

### 테스트 라이프사이클 로깅

```typescript
describe('User API', () => {
  beforeAll(() => {
    testLogger.info('=== Test Suite Started: User API ===');
  });

  afterAll(() => {
    testLogger.info('=== Test Suite Completed: User API ===');
  });

  beforeEach(() => {
    testLogger.debug(`Starting test: ${expect.getState().currentTestName}`);
  });

  afterEach(() => {
    testLogger.debug(`Completed test: ${expect.getState().currentTestName}`);
  });
});
```

### 디버그 정보 로깅

```typescript
it('should create user', async () => {
  const userData = { email: 'test@example.com', name: 'Test' };
  testLogger.debug('Request payload', { userData });

  const response = await request(app).post('/api/users').send(userData);

  testLogger.debug('Response received', {
    status: response.status,
    body: response.body,
  });

  expect(response.status).toBe(201);
});
```

### 성능 측정 로깅

```typescript
it('should respond within acceptable time', async () => {
  const startTime = Date.now();

  const response = await request(app).get('/api/users');

  const duration = Date.now() - startTime;
  testLogger.info(`API response time: ${duration}ms`, { endpoint: '/api/users' });

  expect(duration).toBeLessThan(500);
});
```

### 로그 레벨 설정

```bash
# 환경 변수로 로그 레벨 조정
TEST_LOG_LEVEL=debug npm test    # 상세 로그 출력
TEST_LOG_LEVEL=error npm test    # 에러만 출력
```

## 체크리스트

- [ ] 테스트 DB 분리 (DATABASE_URL)
- [ ] beforeEach에서 데이터 초기화
- [ ] 인증 헬퍼 함수 작성
- [ ] 테스트 데이터 팩토리 패턴
- [ ] 외부 서비스 모킹
- [ ] CI/CD 파이프라인 구성
- [ ] 커버리지 리포트 설정
