# Playwright E2E 테스트 가이드

Playwright를 사용한 프론트엔드 E2E 테스트 작성 가이드.

## 설정

### 초기 설정

```bash
# 설치
npm init playwright@latest

# 또는 기존 프로젝트에 추가
npm install -D @playwright/test
npx playwright install
```

### playwright.config.ts

```typescript
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: 'html',

  use: {
    baseURL: 'http://localhost:3000',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },
    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
    },
    // 모바일 테스트
    {
      name: 'Mobile Chrome',
      use: { ...devices['Pixel 5'] },
    },
  ],

  // 개발 서버 자동 시작
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:3000',
    reuseExistingServer: !process.env.CI,
  },
});
```

## 기본 문법

### 테스트 구조

```typescript
import { test, expect } from '@playwright/test';

test.describe('인증 기능', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
  });

  test('유효한 자격증명으로 로그인하면 대시보드로 이동한다', async ({ page }) => {
    await page.fill('[data-testid="email"]', 'user@example.com');
    await page.fill('[data-testid="password"]', 'password123');
    await page.click('[data-testid="login-button"]');

    await expect(page).toHaveURL('/dashboard');
  });

  test('잘못된 비밀번호로 로그인하면 에러를 표시한다', async ({ page }) => {
    await page.fill('[data-testid="email"]', 'user@example.com');
    await page.fill('[data-testid="password"]', 'wrong');
    await page.click('[data-testid="login-button"]');

    await expect(page.locator('[data-testid="error"]')).toBeVisible();
  });
});
```

### Locator API

```typescript
// 권장 선택자 (우선순위 순)
page.getByTestId('submit');           // data-testid
page.getByRole('button', { name: '제출' }); // 접근성 역할
page.getByText('제출');                // 텍스트
page.getByLabel('이메일');             // label
page.getByPlaceholder('이메일 입력');   // placeholder

// CSS/XPath (피하기)
page.locator('.submit-btn');
page.locator('//button[@type="submit"]');
```

### 액션

```typescript
// 클릭
await page.click('[data-testid="button"]');
await page.getByRole('button').click();

// 입력
await page.fill('[data-testid="input"]', 'text');
await page.getByLabel('이름').fill('홍길동');

// 선택
await page.selectOption('select', 'value');
await page.getByLabel('국가').selectOption({ label: '한국' });

// 체크박스
await page.check('[data-testid="checkbox"]');
await page.getByRole('checkbox').check();

// 파일 업로드
await page.setInputFiles('input[type="file"]', 'path/to/file.pdf');

// 호버
await page.hover('[data-testid="menu"]');

// 키보드
await page.keyboard.press('Enter');
await page.keyboard.type('Hello');
```

### 단언 (Assertions)

```typescript
// 가시성
await expect(page.locator('.element')).toBeVisible();
await expect(page.locator('.element')).toBeHidden();

// 텍스트
await expect(page.locator('h1')).toHaveText('Welcome');
await expect(page.locator('p')).toContainText('hello');

// URL
await expect(page).toHaveURL('/dashboard');
await expect(page).toHaveURL(/.*dashboard/);

// 속성
await expect(page.locator('input')).toHaveAttribute('type', 'email');
await expect(page.locator('button')).toBeEnabled();
await expect(page.locator('button')).toBeDisabled();

// 개수
await expect(page.locator('li')).toHaveCount(5);

// 스크린샷
await expect(page).toHaveScreenshot('homepage.png');
```

## 고급 패턴

### Page Object Model

```typescript
// pages/LoginPage.ts
export class LoginPage {
  constructor(private page: Page) {}

  async goto() {
    await this.page.goto('/login');
  }

  async login(email: string, password: string) {
    await this.page.fill('[data-testid="email"]', email);
    await this.page.fill('[data-testid="password"]', password);
    await this.page.click('[data-testid="login-button"]');
  }

  async getErrorMessage() {
    return this.page.locator('[data-testid="error"]').textContent();
  }
}

// 테스트에서 사용
test('로그인 테스트', async ({ page }) => {
  const loginPage = new LoginPage(page);
  await loginPage.goto();
  await loginPage.login('user@example.com', 'password');
  await expect(page).toHaveURL('/dashboard');
});
```

