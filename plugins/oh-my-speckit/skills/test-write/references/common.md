# 테스트 공통 패턴

모든 테스트(단위/통합/E2E)에 적용되는 공통 원칙과 패턴.

## 핵심 원칙

### 1. AAA 패턴 (Arrange-Act-Assert)

```typescript
// Playwright 예시
test('사용자가 로그인하면 대시보드로 이동해야 한다', async ({ page }) => {
  // Arrange - 테스트 환경 준비
  await page.goto('/login');

  // Act - 테스트 동작 수행
  await page.fill('[data-testid="email"]', 'user@example.com');
  await page.fill('[data-testid="password"]', 'password123');
  await page.click('[data-testid="login-button"]');

  // Assert - 결과 검증
  await expect(page).toHaveURL('/dashboard');
  await expect(page.locator('h1')).toContainText('Welcome');
});
```

```java
// JUnit 예시
@Test
void 사용자가_로그인하면_토큰을_반환해야_한다() throws Exception {
    // Arrange
    LoginRequest request = new LoginRequest("user@example.com", "password123");

    // Act
    ResultActions result = mockMvc.perform(post("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)));

    // Assert
    result.andExpect(status().isOk())
          .andExpect(jsonPath("$.token").exists());
}
```

### 2. 테스트 독립성

각 테스트는 다른 테스트에 의존하지 않아야 함:

```typescript
// Bad - 테스트 간 의존성
test('1. 사용자 생성', async () => { /* ... */ });
test('2. 생성된 사용자로 로그인', async () => { /* 위 테스트에 의존 */ });

// Good - 독립적 테스트
test('사용자가 회원가입하면 자동 로그인된다', async ({ page }) => {
  // 이 테스트 내에서 사용자 생성부터 로그인까지 모두 수행
});
```

### 3. 데이터 격리

#### Fixtures 사용

```typescript
// fixtures/users.json
{
  "testUser": {
    "email": "test@example.com",
    "password": "Test1234!"
  }
}

// 테스트에서 사용
import users from './fixtures/users.json';

test('로그인 테스트', async ({ page }) => {
  await page.fill('#email', users.testUser.email);
});
```

#### 테스트 데이터 정리

```typescript
// beforeEach/afterEach로 데이터 정리
test.beforeEach(async ({ request }) => {
  await request.post('/api/test/reset-db');
});
```

```java
// @Transactional로 자동 롤백
@SpringBootTest
@Transactional
class UserIntegrationTest {
    // 각 테스트 후 자동 롤백
}
```

## 네이밍 컨벤션

### 테스트 파일명

```
# 프론트엔드
auth.spec.ts          # 기능 단위
login.spec.ts         # 세부 기능
user-profile.spec.ts  # kebab-case

# 백엔드 (Java)
AuthControllerIntegrationTest.java
UserServiceIntegrationTest.java
```

### 테스트 케이스명

```typescript
// should_동작_when_조건 패턴
test('should redirect to dashboard when login succeeds', ...);
test('should show error message when password is invalid', ...);

// 한글 사용 (권장)
test('로그인 성공 시 대시보드로 이동해야 한다', ...);
test('잘못된 비밀번호 입력 시 에러 메시지를 표시해야 한다', ...);
```

```java
// 메서드명_상황_기대결과 패턴
@Test
void login_withValidCredentials_returnsToken() { }

@Test
void login_withInvalidPassword_returns401() { }

// 한글 (권장)
@Test
void 유효한_자격증명으로_로그인하면_토큰을_반환한다() { }
```

## 선택자 전략

### data-testid 사용 (권장)

```html
<!-- HTML -->
<button data-testid="submit-button">제출</button>
<input data-testid="email-input" type="email" />
```

```typescript
// 테스트
await page.click('[data-testid="submit-button"]');
await page.fill('[data-testid="email-input"]', 'test@example.com');
```

### 선택자 우선순위

