---
name: test-write
description: This skill should be used when the user asks to "테스트 작성해줘", "테스트 만들어줘", "테스트 코드 작성", "test 작성", "단위 테스트", "통합 테스트", "E2E 테스트", "unit test", "integration test", "e2e test", "MockMvc 테스트", "Mockito 테스트", "JUnit 테스트", "Given-When-Then", "GWT 패턴", "DB 검증 테스트", "Entity 검증", or mentions writing any type of tests. Provides interactive guidance for choosing test type and writing tests.
version: 1.0.0
---

# Test Write

대화형 테스트 작성 가이드. 사용자와 대화하며 적절한 테스트 유형을 선택하고 작성한다.

이 스킬은 implement/verify 커맨드의 tester 팀메이트가 참조합니다.

## 워크플로우 위치

```
specify → implement → verify
              │
              └─→ test-write (필요시)
                   ↑ 현재
```

**역할**: "추가 테스트 작성" 전용 (보조 스킬)
- Implement 단계에서 기본 테스트는 함께 작성됨
- Test-write는 **커버리지 부족** 또는 **특정 테스트 유형 필요** 시 사용
- Verify 실패 → Implement 재진입 시 활용 가능

**사용 시점**:
| 상황 | 사용 여부 |
|------|----------|
| 기본 단위 테스트 (implement 포함) | ❌ 불필요 |
| 커버리지 부족으로 verify 실패 | ✅ 사용 |
| E2E/통합 테스트 추가 요청 | ✅ 사용 |
| 특정 테스트 유형 선택이 필요할 때 | ✅ 사용 |

## 개요

테스트 요청을 받으면 다음을 수행:
1. 프로젝트 컨텍스트 파악 (스택, 기존 테스트 패턴)
2. **테스트 유형 제안** (사용자 선택 대기)
3. 선택된 유형에 맞는 테스트 작성
4. 테스트 실행 및 검증

## 테스트 유형 매트릭스

| 테스트 유형 | 대상 | 어노테이션 | DB | 속도 |
|------------|------|-----------|-----|------|
| **E2E** | Controller | `@WebMvcTest` | Mock/H2 | 빠름 |
| **GWT 통합** | Controller + DB | `@SpringBootTest` + `@AutoConfigureMockMvc` | H2/TestContainers | 중간 |
| **통합 (H2)** | Service/Repository | `@SpringBootTest` | H2 In-Memory | 중간 |
| **통합 (Real)** | Service/Repository | `@SpringBootTest` + `@Testcontainers` | PostgreSQL/MySQL | 느림 |
| **단위** | Service | `@ExtendWith(MockitoExtension.class)` | 없음 (Mock) | 매우 빠름 |
| **단위** | Repository | `@DataJpaTest` | H2 | 빠름 |

### Given-When-Then 통합 테스트 (권장)

**MockMvc 호출 후 Entity 직접 검증**이 필요한 경우:

```
참조: references/backend/gwt-integration.md
예제: examples/UserIntegrationGwtTest.java
```

핵심 패턴:
1. `BaseIntegrationTest` 상속 (공통 설정, 헬퍼 메서드)
2. `flushAndClear()` 호출로 영속성 컨텍스트 초기화
3. Repository로 Entity 직접 조회하여 DB 상태 검증
4. `log.debug()`로 Given/When/Then 각 단계 로깅

## 실행 절차

### Phase 0: 프로젝트 컨텍스트 파악

#### Step 1: 프로젝트 스택 감지

| 감지 파일 | 스택 | 참조 문서 |
|----------|------|----------|
| `build.gradle` (spring-boot) | Spring Boot | `references/backend/junit-*.md` |
| `pom.xml` (spring-boot) | Spring Boot | `references/backend/junit-*.md` |
| `playwright.config.ts` | Frontend | `references/frontend/playwright.md` |
| `cypress.config.ts` | Frontend | `references/frontend/cypress.md` |
| `package.json` (jest) | Node.js | `references/backend/supertest.md` |

#### Step 2: 기존 테스트 패턴 분석

확인 사항:
- 테스트 네이밍 컨벤션 (한글 vs 영어)
- 사용 중인 어노테이션 (`@SpringBootTest`, `@WebMvcTest` 등)
- Mock 프레임워크 (Mockito, BDDMockito 등)
- Logger 사용 여부

