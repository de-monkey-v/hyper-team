# JUnit 통합 테스트 가이드

Service, Repository 레이어의 통합 테스트 가이드. 실제 DB와 연동하여 전체 흐름을 검증한다.

> 💡 **테스트 로깅 패턴**은 [common.md](../common.md#테스트-로깅-패턴)를 참조하세요.

## 핵심 원칙

| 원칙 | 설명 |
|------|------|
| **실제 연동** | 실제 DB, 실제 트랜잭션으로 검증 |
| **데이터 격리** | `@Transactional` 또는 테스트별 정리 |
| **환경 동일성** | 프로덕션과 동일한 환경 (TestContainers) |

## DB 전략 선택

| 전략 | 장점 | 단점 | 사용 시기 |
|------|------|------|----------|
| **H2 In-Memory** | 빠름, 설정 간단 | DB 특화 기능 불가 | 표준 SQL만 사용 |
| **TestContainers** | 프로덕션 동일 | 느림 (컨테이너 시작) | DB 특화 기능 필요 |

## H2 In-Memory 통합 테스트

### 설정

```yaml
# src/test/resources/application-test.yml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
    properties:
      hibernate:
        format_sql: true
  h2:
    console:
      enabled: true

logging:
  level:
    org.springframework.web: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

### 기본 구조

```java
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@Transactional  // 각 테스트 후 자동 롤백
@DisplayName("UserService 통합 테스트")
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

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
    @DisplayName("사용자 생성 통합 테스트")
    class CreateUser {

        @Test
        @DisplayName("사용자 생성 후 DB 저장 확인")
        void 사용자_생성_DB_저장() {
            // Arrange
            CreateUserRequest request = new CreateUserRequest(
                "홍길동", "hong@example.com", "password123"
            );
            log.debug("요청: {}", request);

            // Act
            User created = userService.createUser(request);
            log.debug("생성된 사용자: id={}", created.getId());

            // Assert - Service 반환값 검증
            assertThat(created.getId()).isNotNull();
            assertThat(created.getName()).isEqualTo("홍길동");

            // Assert - DB 직접 조회 검증
            User found = userRepository.findById(created.getId()).orElseThrow();
            assertThat(found.getEmail()).isEqualTo("hong@example.com");
            assertThat(found.getPassword()).isNotEqualTo("password123");  // 암호화 확인
            log.debug("DB 조회 결과: {}", found);
        }

        @Test
        @DisplayName("중복 이메일로 생성 시 예외")
        void 중복_이메일_예외() {
            // Arrange - 먼저 사용자 생성
            userRepository.save(User.builder()
                .name("기존사용자")
                .email("exist@example.com")
                .password("encoded")
                .build());

            CreateUserRequest request = new CreateUserRequest(
                "새사용자", "exist@example.com", "password"
            );

            // Act & Assert
            assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(DuplicateEmailException.class);
        }
    }

    @Nested
    @DisplayName("사용자 조회 통합 테스트")
    class FindUser {

        @Test
        @DisplayName("ID로 사용자 조회")
        void ID로_사용자_조회() {
            // Arrange
            User saved = userRepository.save(User.builder()
                .name("홍길동")
                .email("hong@example.com")
                .password("encoded")
                .build());

            // Act
            User found = userService.findById(saved.getId());

            // Assert
            assertThat(found.getName()).isEqualTo("홍길동");
        }

        @Test
        @DisplayName("이메일로 사용자 조회")
        void 이메일로_사용자_조회() {
            // Arrange
            userRepository.save(User.builder()
                .name("홍길동")
                .email("hong@example.com")
                .password("encoded")
                .build());

            // Act
            User found = userService.findByEmail("hong@example.com");

            // Assert
            assertThat(found.getName()).isEqualTo("홍길동");
        }
    }
}
```

## TestContainers 통합 테스트

### 의존성

```groovy
// build.gradle
dependencies {
    testImplementation 'org.testcontainers:junit-jupiter'
    testImplementation 'org.testcontainers:postgresql'  // 또는 mysql
}
```

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

### 기본 구조

```java
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Slf4j
@SpringBootTest
@Testcontainers
@DisplayName("UserService TestContainers 통합 테스트")
class UserServiceContainerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    private long testStartTime;

    @BeforeEach
    void setUp(TestInfo testInfo) {
        testStartTime = System.currentTimeMillis();
        log.info("=== Test Started: {} (PostgreSQL Container) ===",
                 testInfo.getDisplayName());
        userRepository.deleteAll();  // 데이터 초기화
    }

    @AfterEach
    void tearDown(TestInfo testInfo) {
        long duration = System.currentTimeMillis() - testStartTime;
        log.info("=== Test Completed: {} ({}ms) ===",
                 testInfo.getDisplayName(), duration);
    }

    @Test
    @DisplayName("PostgreSQL에서 사용자 CRUD")
    void PostgreSQL_CRUD_테스트() {
        // Create
        User created = userService.createUser(
            new CreateUserRequest("홍길동", "hong@example.com", "password")
        );
        assertThat(created.getId()).isNotNull();
        log.debug("Created: id={}", created.getId());

        // Read
        User found = userService.findById(created.getId());
        assertThat(found.getName()).isEqualTo("홍길동");

        // Update
        userService.updateUser(created.getId(),
            new UpdateUserRequest("홍길동2", null));
        User updated = userService.findById(created.getId());
        assertThat(updated.getName()).isEqualTo("홍길동2");

        // Delete
        userService.deleteUser(created.getId());
        assertThat(userRepository.findById(created.getId())).isEmpty();
    }

    @Test
    @DisplayName("PostgreSQL 특화 기능 테스트 - JSONB")
    void PostgreSQL_JSONB_테스트() {
        // PostgreSQL의 JSONB 컬럼 테스트
        User user = User.builder()
            .name("홍길동")
            .email("hong@example.com")
            .metadata(Map.of("role", "admin", "department", "IT"))
            .build();
        userRepository.save(user);

        // JSONB 쿼리 테스트
        List<User> admins = userRepository.findByMetadataRole("admin");
        assertThat(admins).hasSize(1);
    }
}
```

### 공유 컨테이너 (Singleton Pattern)

여러 테스트 클래스에서 컨테이너 재사용:

```java
// src/test/java/com/example/support/AbstractIntegrationTest.java
@SpringBootTest
@Testcontainers
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
        postgres.start();  // 한 번만 시작
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    protected long testStartTime;

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
}

