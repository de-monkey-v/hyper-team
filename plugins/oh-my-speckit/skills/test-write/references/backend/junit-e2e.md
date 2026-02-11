# JUnit E2E 테스트 가이드

Controller 레이어의 E2E 테스트 가이드. MockMvc를 사용하여 HTTP 요청/응답 전체 흐름을 검증한다.

> 💡 **테스트 로깅 패턴**은 [common.md](../common.md#테스트-로깅-패턴)를 참조하세요.

## 핵심 원칙

| 원칙 | 설명 |
|------|------|
| **HTTP 검증** | 실제 HTTP 요청/응답 전체 흐름 테스트 |
| **격리** | `@WebMvcTest`로 Controller 레이어만 로드 |
| **Mock 의존성** | Service는 `@MockBean`으로 모킹 |

## 의존성

```groovy
// build.gradle
dependencies {
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    // spring-boot-starter-test에 MockMvc, JsonPath 포함
}
```

## @WebMvcTest (권장)

Controller 레이어만 로드하여 빠른 테스트:

```java
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Slf4j
@WebMvcTest(UserController.class)
@DisplayName("UserController E2E 테스트")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

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

    @Nested
    @DisplayName("GET /api/users/{id}")
    class GetUser {

        @Test
        @DisplayName("사용자 조회 성공 - 200 OK")
        void 사용자_조회_성공() throws Exception {
            // Arrange
            Long userId = 1L;
            User user = User.builder()
                .id(userId)
                .name("홍길동")
                .email("hong@example.com")
                .build();
            given(userService.findById(userId)).willReturn(user);
            log.debug("Mock 설정: userId={}", userId);

            // Act & Assert
            MvcResult result = mockMvc.perform(get("/api/users/{id}", userId)
                    .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())  // 요청/응답 출력
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.email").value("hong@example.com"))
                .andReturn();

            log.debug("응답: {}", result.getResponse().getContentAsString());
            then(userService).should().findById(userId);
        }

        @Test
        @DisplayName("존재하지 않는 사용자 - 404 Not Found")
        void 사용자_미존재_404() throws Exception {
            // Arrange
            Long userId = 999L;
            given(userService.findById(userId))
                .willThrow(new UserNotFoundException("사용자를 찾을 수 없습니다: " + userId));

            // Act & Assert
            mockMvc.perform(get("/api/users/{id}", userId))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다: 999"));
        }
    }

    @Nested
    @DisplayName("POST /api/users")
    class CreateUser {

        @Test
        @DisplayName("사용자 생성 성공 - 201 Created")
        void 사용자_생성_성공() throws Exception {
            // Arrange
            CreateUserRequest request = new CreateUserRequest(
                "홍길동", "hong@example.com", "password123"
            );
            User created = User.builder()
                .id(1L)
                .name("홍길동")
                .email("hong@example.com")
                .build();
            given(userService.createUser(any(CreateUserRequest.class))).willReturn(created);
            log.debug("요청: {}", request);

            // Act & Assert
            mockMvc.perform(post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("홍길동"));

            then(userService).should().createUser(any(CreateUserRequest.class));
        }

        @Test
        @DisplayName("잘못된 요청 - 400 Bad Request")
        void 유효성_검증_실패_400() throws Exception {
            // Arrange - 빈 이름
            CreateUserRequest request = new CreateUserRequest(
                "", "hong@example.com", "password123"
            );

            // Act & Assert
            mockMvc.perform(post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
        }

        @Test
        @DisplayName("중복 이메일 - 409 Conflict")
        void 중복_이메일_409() throws Exception {
            // Arrange
            CreateUserRequest request = new CreateUserRequest(
                "홍길동", "exist@example.com", "password123"
            );
            given(userService.createUser(any()))
                .willThrow(new DuplicateEmailException("이미 존재하는 이메일입니다"));

            // Act & Assert
            mockMvc.perform(post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 존재하는 이메일입니다"));
        }
    }

    @Nested
    @DisplayName("PUT /api/users/{id}")
    class UpdateUser {

        @Test
        @DisplayName("사용자 수정 성공 - 200 OK")
        void 사용자_수정_성공() throws Exception {
            // Arrange
            Long userId = 1L;
            UpdateUserRequest request = new UpdateUserRequest("홍길동2", null);
            User updated = User.builder()
                .id(userId)
                .name("홍길동2")
                .email("hong@example.com")
                .build();
            given(userService.updateUser(eq(userId), any())).willReturn(updated);

            // Act & Assert
            mockMvc.perform(put("/api/users/{id}", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("홍길동2"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/users/{id}")
    class DeleteUser {

        @Test
        @DisplayName("사용자 삭제 성공 - 204 No Content")
        void 사용자_삭제_성공() throws Exception {
            // Arrange
            Long userId = 1L;
            willDoNothing().given(userService).deleteUser(userId);

            // Act & Assert
            mockMvc.perform(delete("/api/users/{id}", userId))
                .andDo(print())
                .andExpect(status().isNoContent());

            then(userService).should().deleteUser(userId);
        }
    }
}
```

## 인증 테스트

### @WithMockUser

```java
@WebMvcTest(AdminController.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("관리자 접근 성공")
    void 관리자_접근_성공() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("인증 없이 접근 - 401 Unauthorized")
    void 인증없이_접근_401() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user", roles = {"USER"})
    @DisplayName("권한 없이 접근 - 403 Forbidden")
    void 권한없이_접근_403() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
            .andExpect(status().isForbidden());
    }
}
```

### JWT 토큰 테스트

```java
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)  // Security 설정 로드
class JwtAuthenticationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserService userService;

    @Test
    @DisplayName("유효한 JWT로 인증 성공")
    void JWT_인증_성공() throws Exception {
        // Arrange
        String token = "valid.jwt.token";
        given(jwtTokenProvider.validateToken(token)).willReturn(true);
        given(jwtTokenProvider.getUsername(token)).willReturn("user@example.com");
        given(userService.findByEmail("user@example.com"))
            .willReturn(User.builder().email("user@example.com").build());

        // Act & Assert
        mockMvc.perform(get("/api/users/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("만료된 JWT - 401 Unauthorized")
    void 만료된_JWT_401() throws Exception {
        // Arrange
        String expiredToken = "expired.jwt.token";
        given(jwtTokenProvider.validateToken(expiredToken))
            .willThrow(new ExpiredTokenException("토큰이 만료되었습니다"));

        // Act & Assert
        mockMvc.perform(get("/api/users/me")
                .header("Authorization", "Bearer " + expiredToken))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("토큰이 만료되었습니다"));
    }
}
```

## 파일 업로드 테스트

```java
@Test
@DisplayName("파일 업로드 성공")
void 파일_업로드_성공() throws Exception {
    // Arrange
    MockMultipartFile file = new MockMultipartFile(
        "file",                        // 파라미터명
        "test-image.png",              // 파일명
        MediaType.IMAGE_PNG_VALUE,     // Content-Type
        "image content".getBytes()     // 내용
    );
    given(fileService.upload(any())).willReturn("https://cdn.example.com/test-image.png");

    // Act & Assert
    mockMvc.perform(multipart("/api/files/upload")
            .file(file))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value("https://cdn.example.com/test-image.png"));
}

@Test
@DisplayName("지원하지 않는 파일 형식 - 400 Bad Request")
void 지원하지않는_형식_400() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "malware.exe",
        "application/octet-stream",
        "content".getBytes()
    );

    mockMvc.perform(multipart("/api/files/upload")
            .file(file))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("지원하지 않는 파일 형식입니다"));
}
```

## 페이징/정렬 테스트

```java
@Test
@DisplayName("페이징 조회 - 기본값")
void 페이징_조회_기본값() throws Exception {
    // Arrange
    List<User> users = List.of(
        User.builder().id(1L).name("User1").build(),
        User.builder().id(2L).name("User2").build()
    );
    Page<User> page = new PageImpl<>(users, PageRequest.of(0, 10), 100);
    given(userService.findAll(any(Pageable.class))).willReturn(page);

    // Act & Assert
    mockMvc.perform(get("/api/users"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.totalElements").value(100))
        .andExpect(jsonPath("$.totalPages").value(10));
}

@Test
@DisplayName("페이징 조회 - 커스텀 파라미터")
void 페이징_조회_커스텀() throws Exception {
    mockMvc.perform(get("/api/users")
            .param("page", "2")
            .param("size", "20")
            .param("sort", "name,desc"))
        .andExpect(status().isOk());

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    then(userService).should().findAll(pageableCaptor.capture());

    Pageable captured = pageableCaptor.getValue();
    assertThat(captured.getPageNumber()).isEqualTo(2);
    assertThat(captured.getPageSize()).isEqualTo(20);
    assertThat(captured.getSort().getOrderFor("name").getDirection())
        .isEqualTo(Sort.Direction.DESC);
}
```

## @SpringBootTest + MockMvc

전체 컨텍스트 로드가 필요한 경우:

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FullStackE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("실제 DB와 연동된 E2E 테스트")
    void 실제_DB_연동_E2E() throws Exception {
        // Arrange - 실제 DB에 데이터 저장
        User user = userRepository.save(User.builder()
            .name("홍길동")
            .email("hong@example.com")
            .password("encoded")
            .build());

        // Act & Assert - 실제 Service, Repository 호출
        mockMvc.perform(get("/api/users/{id}", user.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("홍길동"));
    }
}
```

## REST Assured (선택적)

더 가독성 좋은 API 테스트:

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class RestAssuredTest {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void 사용자_조회() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/api/users/1")
        .then()
            .statusCode(200)
            .body("name", equalTo("홍길동"))
            .body("email", containsString("@"));
    }

    @Test
    void 사용자_생성() {
        CreateUserRequest request = new CreateUserRequest("홍길동", "hong@example.com", "password");

        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/users")
        .then()
            .statusCode(201)
            .header("Location", notNullValue())
            .body("id", notNullValue())
            .body("name", equalTo("홍길동"));
    }
}
```

## 응답 검증 패턴

### JsonPath 표현식

```java
// 기본 필드
.andExpect(jsonPath("$.name").value("홍길동"))
.andExpect(jsonPath("$.id").isNumber())
.andExpect(jsonPath("$.email").isString())

// 배열
.andExpect(jsonPath("$.items").isArray())
.andExpect(jsonPath("$.items.length()").value(3))
.andExpect(jsonPath("$.items[0].name").value("첫번째"))

// 존재 여부
.andExpect(jsonPath("$.password").doesNotExist())
.andExpect(jsonPath("$.createdAt").exists())

// 조건
.andExpect(jsonPath("$.count").value(greaterThan(0)))
.andExpect(jsonPath("$.status").value(oneOf("ACTIVE", "INACTIVE")))
```

### 응답 본문 캡처

```java
@Test
void 응답_본문_캡처() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/users/1"))
        .andExpect(status().isOk())
        .andReturn();

    String content = result.getResponse().getContentAsString();
    UserResponse response = objectMapper.readValue(content, UserResponse.class);

    assertThat(response.getName()).isEqualTo("홍길동");
}
```

## 실행 명령어

```bash
# E2E 테스트만 실행
./gradlew test --tests "*ControllerTest"

# 또는 패키지 기준
./gradlew test --tests "com.example.controller.*"

# Maven
mvn test -Dtest="*ControllerTest"
```

## 체크리스트

- [ ] `@WebMvcTest` 사용 (Controller만 테스트)
- [ ] `@MockBean`으로 Service 모킹
- [ ] 성공/실패 케이스 모두 테스트
- [ ] HTTP 상태 코드 검증 (200, 201, 400, 401, 404, 500 등)
- [ ] 응답 헤더 검증 (Location, Content-Type 등)
- [ ] 인증/권한 테스트 (`@WithMockUser`, JWT)
- [ ] 유효성 검증 실패 테스트
- [ ] `andDo(print())`로 요청/응답 로깅
- [ ] Logger로 테스트 실행 로그 출력
