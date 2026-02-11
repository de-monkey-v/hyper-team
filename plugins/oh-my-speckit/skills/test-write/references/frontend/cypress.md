# Cypress E2E 테스트 가이드

Cypress를 사용한 프론트엔드 E2E 테스트 작성 가이드.

## 설정

### 초기 설정

```bash
# 설치
npm install -D cypress

# Cypress 열기 (초기 설정 자동 생성)
npx cypress open
```

### cypress.config.ts

```typescript
import { defineConfig } from 'cypress';

export default defineConfig({
  e2e: {
    baseUrl: 'http://localhost:3000',
    viewportWidth: 1280,
    viewportHeight: 720,
    video: false,
    screenshotOnRunFailure: true,
    defaultCommandTimeout: 10000,

    setupNodeEvents(on, config) {
      // 플러그인 설정
    },
  },
});
```

### 프로젝트 구조

```
cypress/
├── e2e/
│   ├── auth/
│   │   ├── login.cy.ts
│   │   └── register.cy.ts
│   └── dashboard/
│       └── dashboard.cy.ts
├── fixtures/
│   └── users.json
├── support/
│   ├── commands.ts
│   └── e2e.ts
└── cypress.config.ts
```

## 기본 문법

### 테스트 구조

```typescript
describe('인증 기능', () => {
  beforeEach(() => {
    cy.visit('/login');
  });

  it('유효한 자격증명으로 로그인하면 대시보드로 이동한다', () => {
    // Arrange - beforeEach에서 완료

    // Act
    cy.get('[data-testid="email"]').type('user@example.com');
    cy.get('[data-testid="password"]').type('password123');
    cy.get('[data-testid="login-button"]').click();

    // Assert
    cy.url().should('include', '/dashboard');
    cy.get('[data-testid="welcome-message"]').should('contain', '환영합니다');
  });

  it('잘못된 비밀번호로 로그인하면 에러를 표시한다', () => {
    cy.get('[data-testid="email"]').type('user@example.com');
    cy.get('[data-testid="password"]').type('wrongpassword');
    cy.get('[data-testid="login-button"]').click();

    cy.get('[data-testid="error-message"]')
      .should('be.visible')
      .and('contain', '비밀번호가 일치하지 않습니다');
  });
});
```

### 선택자

```typescript
// data-testid (권장)
cy.get('[data-testid="submit-button"]');

// CSS 선택자
cy.get('.submit-btn');
cy.get('#email-input');

// 텍스트 기반
cy.contains('제출');
cy.contains('button', '제출');

// 복합 선택자
cy.get('form').find('[data-testid="email"]');
cy.get('ul').children('li').first();
```

### 액션

```typescript
// 클릭
cy.get('button').click();
cy.get('button').dblclick();
cy.get('button').rightclick();

// 입력
cy.get('input').type('Hello');
cy.get('input').type('{enter}');  // 특수 키
cy.get('input').clear().type('New text');

// 선택
cy.get('select').select('option-value');
cy.get('select').select(['option1', 'option2']);  // 다중 선택

// 체크박스/라디오
cy.get('input[type="checkbox"]').check();
cy.get('input[type="checkbox"]').uncheck();

// 파일 업로드
cy.get('input[type="file"]').selectFile('cypress/fixtures/file.pdf');

// 스크롤
cy.get('.element').scrollIntoView();
cy.scrollTo('bottom');

// 포커스/블러
cy.get('input').focus();
cy.get('input').blur();
```

### 단언 (Assertions)

```typescript
// Should 체이닝
cy.get('.element').should('be.visible');
cy.get('.element').should('not.exist');
cy.get('.element').should('have.text', 'Hello');
cy.get('.element').should('contain', 'Hello');
cy.get('.element').should('have.class', 'active');
cy.get('.element').should('have.attr', 'href', '/home');
cy.get('.element').should('have.css', 'color', 'rgb(255, 0, 0)');

// 복합 단언
cy.get('input')
  .should('be.visible')
  .and('have.value', '')
  .and('have.attr', 'placeholder', '이메일 입력');

// URL
cy.url().should('include', '/dashboard');
cy.url().should('eq', 'http://localhost:3000/dashboard');

// 개수
cy.get('li').should('have.length', 5);
cy.get('li').should('have.length.greaterThan', 3);
```

## 커스텀 명령어

### commands.ts

```typescript
// cypress/support/commands.ts

declare global {
  namespace Cypress {
    interface Chainable {
      login(email: string, password: string): Chainable<void>;
      logout(): Chainable<void>;
      getByTestId(testId: string): Chainable<JQuery<HTMLElement>>;
    }
  }
}

// 로그인 명령어
Cypress.Commands.add('login', (email: string, password: string) => {
  cy.visit('/login');
  cy.get('[data-testid="email"]').type(email);
  cy.get('[data-testid="password"]').type(password);
  cy.get('[data-testid="login-button"]').click();
  cy.url().should('include', '/dashboard');
});

// 로그아웃 명령어
Cypress.Commands.add('logout', () => {
  cy.get('[data-testid="user-menu"]').click();
  cy.get('[data-testid="logout-button"]').click();
});

// data-testid 단축 명령어
Cypress.Commands.add('getByTestId', (testId: string) => {
  return cy.get(`[data-testid="${testId}"]`);
});

export {};
```

### 사용 예시

```typescript
describe('대시보드', () => {
  beforeEach(() => {
    cy.login('user@example.com', 'password123');
  });

  it('프로필 페이지에 접근할 수 있다', () => {
    cy.getByTestId('profile-link').click();
    cy.url().should('include', '/profile');
  });
});
```