// 테스트 클래스
@Slf4j
class UserServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserService userService;

    @Test
    void 테스트() {
        // postgres 컨테이너 공유
    }
}

class OrderServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Test
    void 테스트() {
        // 같은 postgres 컨테이너 사용
    }
}
```

## 테스트 데이터 관리

### @Sql 어노테이션

```java
@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/sql/init-users.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class UserQueryTest {

    @Test
    void 초기_데이터로_테스트() {
        // init-users.sql로 초기화된 데이터 사용
    }
}
```

```sql
-- src/test/resources/sql/init-users.sql
INSERT INTO users (id, name, email, password, status, created_at)
VALUES
    (1, '홍길동', 'hong@example.com', 'encoded', 'ACTIVE', NOW()),
    (2, '김철수', 'kim@example.com', 'encoded', 'ACTIVE', NOW()),
    (3, '이영희', 'lee@example.com', 'encoded', 'INACTIVE', NOW());

-- src/test/resources/sql/cleanup.sql
DELETE FROM users;
```

### TestEntityManager

```java
@DataJpaTest
class ComplexQueryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Test
    void 복잡한_조인_쿼리_테스트() {
        // Arrange
        Department dept = new Department("IT");
        entityManager.persist(dept);

        User user1 = User.builder().name("홍길동").department(dept).build();
        User user2 = User.builder().name("김철수").department(dept).build();
        entityManager.persist(user1);
        entityManager.persist(user2);

        entityManager.flush();
        entityManager.clear();  // 1차 캐시 클리어

        // Act
        List<User> users = userRepository.findByDepartmentName("IT");

        // Assert
        assertThat(users).hasSize(2);
    }
}
```

### Fixture Factory

```java
// src/test/java/com/example/support/TestDataFactory.java
@Component
public class TestDataFactory {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Transactional
    public User createUser(String name, String email) {
        return userRepository.save(User.builder()
            .name(name)
            .email(email)
            .password(passwordEncoder.encode("password"))
            .status(UserStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .build());
    }

    @Transactional
    public List<User> createUsers(int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> createUser("User" + i, "user" + i + "@example.com"))
            .toList();
    }
}