### Phase 1: 테스트 대상 확인

사용자가 지정한 테스트 대상 파악:
- 특정 클래스/메서드 지정됨 → 해당 대상 분석
- 미지정 → 최근 변경된 파일 또는 구현 대상 확인

### Phase 2: 테스트 유형 제안 (AskUserQuestion)

**테스트 대상 분석 후 적절한 유형을 제안한다.**

#### Controller 테스트 시

```
[옵션 A] E2E 테스트 (MockMvc) - 추천
- @WebMvcTest 사용, HTTP 요청/응답 전체 흐름 검증

[옵션 B] 통합 테스트
- @SpringBootTest + TestRestTemplate, 전체 스택 검증
```

#### Service 테스트 시

```
[옵션 A] 단위 테스트 (Mockito) - 추천
- @ExtendWith(MockitoExtension.class), 빠른 실행

[옵션 B] 통합 테스트 (H2)
- @SpringBootTest, H2 In-Memory DB

[옵션 C] 통합 테스트 (TestContainers)
- @SpringBootTest + @Testcontainers, 프로덕션 동일 환경
```

#### Repository 테스트 시

```
[옵션 A] @DataJpaTest (H2) - 추천
- JPA 레이어만 로드, 빠른 실행

[옵션 B] 통합 테스트 (TestContainers)
- 실제 DB와 동일한 환경
```

### Phase 2.5: DB 전략 선택 (통합 테스트 선택 시)

통합 테스트를 선택한 경우 DB 전략 확인:
- H2 In-Memory: 설정 간단, 빠른 실행, 표준 SQL만 사용하는 경우
- TestContainers: 실제 DB와 100% 호환, DB 특화 기능 사용 가능

### Phase 3: 테스트 코드 생성

**선택된 유형에 맞는 테스트 작성.**

#### 공통 패턴 (`references/common.md` 참조)

1. **Given-When-Then 패턴** (권장): 행동 중심 BDD 스타일
   - Given: 전제조건 설정
   - When: 테스트 행동 수행
   - Then: 결과 검증 (응답 + DB 상태)
2. **테스트 독립성**: 각 테스트가 독립적으로 실행
3. **한글 테스트명**: `@DisplayName("정상적인 사용자 생성 시 DB에 저장된다")`
4. **@Nested 그룹화**: 엔드포인트별 그룹화 (POST /api/users, GET /api/users/{id})
5. **Logger 포함**: `@Slf4j` + `log.debug()`로 각 단계 로깅

> 통합 테스트 필수: MockMvc 호출 후 `flushAndClear()` → Repository로 DB 검증

#### Logger 포함 테스트 템플릿

```java
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

#### 유형별 참조 문서

| 테스트 유형 | 참조 문서 | 언제 사용 |
|------------|----------|---------|
| **GWT 통합 테스트** | `references/backend/gwt-integration.md` | MockMvc + DB 검증 (권장) |
| 단위 테스트 (Mockito) | `references/backend/junit-unit.md` | Service 로직만 검증 |
| 통합 테스트 (H2/TestContainers) | `references/backend/junit-integration.md` | DB 연동 검증 |
| E2E 테스트 (MockMvc Mock) | `references/backend/junit-e2e.md` | Controller만 검증 (Service Mock) |

### Phase 4: 테스트 파일 생성

#### 파일 위치 결정

```
src/test/java/
└── com/example/
    ├── unit/           # 단위 테스트
    │   ├── service/
    │   │   └── UserServiceTest.java
    │   └── repository/
    │       └── UserRepositoryTest.java
    ├── integration/    # 통합 테스트
    │   └── UserServiceIntegrationTest.java
    └── e2e/            # E2E 테스트
        └── UserControllerE2ETest.java
```

또는 기존 프로젝트 컨벤션 따름.

### Phase 5: 실행 및 검증

```bash
# Gradle
./gradlew test --tests "UserServiceTest"

# Maven
mvn test -Dtest="UserServiceTest"
```

### Phase 6: 완료 및 결과 보고

```
✅ 테스트 작성 완료

📍 생성된 파일:
- src/test/java/.../UserServiceTest.java

📋 테스트 요약:
- 테스트 유형: 단위 테스트 (Mockito)
- 테스트 메서드: 5개
- 커버리지 대상: UserService