| 우선순위 | 선택자 | 이유 |
|---------|--------|------|
| 1 | `data-testid` | 테스트 전용, 변경에 강함 |
| 2 | Role (getByRole) | 접근성 기반, 의미 있음 |
| 3 | Text (getByText) | 사용자 관점, 변경 가능성 |
| 4 | CSS selector | 마지막 수단 |

```typescript
// 권장 순서
await page.getByTestId('submit-button').click();
await page.getByRole('button', { name: '제출' }).click();
await page.getByText('제출').click();
await page.click('.submit-btn'); // 피하기
```

## 대기 전략

### 명시적 대기 (권장)

```typescript
// Playwright - 자동 대기 내장
await page.click('button'); // 자동으로 클릭 가능할 때까지 대기

// 명시적 대기가 필요한 경우
await page.waitForSelector('[data-testid="result"]');
await page.waitForResponse('**/api/users');
await expect(page.locator('.loading')).toBeHidden();
```

### 하드코딩된 대기 피하기

```typescript
// Bad
await page.waitForTimeout(3000); // 절대 금지!

// Good
await page.waitForLoadState('networkidle');
await expect(page.locator('.spinner')).toBeHidden();
```

## 환경 설정

### 테스트 환경 분리

```bash
# .env.test
DATABASE_URL=postgresql://localhost:5432/test_db
API_URL=http://localhost:3001
```

```typescript
// playwright.config.ts
export default defineConfig({
  use: {
    baseURL: process.env.API_URL || 'http://localhost:3000',
  },
});
```

### CI/CD 고려사항

```yaml
# GitHub Actions 예시
- name: Run E2E Tests
  run: npx playwright test
  env:
    CI: true
    BASE_URL: ${{ secrets.TEST_URL }}
```

## Mock과 Stub

### 외부 서비스 Mocking

```typescript
// Playwright - API Mocking
await page.route('**/api/external-service', route => {
  route.fulfill({
    status: 200,
    body: JSON.stringify({ data: 'mocked' }),
  });
});
```

```java
// Spring - @MockBean
@SpringBootTest
class PaymentIntegrationTest {
    @MockBean
    private ExternalPaymentGateway paymentGateway;

    @BeforeEach
    void setup() {
        when(paymentGateway.process(any()))
            .thenReturn(PaymentResult.success());
    }
}
```

### 언제 Mock을 사용할까?

| Mock 사용 | 실제 호출 |
|----------|----------|
| 외부 결제 시스템 | 내부 DB 연동 |
| 이메일 발송 | 내부 API 간 통신 |
| SMS 발송 | 캐시 시스템 |
| 서드파티 API | 메시지 큐 |

## 에러 케이스 테스트

```typescript
test('네트워크 오류 시 에러 메시지를 표시한다', async ({ page }) => {
  // 네트워크 오류 시뮬레이션
  await page.route('**/api/data', route => route.abort());

  await page.click('[data-testid="fetch-button"]');

  await expect(page.locator('[data-testid="error-message"]'))
    .toContainText('네트워크 오류');
});
```

```java
@Test
void 서버_오류_시_500을_반환한다() throws Exception {
    // 강제로 예외 발생
    doThrow(new RuntimeException("DB Error"))
        .when(userService).findById(any());

    mockMvc.perform(get("/api/users/1"))
        .andExpect(status().isInternalServerError());
}
```

## 테스트 로깅 패턴

테스트 실행 시 디버깅 및 성능 분석을 위한 로깅 패턴.

### JUnit + @Slf4j

