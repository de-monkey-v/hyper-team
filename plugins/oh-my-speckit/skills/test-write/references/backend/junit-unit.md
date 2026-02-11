# JUnit 단위 테스트 가이드

Service, Repository 레이어의 단위 테스트 작성 가이드. Mockito를 사용하여 의존성을 격리한다.

> 💡 **테스트 로깅 패턴**은 [common.md](../common.md#테스트-로깅-패턴)를 참조하세요.

## 핵심 원칙

| 원칙 | 설명 |
|------|------|
| **격리** | 테스트 대상만 실제 실행, 나머지는 Mock |
| **빠른 실행** | Spring Context 로드 없음 |
| **단일 책임** | 하나의 메서드/기능만 검증 |

## 의존성

```groovy
// build.gradle
dependencies {
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    // spring-boot-starter-test에 Mockito, JUnit5 포함
}
```

## Service 단위 테스트

### 기본 구조

```java
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import lombok.extern.slf4j.Slf4j;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.BDDMockito.*;

@Slf4j
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 단위 테스트")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
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
    @DisplayName("사용자 조회")
    class FindUser {

        @Test
        @DisplayName("ID로 사용자 조회 성공")
        void 사용자_조회_성공() {
            // Arrange
            Long userId = 1L;
            User user = new User(userId, "홍길동", "hong@example.com");
            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            log.debug("Mock 설정 완료: userId={}", userId);

            // Act
            User result = userService.findById(userId);
            log.debug("조회 결과: {}", result);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("홍길동");
            then(userRepository).should().findById(userId);
        }

        @Test
        @DisplayName("존재하지 않는 사용자 조회 시 예외 발생")
        void 사용자_조회_실패() {
            // Arrange
            Long userId = 999L;
            given(userRepository.findById(userId)).willReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> userService.findById(userId))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("사용자 생성")
    class CreateUser {

        @Test
        @DisplayName("유효한 정보로 사용자 생성 성공")
        void 사용자_생성_성공() {
            // Arrange
            CreateUserRequest request = new CreateUserRequest("홍길동", "hong@example.com", "password123");
            String encodedPassword = "encodedPassword";
            User savedUser = new User(1L, "홍길동", "hong@example.com");

            given(userRepository.existsByEmail(request.getEmail())).willReturn(false);
            given(passwordEncoder.encode(request.getPassword())).willReturn(encodedPassword);
            given(userRepository.save(any(User.class))).willReturn(savedUser);

            // Act
            User result = userService.createUser(request);

            // Assert
            assertThat(result.getId()).isEqualTo(1L);
            then(userRepository).should().existsByEmail(request.getEmail());
            then(passwordEncoder).should().encode(request.getPassword());
            then(userRepository).should().save(any(User.class));
        }

        @Test
        @DisplayName("중복 이메일로 생성 시 예외 발생")
        void 중복_이메일_예외() {
            // Arrange
            CreateUserRequest request = new CreateUserRequest("홍길동", "exist@example.com", "password");
            given(userRepository.existsByEmail(request.getEmail())).willReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(DuplicateEmailException.class);

            then(userRepository).should(never()).save(any());
        }
    }
}
```

## Mockito 핵심 패턴

### 1. BDDMockito (권장)

```java
import static org.mockito.BDDMockito.*;

// given - 조건 설정
given(repository.findById(1L)).willReturn(Optional.of(user));

// when - 실행 (테스트 코드에서 직접 호출)
User result = service.findById(1L);

// then - 검증
then(repository).should().findById(1L);
then(repository).should(times(1)).findById(anyLong());
then(repository).should(never()).delete(any());
```

### 2. ArgumentCaptor

메서드에 전달된 인자를 캡처하여 검증:

```java
@Test
void 저장되는_엔티티_검증() {
    // Arrange
    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    CreateUserRequest request = new CreateUserRequest("홍길동", "hong@example.com", "password");
    given(passwordEncoder.encode(anyString())).willReturn("encoded");
    given(userRepository.save(any())).willReturn(new User(1L, "홍길동", "hong@example.com"));

    // Act
    userService.createUser(request);

    // Assert
    then(userRepository).should().save(userCaptor.capture());
    User capturedUser = userCaptor.getValue();

    assertThat(capturedUser.getName()).isEqualTo("홍길동");
    assertThat(capturedUser.getEmail()).isEqualTo("hong@example.com");
    assertThat(capturedUser.getPassword()).isEqualTo("encoded");  // 암호화된 비밀번호
}
```

### 3. Mock vs Spy

```java
@Mock
private UserRepository userRepository;  // 모든 메서드가 Mock

@Spy
private UserValidator userValidator;    // 실제 메서드 호출, 일부만 Mock

@Test
void spy_사용_예시() {
    // Spy는 실제 메서드 호출
    boolean isValid = userValidator.isValidEmail("test@example.com");  // 실제 로직 실행

    // 특정 메서드만 Stub
    doReturn(false).when(userValidator).isValidEmail("invalid");
}
```

### 4. void 메서드 Stub

```java
@Test
void void_메서드_예외_발생() {
    // void 메서드에서 예외 발생
    doThrow(new RuntimeException("에러"))
        .when(emailService).sendEmail(anyString(), anyString());

    assertThatThrownBy(() -> userService.sendWelcomeEmail("test@example.com"))
        .isInstanceOf(RuntimeException.class);
}

@Test
void void_메서드_아무것도_안함() {
    // void 메서드가 아무것도 안 하도록
    doNothing().when(auditService).log(any());

    userService.createUser(request);

    then(auditService).should().log(any());
}
```

### 5. 연속 호출 Stub

```java
@Test
void 연속_호출_다른_결과() {
    given(repository.findById(1L))
        .willReturn(Optional.empty())   // 첫 번째 호출
        .willReturn(Optional.of(user)); // 두 번째 호출

    assertThat(service.findById(1L)).isNull();      // 첫 번째
    assertThat(service.findById(1L)).isNotNull();   // 두 번째
}
```

## Repository 단위 테스트 (@DataJpaTest)

JPA Repository만 로드하여 빠르게 테스트:

```java
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("UserRepository 단위 테스트")
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("이메일로 사용자 조회")
    void 이메일로_사용자_조회() {
        // Arrange
        User user = User.builder()
            .name("홍길동")
            .email("hong@example.com")
            .password("encoded")
            .build();
        entityManager.persist(user);
        entityManager.flush();
        entityManager.clear();  // 1차 캐시 클리어

        // Act
        Optional<User> found = userRepository.findByEmail("hong@example.com");

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("존재하지 않는 이메일 조회 시 빈 Optional")
    void 존재하지_않는_이메일_조회() {
        Optional<User> found = userRepository.findByEmail("notexist@example.com");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("이메일 중복 확인")
    void 이메일_중복_확인() {
        // Arrange
        User user = User.builder()
            .name("홍길동")
            .email("exist@example.com")
            .password("encoded")
            .build();
        entityManager.persist(user);
        entityManager.flush();

        // Act & Assert
        assertThat(userRepository.existsByEmail("exist@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("notexist@example.com")).isFalse();
    }

    @Test
    @DisplayName("커스텀 쿼리 메서드 테스트")
    void 활성_사용자_조회() {
        // Arrange
        User activeUser = User.builder()
            .name("활성")
            .email("active@example.com")
            .status(UserStatus.ACTIVE)
            .build();
        User inactiveUser = User.builder()
            .name("비활성")
            .email("inactive@example.com")
            .status(UserStatus.INACTIVE)
            .build();
        entityManager.persist(activeUser);
        entityManager.persist(inactiveUser);
        entityManager.flush();

        // Act
        List<User> activeUsers = userRepository.findByStatus(UserStatus.ACTIVE);

        // Assert
        assertThat(activeUsers).hasSize(1);
        assertThat(activeUsers.get(0).getName()).isEqualTo("활성");
    }
}
```

## 테스트 데이터 빌더

### Fixture 클래스

```java
public class UserFixture {

    public static User createUser() {
        return User.builder()
            .id(1L)
            .name("홍길동")
            .email("hong@example.com")
            .password("encodedPassword")
            .status(UserStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .build();
    }

    public static User createUser(String name, String email) {
        return User.builder()
            .name(name)
            .email(email)
            .password("encodedPassword")
            .status(UserStatus.ACTIVE)
            .build();
    }

    public static CreateUserRequest createRequest() {
        return new CreateUserRequest("홍길동", "hong@example.com", "password123");
    }
}
```

### 테스트에서 사용

```java
@Test
void 사용자_조회() {
    // Arrange
    User user = UserFixture.createUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    // Act & Assert
    ...
}
```

## 예외 테스트

```java
@Test
@DisplayName("null 인자 시 IllegalArgumentException")
void null_인자_예외() {
    assertThatThrownBy(() -> userService.findById(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ID는 null일 수 없습니다");
}

@Test
@DisplayName("예외 메시지에 특정 값 포함")
void 예외_메시지_검증() {
    assertThatThrownBy(() -> userService.findById(999L))
        .isInstanceOf(UserNotFoundException.class)
        .hasMessageContaining("999");
}

@Test
@DisplayName("예외가 발생하지 않음을 검증")
void 예외_미발생() {
    assertThatCode(() -> userService.findById(1L))
        .doesNotThrowAnyException();
}
```

## 네이밍 컨벤션

### 메서드명 (한글 권장)

```java
// 패턴: 동작_조건 또는 상황_결과
@Test void 사용자_조회_성공() {}
@Test void 존재하지_않는_사용자_조회시_예외() {}
@Test void 중복_이메일로_가입시_DuplicateEmailException_발생() {}
```

### @DisplayName 활용

```java
@Nested
@DisplayName("사용자 생성")
class CreateUser {

    @Test
    @DisplayName("유효한 정보로 생성 성공")
    void success() {}

    @Test
    @DisplayName("중복 이메일 시 DuplicateEmailException")
    void duplicateEmail() {}
}
```

## 실행 명령어

```bash
# 전체 단위 테스트
./gradlew test --tests "*Test"

# 특정 클래스
./gradlew test --tests "UserServiceTest"

# 특정 메서드
./gradlew test --tests "UserServiceTest.사용자_조회_성공"

# Maven
mvn test -Dtest="UserServiceTest"
```

## 체크리스트

- [ ] `@ExtendWith(MockitoExtension.class)` 사용
- [ ] `@Mock`과 `@InjectMocks` 올바르게 설정
- [ ] BDDMockito (given/then) 스타일 사용
- [ ] 한글 테스트명 + `@DisplayName` 활용
- [ ] `@Nested`로 테스트 그룹화
- [ ] ArgumentCaptor로 인자 검증 (필요시)
- [ ] Logger로 테스트 실행 로그 출력
- [ ] Fixture 클래스로 테스트 데이터 관리
