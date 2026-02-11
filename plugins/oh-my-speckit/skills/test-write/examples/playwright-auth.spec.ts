/**
 * 인증 기능 E2E 테스트 예시 (Playwright)
 *
 * 실행: npx playwright test auth.spec.ts
 */

import { test, expect } from '@playwright/test';

test.describe('인증 기능', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
  });

  test('유효한 자격증명으로 로그인하면 대시보드로 이동한다', async ({ page }) => {
    // Arrange - 이미 beforeEach에서 /login으로 이동

    // Act
    await page.fill('[data-testid="email-input"]', 'user@example.com');
    await page.fill('[data-testid="password-input"]', 'password123');
    await page.click('[data-testid="login-button"]');

    // Assert
    await expect(page).toHaveURL('/dashboard');
    await expect(page.locator('[data-testid="welcome-message"]')).toContainText('환영합니다');
  });

  test('잘못된 비밀번호로 로그인하면 에러 메시지를 표시한다', async ({ page }) => {
    // Act
    await page.fill('[data-testid="email-input"]', 'user@example.com');
    await page.fill('[data-testid="password-input"]', 'wrongpassword');
    await page.click('[data-testid="login-button"]');

    // Assert
    await expect(page.locator('[data-testid="error-message"]')).toBeVisible();
    await expect(page.locator('[data-testid="error-message"]')).toContainText('비밀번호가 일치하지 않습니다');
    await expect(page).toHaveURL('/login'); // 페이지 이동 없음
  });

  test('존재하지 않는 이메일로 로그인 시도하면 에러를 표시한다', async ({ page }) => {
    await page.fill('[data-testid="email-input"]', 'nonexistent@example.com');
    await page.fill('[data-testid="password-input"]', 'password123');
    await page.click('[data-testid="login-button"]');

    await expect(page.locator('[data-testid="error-message"]')).toContainText('사용자를 찾을 수 없습니다');
  });

  test('이메일 형식이 잘못되면 유효성 검사 에러를 표시한다', async ({ page }) => {
    await page.fill('[data-testid="email-input"]', 'invalid-email');
    await page.fill('[data-testid="password-input"]', 'password123');
    await page.click('[data-testid="login-button"]');

    await expect(page.locator('[data-testid="email-error"]')).toContainText('유효한 이메일');
  });
});

test.describe('로그아웃', () => {
  test.beforeEach(async ({ page }) => {
    // 로그인 상태로 시작
    await page.goto('/login');
    await page.fill('[data-testid="email-input"]', 'user@example.com');
    await page.fill('[data-testid="password-input"]', 'password123');
    await page.click('[data-testid="login-button"]');
    await page.waitForURL('/dashboard');
  });

  test('로그아웃하면 로그인 페이지로 이동한다', async ({ page }) => {
    await page.click('[data-testid="user-menu"]');
    await page.click('[data-testid="logout-button"]');

    await expect(page).toHaveURL('/login');
  });

  test('로그아웃 후 보호된 페이지 접근 시 로그인으로 리다이렉트된다', async ({ page }) => {
    await page.click('[data-testid="user-menu"]');
    await page.click('[data-testid="logout-button"]');
    await page.waitForURL('/login');

    // 보호된 페이지 직접 접근 시도
    await page.goto('/dashboard');

    await expect(page).toHaveURL('/login');
  });
});

test.describe('회원가입', () => {
  test('새 계정을 생성하면 자동으로 로그인된다', async ({ page }) => {
    await page.goto('/register');

    await page.fill('[data-testid="name-input"]', '홍길동');
    await page.fill('[data-testid="email-input"]', `test-${Date.now()}@example.com`);
    await page.fill('[data-testid="password-input"]', 'SecurePass123!');
    await page.fill('[data-testid="password-confirm-input"]', 'SecurePass123!');
    await page.click('[data-testid="register-button"]');

    await expect(page).toHaveURL('/dashboard');
    await expect(page.locator('[data-testid="welcome-message"]')).toContainText('홍길동');
  });

  test('이미 존재하는 이메일로 가입 시도하면 에러를 표시한다', async ({ page }) => {
    await page.goto('/register');

    await page.fill('[data-testid="name-input"]', '홍길동');
    await page.fill('[data-testid="email-input"]', 'existing@example.com');
    await page.fill('[data-testid="password-input"]', 'SecurePass123!');
    await page.fill('[data-testid="password-confirm-input"]', 'SecurePass123!');
    await page.click('[data-testid="register-button"]');

    await expect(page.locator('[data-testid="error-message"]')).toContainText('이미 사용 중인 이메일');
  });
});