```java
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

@Slf4j
class SomeTest {
    private long testStartTime;

    @BeforeEach
    void setUp(TestInfo testInfo) {
        testStartTime = System.currentTimeMillis();
        log.info("=== Test Started: {} ===", testInfo.getDisplayName());
    }

    @AfterEach
    void tearDown(TestInfo testInfo) {
        long duration = System.currentTimeMillis() - testStartTime;
        log.info("=== Test Completed: {} ({}ms) ===",
                 testInfo.getDisplayName(), duration);
    }

    @Test
    @DisplayName("테스트 설명")
    void 테스트_메서드명() {
        // Arrange
        log.debug("Setting up test data...");

        // Act
        log.debug("Executing target method...");

        // Assert
        log.debug("Verifying results...");
    }
}
```

### 로깅 설정 (application-test.yml)

```yaml
logging:
  level:
    root: INFO
    com.example: DEBUG                    # 프로젝트 패키지
    org.springframework.web: DEBUG        # Spring Web 요청/응답
    org.hibernate.SQL: DEBUG              # Hibernate SQL 쿼리
    org.hibernate.type.descriptor.sql: TRACE  # SQL 바인딩 파라미터
```

### Playwright/Cypress

```typescript
// beforeEach/afterEach로 로깅
let testStartTime: number;

test.beforeEach(async ({}, testInfo) => {
  testStartTime = Date.now();
  console.log(`=== Test Started: ${testInfo.title} ===`);
});

test.afterEach(async ({}, testInfo) => {
  const duration = Date.now() - testStartTime;
  console.log(`=== Test Completed: ${testInfo.title} (${duration}ms) ===`);
});
```

## 🚫 절대 금지 규칙 (CRITICAL)

### 1. 빈 테스트 메서드 작성 금지

**모든 테스트는 실제 실행 코드와 검증 로직을 반드시 포함해야 합니다.**

```java
// ❌ 절대 금지 - 주석만 있는 빈 테스트
@Test
void 사용자_탈퇴_성공() throws Exception {
    // Given - 사용자 생성
    // When - 탈퇴 API 호출
    // Then - 검증
}
// → 이 테스트는 아무것도 안 하고 통과됨!

// ✅ 필수 - 실제 코드 포함
@Test
void 사용자_탈퇴_성공() throws Exception {
    // Given
    User user = createTestUser("test@example.com");

    // When
    mockMvc.perform(delete("/api/users/{id}", user.getId()))
        .andExpect(status().isNoContent());

    // Then
    assertThat(userRepository.findById(user.getId())).isEmpty();
}
```

**필수 포함 요소:**
- 실제 메서드 호출 또는 `mockMvc.perform()`
- `assertThat()` 또는 `andExpect()` 검증

### 2. 에러 코드-상황 불일치 금지

**에러 코드의 메시지가 실제 발생 상황과 일치해야 합니다.**

```java
// ❌ 금지 - 상황: "이미 복구된 사용자 재복구"
throw new BusinessException(ErrorCode.USER_ALREADY_DELETED);  // 메시지: "이미 탈퇴한 사용자"
// → 복구인데 왜 탈퇴 에러? 디버깅 혼란!

// ✅ 올바름 - 메시지가 상황 설명
throw new BusinessException(ErrorCode.USER_ALREADY_ACTIVE);  // 메시지: "이미 활성화된 사용자"
```

**에러 코드 사용 전 확인:**
1. ErrorCode enum 정의와 메시지 확인
2. 메시지가 현재 상황을 정확히 설명하는지 검토
3. 적합한 에러 코드가 없으면 새로 생성 제안

---

## 체크리스트

테스트 작성 시 확인:

- [ ] AAA 패턴을 따르는가?
- [ ] 다른 테스트에 의존하지 않는가?
- [ ] data-testid를 사용하는가?
- [ ] 하드코딩된 대기가 없는가?
- [ ] 테스트 데이터가 격리되어 있는가?
- [ ] 에러 케이스도 테스트하는가?
- [ ] 테스트명이 명확한가?
- [ ] 테스트 로깅이 포함되어 있는가?
- [ ] **⚠️ 모든 @Test에 실제 호출/검증 코드가 있는가?**
- [ ] **⚠️ 에러 코드 메시지가 상황과 일치하는가?**
