# Given-When-Then 통합 테스트 가이드

MockMvc + Entity 직접 검증 패턴. **API 호출 후 실제 DB 상태를 확인**하는 완전한 통합 테스트.

> 💡 **테스트 로깅 패턴**은 [common.md](../common.md#테스트-로깅-패턴)를 참조하세요.

## 핵심 원칙

| 원칙 | 설명 |
|------|------|
| **Given-When-Then** | BDD 스타일의 명확한 테스트 구조 |
| **DB 상태 검증** | MockMvc 호출 후 Repository로 Entity 직접 조회 |
| **완전한 격리** | `@Transactional`로 테스트 간 데이터 격리 |
| **일관된 템플릿** | BaseTest 클래스로 구조 통일 |

## Given-When-Then vs AAA 패턴

| Given-When-Then | AAA (Arrange-Act-Assert) |
|----------------|-------------------------|
| **Given** (전제조건) | **Arrange** (준비) |
| **When** (행동) | **Act** (실행) |
| **Then** (결과) | **Assert** (검증) |

**핵심 차이점:**
- AAA: 내부 상태와 기술적 동작에 초점
- GWT: 행동(behavior)과 비즈니스 로직에 초점 (BDD 스타일)

## 표준 테스트 템플릿

### 1. BaseIntegrationTest 추상 클래스

모든 통합 테스트에서 상속받는 기본 클래스:

```java
package com.example.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected EntityManager entityManager;

    // 테스트 시간 측정
    private long testStartTime;

    @BeforeEach
    void setUpBase(TestInfo testInfo) {
        testStartTime = System.currentTimeMillis();
        log.info("=== Test Started: {} ===", testInfo.getDisplayName());
    }

    @AfterEach
    void tearDownBase(TestInfo testInfo) {
        long duration = System.currentTimeMillis() - testStartTime;
        log.info("=== Test Completed: {} ({}ms) ===",
                 testInfo.getDisplayName(), duration);
    }

    /**
     * 영속성 컨텍스트를 초기화하여 실제 DB 조회를 강제합니다.
     * MockMvc 호출 후 Entity 검증 전에 반드시 호출하세요.
     */
    protected void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    /**
     * JSON 응답에서 ID를 추출합니다.
     */
    protected Long extractIdFromResponse(MvcResult result) throws Exception {
        String content = result.getResponse().getContentAsString();
        Number id = JsonPath.read(content, "$.id");
        return id.longValue();
    }

    /**
     * 표준화된 GET 요청
     */
    protected ResultActions performGet(String url, Object... uriVars) throws Exception {
        return mockMvc.perform(get(url, uriVars)
            .accept(MediaType.APPLICATION_JSON));
    }

    /**
     * 표준화된 POST 요청
     */
    protected ResultActions performPost(String url, Object body) throws Exception {
        return mockMvc.perform(post(url)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)));
    }

    /**
     * 표준화된 PUT 요청
     */
    protected ResultActions performPut(String url, Object body, Object... uriVars) throws Exception {
        return mockMvc.perform(put(url, uriVars)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)));
    }

    /**
     * 표준화된 PATCH 요청
     */
    protected ResultActions performPatch(String url, Object body, Object... uriVars) throws Exception {
        return mockMvc.perform(patch(url, uriVars)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)));
    }

    /**
     * 표준화된 DELETE 요청
     */
    protected ResultActions performDelete(String url, Object... uriVars) throws Exception {
        return mockMvc.perform(delete(url, uriVars));
    }
}
```

### 2. 도메인별 BaseTest 클래스

```java
package com.example.support;

import com.example.domain.User;
import com.example.domain.Product;
import com.example.repository.UserRepository;
import com.example.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class BaseOrderTest extends BaseIntegrationTest {

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected ProductRepository productRepository;

    /**
     * 테스트용 사용자 생성
     */
    protected User createTestUser(String email, String name) {
        return userRepository.save(User.builder()
            .email(email)
            .name(name)
            .password("encoded-password")
            .build());
    }

    /**
     * 테스트용 상품 생성
     */
    protected Product createTestProduct(String name, double price, int stock) {
        return productRepository.save(Product.builder()
            .name(name)
            .price(price)
            .stock(stock)
            .build());
    }
}
```

## 핵심 패턴: MockMvc + Entity 검증

### CREATE 테스트 (POST)

```java
@Test
@DisplayName("정상적인 사용자 생성 시 DB에 저장되고 비밀번호가 암호화된다")
void givenValidUserRequest_whenCreateUser_thenUserIsPersistedWithEncodedPassword() throws Exception {
    // Given
    CreateUserRequest request = new CreateUserRequest(
        "홍길동",
        "hong@example.com",
        "password123"
    );
    log.debug("Given: 유효한 사용자 생성 요청 - email={}", request.email());

    // When
    MvcResult result = performPost("/api/users", request)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.name").value("홍길동"))
        .andExpect(jsonPath("$.email").value("hong@example.com"))
        .andReturn();
    log.debug("When: POST /api/users 호출 완료");

    // Then - DB 상태 검증
    Long userId = extractIdFromResponse(result);
    flushAndClear();  // ⚠️ 중요: 영속성 컨텍스트 초기화

    User savedUser = userRepository.findById(userId)
        .orElseThrow(() -> new AssertionError("사용자가 DB에 저장되지 않았습니다"));

    assertThat(savedUser.getName()).isEqualTo("홍길동");
    assertThat(savedUser.getEmail()).isEqualTo("hong@example.com");
    assertThat(savedUser.getPassword()).isNotEqualTo("password123");  // 암호화 확인
    assertThat(savedUser.getCreatedAt()).isNotNull();
    log.debug("Then: DB에서 사용자 확인 완료 - id={}", savedUser.getId());
}
```

### UPDATE 테스트 (PUT/PATCH)

```java
@Test
@DisplayName("기존 사용자 정보 수정 시 지정된 필드만 변경된다")
void givenExistingUser_whenUpdateProfile_thenOnlySpecifiedFieldsAreUpdated() throws Exception {
    // Given
    User existingUser = userRepository.save(User.builder()
        .name("홍길동")
        .email("hong@example.com")
        .password("encoded-password")
        .build());
    LocalDateTime originalCreatedAt = existingUser.getCreatedAt();
    Long userId = existingUser.getId();
    log.debug("Given: 기존 사용자 - id={}, name={}", userId, existingUser.getName());

    UpdateUserRequest request = new UpdateUserRequest("홍길동2", null);

    // When
    performPut("/api/users/{id}", request, userId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("홍길동2"));
    log.debug("When: PUT /api/users/{} 호출 완료", userId);

    // Then - DB 상태 검증
    flushAndClear();

    User updatedUser = userRepository.findById(userId).orElseThrow();

    assertThat(updatedUser.getName()).isEqualTo("홍길동2");           // 변경됨
    assertThat(updatedUser.getEmail()).isEqualTo("hong@example.com"); // 유지됨
    assertThat(updatedUser.getPassword()).isEqualTo("encoded-password"); // 유지됨
    assertThat(updatedUser.getCreatedAt()).isEqualTo(originalCreatedAt); // 유지됨
    assertThat(updatedUser.getUpdatedAt()).isAfter(originalCreatedAt);   // 갱신됨
    log.debug("Then: DB에서 변경사항 확인 완료");
}
```

### DELETE 테스트

```java
@Test
@DisplayName("기존 사용자 삭제 시 DB에서 제거된다")
void givenExistingUser_whenDeleteUser_thenUserIsRemovedFromDatabase() throws Exception {
    // Given
    User existingUser = userRepository.save(User.builder()
        .name("삭제될사용자")
        .email("delete@example.com")
        .password("encoded")
        .build());
    Long userId = existingUser.getId();
    assertThat(userRepository.findById(userId)).isPresent();  // 삭제 전 존재 확인
    log.debug("Given: 삭제 대상 사용자 - id={}", userId);

    // When
    performDelete("/api/users/{id}", userId)
        .andExpect(status().isNoContent());
    log.debug("When: DELETE /api/users/{} 호출 완료", userId);

    // Then - DB 상태 검증
    flushAndClear();

    assertThat(userRepository.findById(userId)).isEmpty();
    log.debug("Then: DB에서 사용자 삭제 확인 완료");

    // 추가: 조회 시 404 반환 확인
    performGet("/api/users/{id}", userId)
        .andExpect(status().isNotFound());
}
```

### 연관 엔티티 검증 (OneToMany)

```java
@Test
@DisplayName("주문 생성 시 모든 주문 항목이 DB에 저장된다")
void givenOrderWithItems_whenCreateOrder_thenAllItemsArePersisted() throws Exception {
    // Given
    User user = createTestUser("buyer@example.com", "구매자");
    Product laptop = createTestProduct("Laptop", 1000.0, 10);
    Product mouse = createTestProduct("Mouse", 25.0, 50);

    CreateOrderRequest request = CreateOrderRequest.builder()
        .userId(user.getId())
        .items(List.of(
            new OrderItemDto(laptop.getId(), 1),
            new OrderItemDto(mouse.getId(), 2)
        ))
        .build();
    log.debug("Given: 주문 요청 - 사용자={}, 상품 2개", user.getId());

    // When
    MvcResult result = performPost("/api/orders", request)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.totalAmount").value(1050.0))  // 1000 + 25*2
        .andReturn();
    log.debug("When: POST /api/orders 호출 완료");

    // Then - 주문 및 연관 엔티티 검증
    Long orderId = extractIdFromResponse(result);
    flushAndClear();

    Order savedOrder = orderRepository.findById(orderId).orElseThrow();

    assertThat(savedOrder.getUser().getId()).isEqualTo(user.getId());
    assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
    assertThat(savedOrder.getItems()).hasSize(2);

    // 개별 항목 검증
    OrderItem laptopItem = savedOrder.getItems().stream()
        .filter(item -> item.getProduct().getId().equals(laptop.getId()))
        .findFirst()
        .orElseThrow();

    assertThat(laptopItem.getQuantity()).isEqualTo(1);
    assertThat(laptopItem.getPrice()).isEqualTo(1000.0);
    assertThat(laptopItem.getOrder()).isEqualTo(savedOrder);  // 양방향 관계 확인

    // 재고 감소 검증
    Product updatedLaptop = productRepository.findById(laptop.getId()).orElseThrow();
    assertThat(updatedLaptop.getStock()).isEqualTo(9);  // 10 - 1

    log.debug("Then: 주문 및 연관 엔티티 검증 완료");
}
```

## 엣지케이스 테스트

### 경계값 테스트

```java
@Nested
@DisplayName("경계값 테스트")
class BoundaryTests {

    @Test
    @DisplayName("재고가 0일 때 주문 시도하면 실패한다")
    void givenZeroStock_whenOrderProduct_thenReturnOutOfStock() throws Exception {
        // Given
        User user = createTestUser("buyer@example.com", "구매자");
        Product outOfStockProduct = createTestProduct("품절상품", 100.0, 0);

        CreateOrderRequest request = CreateOrderRequest.builder()
            .userId(user.getId())
            .items(List.of(new OrderItemDto(outOfStockProduct.getId(), 1)))
            .build();

        // When & Then
        performPost("/api/orders", request)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("OUT_OF_STOCK"));

        // DB 상태 변경 없음 확인
        flushAndClear();
        assertThat(orderRepository.count()).isEqualTo(0);
        assertThat(productRepository.findById(outOfStockProduct.getId()).get().getStock()).isEqualTo(0);
    }

    @Test
    @DisplayName("마지막 1개 재고 주문 시 성공하고 재고가 0이 된다")
    void givenLastItemInStock_whenOrder_thenSucceedAndStockBecomesZero() throws Exception {
        // Given
        User user = createTestUser("buyer@example.com", "구매자");
        Product lastItem = createTestProduct("마지막상품", 100.0, 1);

        CreateOrderRequest request = CreateOrderRequest.builder()
            .userId(user.getId())
            .items(List.of(new OrderItemDto(lastItem.getId(), 1)))
            .build();

        // When
        performPost("/api/orders", request)
            .andExpect(status().isCreated());

        // Then
        flushAndClear();
        Product soldOut = productRepository.findById(lastItem.getId()).orElseThrow();
        assertThat(soldOut.getStock()).isEqualTo(0);
    }
}
```

### Null 처리 테스트

```java
@Nested
@DisplayName("Null 처리 테스트")
class NullHandlingTests {

    @Test
    @DisplayName("필수 필드가 null이면 400 에러를 반환한다")
    void givenNullRequiredField_whenCreate_thenReturnBadRequest() throws Exception {
        // Given
        CreateUserRequest request = new CreateUserRequest(
            null,  // name이 null (필수 필드)
            "test@example.com",
            "password"
        );

        // When & Then
        performPost("/api/users", request)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists());
    }

    @Test
    @DisplayName("선택적 필드가 null이면 정상 저장된다")
    void givenNullOptionalField_whenCreate_thenSucceed() throws Exception {
        // Given
        CreateUserRequest request = new CreateUserRequest(
            "홍길동",
            "test@example.com",
            "password",
            null  // address (선택 필드)
        );

        // When
        MvcResult result = performPost("/api/users", request)
            .andExpect(status().isCreated())
            .andReturn();

        // Then
        Long userId = extractIdFromResponse(result);
        flushAndClear();

        User savedUser = userRepository.findById(userId).orElseThrow();
        assertThat(savedUser.getAddress()).isNull();
    }
}
```

### 중복 검증 테스트

```java
@Test
@DisplayName("중복 이메일로 가입 시도하면 409 에러를 반환한다")
void givenDuplicateEmail_whenCreateUser_thenReturnConflict() throws Exception {
    // Given
    userRepository.save(User.builder()
        .name("기존사용자")
        .email("exist@example.com")
        .password("encoded")
        .build());

    CreateUserRequest request = new CreateUserRequest(
        "새사용자",
        "exist@example.com",  // 중복 이메일
        "password"
    );

    // When & Then
    performPost("/api/users", request)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("DUPLICATE_EMAIL"));

    // 새 사용자가 생성되지 않았는지 확인
    flushAndClear();
    List<User> users = userRepository.findAll();
    assertThat(users).hasSize(1);  // 기존 1명만 존재
}
```

### 인증/권한 테스트

```java
@Nested
@DisplayName("인증/권한 테스트")
class AuthorizationTests {

    @Test
    @WithMockUser(roles = {"USER"})
    @DisplayName("일반 사용자가 관리자 기능 접근 시 403 에러")
    void givenUserRole_whenAccessAdminEndpoint_thenReturn403() throws Exception {
        performDelete("/api/users/1")
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("인증 없이 보호된 엔드포인트 접근 시 401 에러")
    void givenNoAuth_whenAccessProtectedEndpoint_thenReturn401() throws Exception {
        performGet("/api/users/1")
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("관리자가 삭제 시 성공")
    void givenAdminRole_whenDeleteUser_thenSucceed() throws Exception {
        // Given
        User user = createTestUser("delete@example.com", "삭제대상");

        // When
        performDelete("/api/users/{id}", user.getId())
            .andExpect(status().isNoContent());

        // Then
        flushAndClear();
        assertThat(userRepository.findById(user.getId())).isEmpty();
    }
}
```

## BDDMockito 사용법

### given().willReturn() 패턴

```java
import static org.mockito.BDDMockito.*;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @MockBean
    private UserService userService;

    @Test
    void givenValidUserId_whenGetUser_thenReturnUser() throws Exception {
        // Given
        Long userId = 1L;
        User user = User.builder().id(userId).name("홍길동").build();
        given(userService.findById(userId)).willReturn(user);

        // When & Then
        mockMvc.perform(get("/api/users/{id}", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("홍길동"));

        then(userService).should().findById(userId);
    }
}
```

### willThrow() 예외 처리

```java
@Test
void givenNonExistentUser_whenGetUser_thenReturn404() throws Exception {
    // Given
    Long userId = 999L;
    given(userService.findById(userId))
        .willThrow(new UserNotFoundException("사용자를 찾을 수 없습니다"));

    // When & Then
    mockMvc.perform(get("/api/users/{id}", userId))
        .andExpect(status().isNotFound());
}
```

## 테스트 디렉토리 구조

```
src/test/java/
└── com/example/
    ├── support/                        # 테스트 지원 클래스
    │   ├── BaseIntegrationTest.java    # 공통 추상 클래스
    │   ├── BaseOrderTest.java          # 주문 도메인 기본 클래스
    │   └── TestDataFactory.java        # 테스트 데이터 팩토리
    │
    ├── controller/                     # @WebMvcTest (Mock 기반)
    │   └── UserControllerTest.java
    │
    └── integration/                    # @SpringBootTest (실제 DB)
        ├── UserIntegrationTest.java
        └── OrderIntegrationTest.java
```

## 🚫 절대 금지 규칙

### 빈 테스트 메서드 작성 금지

```java
// ❌ 절대 금지 - 주석만 있고 실제 코드 없음
@Test
void 테스트_메서드() throws Exception {
    // Given
    // 데이터 설정...

    // When
    // mockMvc 호출 예정...  ← 주석만!

    // Then
    // 검증 예정...  ← 실제 검증 없음!
}
```

**모든 @Test는 반드시 포함해야 함:**
- `mockMvc.perform()` 또는 실제 메서드 호출
- `andExpect()` 또는 `assertThat()` 검증

### 에러 코드-상황 불일치 금지

```java
// ❌ 금지 - 복구 시도인데 "탈퇴" 에러 사용
throw new BusinessException(ErrorCode.USER_ALREADY_DELETED);

// ✅ 올바름 - 상황에 맞는 에러 코드
throw new BusinessException(ErrorCode.USER_ALREADY_ACTIVE);
```

**사용 전 반드시 ErrorCode 정의 확인!**

---

## 체크리스트

### 통합 테스트 작성 시

- [ ] `BaseIntegrationTest` 상속
- [ ] `@DisplayName`으로 한글 테스트명 작성
- [ ] Given-When-Then 주석으로 구조 표시
- [ ] `log.debug()`로 각 단계 로깅
- [ ] MockMvc 호출 후 `flushAndClear()` 호출
- [ ] Repository로 Entity 직접 조회하여 검증
- [ ] 연관 엔티티 상태도 함께 검증 (재고 감소 등)
- [ ] **⚠️ 모든 @Test에 실제 호출/검증 코드 존재**
- [ ] **⚠️ 에러 코드 메시지가 상황과 일치**

### 엣지케이스 확인

- [ ] 경계값 (0, 1, 최대값)
- [ ] Null 필수 필드
- [ ] 중복 데이터
- [ ] 존재하지 않는 ID
- [ ] 권한 없는 접근
- [ ] 잘못된 형식 (이메일, JSON 등)

## 관련 문서

| 파일 | 설명 |
|------|------|
| `../common.md` | 테스트 공통 패턴 |
| `junit-e2e.md` | @WebMvcTest E2E 테스트 |
| `junit-integration.md` | H2/TestContainers 통합 테스트 |
| `junit-unit.md` | Mockito 단위 테스트 |