## API Mocking (Intercept)

### 응답 모킹

```typescript
it('API 응답을 모킹한다', () => {
  // 응답 가로채기
  cy.intercept('GET', '/api/users', {
    statusCode: 200,
    body: [
      { id: 1, name: '홍길동' },
      { id: 2, name: '김철수' },
    ],
  }).as('getUsers');

  cy.visit('/users');

  // 요청 대기
  cy.wait('@getUsers');

  cy.get('.user-item').should('have.length', 2);
});
```

### 에러 시뮬레이션

```typescript
it('API 에러 시 에러 메시지를 표시한다', () => {
  cy.intercept('GET', '/api/users', {
    statusCode: 500,
    body: { error: 'Server Error' },
  }).as('getUsersError');

  cy.visit('/users');
  cy.wait('@getUsersError');

  cy.get('[data-testid="error-message"]').should('contain', '오류가 발생했습니다');
});
```

### Fixture 사용

```typescript
// cypress/fixtures/users.json
[
  { "id": 1, "name": "홍길동", "email": "hong@example.com" },
  { "id": 2, "name": "김철수", "email": "kim@example.com" }
]

// 테스트에서 사용
it('fixture 데이터로 응답한다', () => {
  cy.intercept('GET', '/api/users', { fixture: 'users.json' }).as('getUsers');

  cy.visit('/users');
  cy.wait('@getUsers');

  cy.get('.user-item').should('have.length', 2);
});
```

### 요청 검증

```typescript
it('올바른 요청 데이터를 전송한다', () => {
  cy.intercept('POST', '/api/users', (req) => {
    // 요청 검증
    expect(req.body).to.have.property('email', 'new@example.com');

    req.reply({
      statusCode: 201,
      body: { id: 3, ...req.body },
    });
  }).as('createUser');

  cy.visit('/users/new');
  cy.get('[data-testid="email"]').type('new@example.com');
  cy.get('[data-testid="submit"]').click();

  cy.wait('@createUser').its('request.body').should('deep.equal', {
    email: 'new@example.com',
  });
});
```

## 세션 관리

### cy.session()

```typescript
// 세션 캐싱으로 로그인 속도 향상
Cypress.Commands.add('login', (email: string, password: string) => {
  cy.session([email, password], () => {
    cy.visit('/login');
    cy.get('[data-testid="email"]').type(email);
    cy.get('[data-testid="password"]').type(password);
    cy.get('[data-testid="login-button"]').click();
    cy.url().should('include', '/dashboard');
  });
});

// 테스트에서 사용 - 세션이 캐시됨
describe('여러 테스트', () => {
  beforeEach(() => {
    cy.login('user@example.com', 'password');  // 첫 번째만 실제 로그인
    cy.visit('/dashboard');
  });

  it('테스트 1', () => { /* ... */ });
  it('테스트 2', () => { /* ... */ });  // 캐시된 세션 사용
});
```

## 디버깅

### 타임트래블

Cypress UI에서 각 명령어 클릭 시 해당 시점의 DOM 스냅샷 확인 가능

### cy.pause()

```typescript
it('디버깅', () => {
  cy.visit('/login');
  cy.pause();  // 여기서 일시 정지
  cy.get('[data-testid="email"]').type('user@example.com');
});
```

### cy.debug()

```typescript
it('디버깅', () => {
  cy.get('[data-testid="email"]')
    .debug()  // 콘솔에 요소 정보 출력
    .type('user@example.com');
});
```

### 스크린샷

```typescript
it('스크린샷 저장', () => {
  cy.visit('/dashboard');
  cy.screenshot('dashboard-loaded');  // 수동 스크린샷
});
```

## CI/CD 설정

### GitHub Actions

```yaml
name: E2E Tests

on: [push, pull_request]

jobs:
  cypress:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'

      - name: Install dependencies
        run: npm ci

      - name: Cypress run
        uses: cypress-io/github-action@v6
        with:
          build: npm run build
          start: npm start
          wait-on: 'http://localhost:3000'

      - uses: actions/upload-artifact@v4
        if: failure()
        with:
          name: cypress-screenshots
          path: cypress/screenshots
```

### 병렬 실행

```yaml
jobs:
  cypress:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        containers: [1, 2, 3]  # 3개 컨테이너로 분할
    steps:
      - uses: cypress-io/github-action@v6
        with:
          record: true
          parallel: true
          group: 'E2E Tests'
        env:
          CYPRESS_RECORD_KEY: ${{ secrets.CYPRESS_RECORD_KEY }}
```

## Playwright vs Cypress 비교

| 항목 | Cypress | Playwright |
|------|---------|------------|
| 브라우저 | Chrome, Firefox, Edge | Chrome, Firefox, Safari, Edge |
| 언어 | JavaScript/TypeScript | JavaScript/TypeScript, Python, Java, C# |
| 실행 방식 | 브라우저 내 실행 | Node.js에서 브라우저 제어 |
| 디버깅 | 타임트래블 UI | 트레이스 뷰어 |
| API 테스트 | cy.request() | request fixture |
| 다중 탭 | 제한적 | 완전 지원 |
| 커뮤니티 | 매우 큼 | 빠르게 성장 중 |

## 체크리스트

- [ ] `data-testid` 선택자 사용
- [ ] Custom Commands로 반복 코드 제거
- [ ] cy.intercept()로 API 모킹
- [ ] cy.session()으로 로그인 캐싱
- [ ] Fixtures로 테스트 데이터 관리
- [ ] CI/CD 파이프라인 구성
- [ ] 실패 시 스크린샷 저장 설정