### Fixtures

```typescript
// fixtures/auth.ts
import { test as base } from '@playwright/test';

type AuthFixtures = {
  authenticatedPage: Page;
};

export const test = base.extend<AuthFixtures>({
  authenticatedPage: async ({ page }, use) => {
    // 로그인 수행
    await page.goto('/login');
    await page.fill('[data-testid="email"]', 'user@example.com');
    await page.fill('[data-testid="password"]', 'password');
    await page.click('[data-testid="login-button"]');
    await page.waitForURL('/dashboard');

    // 인증된 페이지 제공
    await use(page);
  },
});

// 테스트에서 사용
import { test } from './fixtures/auth';

test('인증된 사용자만 프로필 접근 가능', async ({ authenticatedPage }) => {
  await authenticatedPage.goto('/profile');
  await expect(authenticatedPage.locator('h1')).toHaveText('My Profile');
});
```

### API Mocking

```typescript
test('외부 API 실패 시 에러 표시', async ({ page }) => {
  // API 응답 모킹
  await page.route('**/api/users', route => {
    route.fulfill({
      status: 500,
      body: JSON.stringify({ error: 'Server Error' }),
    });
  });

  await page.goto('/users');
  await expect(page.locator('[data-testid="error"]')).toContainText('오류');
});

test('특정 데이터로 응답 모킹', async ({ page }) => {
  await page.route('**/api/products', route => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        { id: 1, name: 'Product A', price: 100 },
        { id: 2, name: 'Product B', price: 200 },
      ]),
    });
  });

  await page.goto('/products');
  await expect(page.locator('.product-item')).toHaveCount(2);
});
```

### 네트워크 대기

```typescript
// API 응답 대기
const responsePromise = page.waitForResponse('**/api/data');
await page.click('[data-testid="load-button"]');
const response = await responsePromise;
expect(response.status()).toBe(200);

// 네트워크 idle 대기
await page.waitForLoadState('networkidle');
```

### 다중 탭/창

```typescript
test('새 탭에서 링크 열기', async ({ page, context }) => {
  const [newPage] = await Promise.all([
    context.waitForEvent('page'),
    page.click('a[target="_blank"]'),
  ]);

  await newPage.waitForLoadState();
  await expect(newPage).toHaveURL(/external-site/);
});
```

### 파일 다운로드

```typescript
test('파일 다운로드', async ({ page }) => {
  const [download] = await Promise.all([
    page.waitForEvent('download'),
    page.click('[data-testid="download-button"]'),
  ]);

  const path = await download.path();
  expect(path).toBeTruthy();
});
```

## 디버깅

### UI 모드

```bash
npx playwright test --ui
```

### 디버그 모드

```bash
npx playwright test --debug
```

### 트레이스 뷰어

```bash
npx playwright show-trace trace.zip
```

### 코드젠

```bash
# 브라우저에서 동작을 녹화하여 코드 생성
npx playwright codegen http://localhost:3000
```

## CI/CD 설정

### GitHub Actions

```yaml
name: E2E Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'

      - name: Install dependencies
        run: npm ci

      - name: Install Playwright Browsers
        run: npx playwright install --with-deps

      - name: Run E2E tests
        run: npx playwright test

      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: playwright-report
          path: playwright-report/
          retention-days: 30
```

## 프로젝트 구조 예시

```
tests/
├── e2e/
│   ├── auth/
│   │   ├── login.spec.ts
│   │   └── register.spec.ts
│   ├── dashboard/
│   │   └── dashboard.spec.ts
│   ├── fixtures/
│   │   ├── auth.ts
│   │   └── test-data.json
│   └── pages/
│       ├── LoginPage.ts
│       └── DashboardPage.ts
├── playwright.config.ts
└── package.json
```

## 체크리스트

- [ ] `data-testid` 선택자 사용
- [ ] Page Object Model 적용 (복잡한 경우)
- [ ] 하드코딩된 대기 없음
- [ ] API 모킹으로 외부 의존성 제거
- [ ] 스크린샷/비디오 설정 (실패 시)
- [ ] CI/CD 파이프라인 구성