// 테스트에서 사용
@SpringBootTest
class UserListTest {

    @Autowired
    private TestDataFactory testDataFactory;

    @Test
    void 페이징_테스트() {
        // Arrange
        testDataFactory.createUsers(100);

        // Act
        Page<User> page = userService.findAll(PageRequest.of(0, 10));

        // Assert
        assertThat(page.getTotalElements()).isEqualTo(100);
        assertThat(page.getContent()).hasSize(10);
    }
}
```

## 트랜잭션 테스트

### @Transactional 동작

```java
@SpringBootTest
@Transactional  // 테스트 후 자동 롤백
class TransactionTest {

    @Test
    void 트랜잭션_롤백_테스트() {
        // 이 테스트에서 생성된 데이터는 롤백됨
        userService.createUser(request);

        // 다른 테스트에 영향 없음
    }
}
```

### 롤백 방지

```java
@Test
@Rollback(false)  // 롤백하지 않음 (DB에 데이터 남김)
void 실제_데이터_확인_필요시() {
    // 디버깅 목적으로 데이터 유지
}

@Test
@Commit  // @Rollback(false)와 동일
void 커밋_필요시() {
    // 트랜잭션 커밋
}
```

### 전파 속성 테스트

```java
@Test
void 트랜잭션_전파_테스트() {
    // REQUIRES_NEW가 있는 경우 별도 트랜잭션
    assertThatThrownBy(() -> userService.createWithAudit(request))
        .isInstanceOf(RuntimeException.class);

    // 감사 로그는 별도 트랜잭션이므로 저장됨
    assertThat(auditRepository.count()).isEqualTo(1);
}
```

## 비동기 테스트

```java
@SpringBootTest
class AsyncServiceTest {

    @Autowired
    private AsyncService asyncService;

    @Test
    @Timeout(5)  // 5초 내 완료
    void 비동기_작업_완료_대기() throws Exception {
        CompletableFuture<Result> future = asyncService.processAsync(request);

        Result result = future.get(3, TimeUnit.SECONDS);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void Awaitility_사용() {
        asyncService.startBackgroundJob();

        await()
            .atMost(5, TimeUnit.SECONDS)
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .until(() -> jobRepository.isCompleted());
    }
}
```

## 성능 테스트

```java
@Test
@DisplayName("대량 데이터 조회 성능")
void 대량_데이터_조회_성능() {
    // Arrange
    testDataFactory.createUsers(1000);

    // Act
    long startTime = System.currentTimeMillis();
    List<User> users = userService.findActiveUsers();
    long duration = System.currentTimeMillis() - startTime;

    log.info("조회 시간: {}ms, 결과: {}건", duration, users.size());

    // Assert
    assertThat(duration).isLessThan(1000);  // 1초 이내
    assertThat(users).hasSizeGreaterThan(500);
}
```

## 실행 명령어

```bash
# 통합 테스트만 실행
./gradlew test --tests "*IntegrationTest"

# TestContainers 테스트만
./gradlew test --tests "*ContainerTest"

# 특정 프로파일로 실행
./gradlew test -Dspring.profiles.active=test

# Maven
mvn test -Dtest="*IntegrationTest"
```

## 체크리스트

### H2 테스트
- [ ] `application-test.yml`에 H2 설정
- [ ] `@ActiveProfiles("test")` 사용
- [ ] `@Transactional`로 데이터 격리

### TestContainers 테스트
- [ ] 의존성 추가 (`testcontainers:junit-jupiter`, `testcontainers:postgresql`)
- [ ] `@Testcontainers` + `@Container` 사용
- [ ] `@DynamicPropertySource`로 속성 주입
- [ ] 공유 컨테이너 패턴 적용 (선택)

### 공통
- [ ] Logger로 테스트 실행 로그 출력
- [ ] 테스트 데이터 Fixture/Factory 사용
- [ ] CI/CD Docker 지원 확인 (TestContainers)