## 다음 단계
→ 구현 검증: /oh-my-speckit:verify
```

## 테스트 피라미드

```
        /\
       /E2E\        <- 적게 (비용 높음, 느림)
      /------\
     /통합테스트\    <- 중간
    /----------\
   /  단위 테스트  \  <- 많이 (비용 낮음, 빠름)
  /--------------\
```

**권장 비율**: 단위 70% / 통합 20% / E2E 10%

## 스택별 가이드

### Spring Boot (Java)

| 테스트 유형 | 문서 | 주요 어노테이션 |
|------------|------|---------------|
| 단위 테스트 | `references/backend/junit-unit.md` | `@ExtendWith(MockitoExtension.class)` |
| 통합 테스트 | `references/backend/junit-integration.md` | `@SpringBootTest`, `@Testcontainers` |
| E2E 테스트 | `references/backend/junit-e2e.md` | `@WebMvcTest`, `@AutoConfigureMockMvc` |

### Frontend

| 프레임워크 | 문서 | 주요 특징 |
|-----------|------|----------|
| Playwright | `references/frontend/playwright.md` | 크로스 브라우저, 자동 대기 |
| Cypress | `references/frontend/cypress.md` | 실시간 리로드, 타임트래블 |

### Node.js

| 프레임워크 | 문서 | 주요 특징 |
|-----------|------|----------|
| Supertest + Jest | `references/backend/supertest.md` | Express/NestJS API 테스트 |

## 절대 금지 규칙 (CRITICAL)

### 금지 1: 빈 테스트 메서드 작성 금지

**모든 @Test 메서드는 반드시 실제 검증 로직을 포함해야 합니다.**

```java
// ❌ 절대 금지 - 빈 테스트
@Test
void 사용자_탈퇴_성공() throws Exception {
    // When
    // TODO: mockMvc 호출 예정  ← 주석만 있음!
    // Then
    // 검증 필요  ← 검증 로직 없음!
}

// ✅ 필수 - 실제 검증 포함
@Test
void 사용자_탈퇴_성공() throws Exception {
    // Given
    User user = createTestUser("test@example.com");
    // When
    performDelete("/api/users/{id}", user.getId())
        .andExpect(status().isNoContent());
    // Then
    flushAndClear();
    assertThat(userRepository.findById(user.getId())).isEmpty();
}
```

**검증 체크리스트:**
- [ ] `mockMvc.perform()` 또는 실제 메서드 호출이 있는가?
- [ ] `andExpect()` 또는 `assertThat()`이 최소 1개 이상 있는가?
- [ ] 주석으로만 작성된 코드가 없는가?

### 금지 2: 에러 코드-상황 불일치 금지

**에러 코드의 메시지가 실제 발생 상황과 반드시 일치해야 합니다.**

```java
// ❌ 금지 - 상황과 다른 에러 코드
throw new BusinessException(ErrorCode.USER_ALREADY_DELETED);  // "이미 탈퇴한 사용자"

// ✅ 올바른 사용
throw new BusinessException(ErrorCode.USER_ALREADY_ACTIVE);  // "이미 활성화된 사용자"
```

**사용 전 확인:**
1. ErrorCode enum/상수 클래스를 Read하여 메시지 확인
2. 메시지가 현재 상황을 정확히 설명하는지 검토
3. 적합한 코드 없으면 새로 생성 제안

---

## 관련 스킬

| 스킬 | 관계 |
|------|------|
| implement | 이전 단계 (기본 테스트 포함) |
| verify | 다음 단계 (테스트 실행) |

## 참고 자료

| 파일 | 설명 |
|------|------|
| `references/common.md` | 테스트 공통 패턴 (GWT, 독립성, 네이밍) |
| **`references/backend/gwt-integration.md`** | **Given-When-Then + DB 검증 (권장)** |
| `references/backend/junit-unit.md` | JUnit + Mockito 단위 테스트 |
| `references/backend/junit-integration.md` | H2/TestContainers 통합 테스트 |
| `references/backend/junit-e2e.md` | MockMvc E2E 테스트 |
| `references/backend/supertest.md` | Node.js API 테스트 |
| `references/frontend/playwright.md` | Playwright 가이드 |
| `references/frontend/cypress.md` | Cypress 가이드 |
| `examples/UserIntegrationGwtTest.java` | **GWT 통합 테스트 완전 예제** |
| `examples/` | 기타 실전 예시 코드 |
