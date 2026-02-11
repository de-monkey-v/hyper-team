package com.example.service;

import com.example.domain.User;
import com.example.domain.UserStatus;
import com.example.dto.CreateUserRequest;
import com.example.dto.UpdateUserRequest;
import com.example.exception.DuplicateEmailException;
import com.example.exception.UserNotFoundException;
import com.example.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

/**
 * UserService 단위 테스트 예제
 *
 * - @ExtendWith(MockitoExtension.class): Spring Context 없이 Mockito만 사용
 * - @Mock: 의존성을 Mock으로 대체
 * - @InjectMocks: Mock을 주입받는 테스트 대상
 * - BDDMockito: given/when/then 스타일
 * - @Nested: 테스트 그룹화
 * - @DisplayName: 한글 테스트명
 * - Logger: 테스트 실행 로그
 */
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

    // 테스트 데이터
    private User testUser;
    private CreateUserRequest createRequest;

    // 테스트 시간 측정
    private long testStartTime;

    @BeforeEach
    void setUp(TestInfo testInfo) {
        testStartTime = System.currentTimeMillis();
        log.info("=== Test Started: {} ===", testInfo.getDisplayName());

        // 공통 테스트 데이터 초기화
        testUser = User.builder()
            .id(1L)
            .name("홍길동")
            .email("hong@example.com")
            .password("encodedPassword")
            .status(UserStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .build();

        createRequest = new CreateUserRequest("홍길동", "hong@example.com", "password123");
    }

    @AfterEach
    void tearDown(TestInfo testInfo) {
        long duration = System.currentTimeMillis() - testStartTime;
        log.info("=== Test Completed: {} ({}ms) ===", testInfo.getDisplayName(), duration);
    }

    // ========================================
    // 사용자 조회 테스트
    // ========================================

    @Nested
    @DisplayName("사용자 조회")
    class FindUser {

        @Test
        @DisplayName("ID로 사용자 조회 성공")
        void 사용자_조회_성공() {
            // Arrange
            Long userId = 1L;
            given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
            log.debug("Mock 설정 완료: userId={}", userId);

            // Act
            User result = userService.findById(userId);
            log.debug("조회 결과: {}", result);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(userId);
            assertThat(result.getName()).isEqualTo("홍길동");
            assertThat(result.getEmail()).isEqualTo("hong@example.com");

            // Mock 호출 검증
            then(userRepository).should().findById(userId);
            then(userRepository).should(times(1)).findById(anyLong());
        }

        @Test
        @DisplayName("존재하지 않는 사용자 조회 시 UserNotFoundException 발생")
        void 사용자_조회_실패_UserNotFoundException() {
            // Arrange
            Long userId = 999L;
            given(userRepository.findById(userId)).willReturn(Optional.empty());
            log.debug("존재하지 않는 userId={} 조회 시도", userId);

            // Act & Assert
            assertThatThrownBy(() -> userService.findById(userId))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다")
                .hasMessageContaining("999");

            then(userRepository).should().findById(userId);
        }

        @Test
        @DisplayName("null ID로 조회 시 IllegalArgumentException 발생")
        void null_ID_조회시_IllegalArgumentException() {
            // Act & Assert
            assertThatThrownBy(() -> userService.findById(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ID는 null일 수 없습니다");

            // Repository 호출되지 않음
            then(userRepository).should(never()).findById(any());
        }

        @Test
        @DisplayName("이메일로 사용자 조회 성공")
        void 이메일로_사용자_조회_성공() {
            // Arrange
            String email = "hong@example.com";
            given(userRepository.findByEmail(email)).willReturn(Optional.of(testUser));

            // Act
            User result = userService.findByEmail(email);

            // Assert
            assertThat(result.getEmail()).isEqualTo(email);
            then(userRepository).should().findByEmail(email);
        }
    }

    // ========================================
    // 사용자 생성 테스트
    // ========================================

    @Nested
    @DisplayName("사용자 생성")
    class CreateUser {

        @Test
        @DisplayName("유효한 정보로 사용자 생성 성공")
        void 사용자_생성_성공() {
            // Arrange
            String encodedPassword = "encodedPassword123";
            given(userRepository.existsByEmail(createRequest.getEmail())).willReturn(false);
            given(passwordEncoder.encode(createRequest.getPassword())).willReturn(encodedPassword);
            given(userRepository.save(any(User.class))).willReturn(testUser);
            log.debug("요청: {}", createRequest);

            // Act
            User result = userService.createUser(createRequest);
            log.debug("생성된 사용자: {}", result);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("홍길동");

            // 호출 순서 및 횟수 검증
            then(userRepository).should().existsByEmail(createRequest.getEmail());
            then(passwordEncoder).should().encode(createRequest.getPassword());
            then(userRepository).should().save(any(User.class));
        }

        @Test
        @DisplayName("저장되는 User 엔티티 검증 (ArgumentCaptor)")
        void 저장되는_엔티티_검증() {
            // Arrange
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            String encodedPassword = "encodedPassword";

            given(userRepository.existsByEmail(anyString())).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn(encodedPassword);
            given(userRepository.save(any(User.class))).willReturn(testUser);

            // Act
            userService.createUser(createRequest);

            // Assert - 저장되는 User 객체 검증
            then(userRepository).should().save(userCaptor.capture());
            User capturedUser = userCaptor.getValue();

            assertThat(capturedUser.getName()).isEqualTo("홍길동");
            assertThat(capturedUser.getEmail()).isEqualTo("hong@example.com");
            assertThat(capturedUser.getPassword()).isEqualTo(encodedPassword);  // 암호화된 비밀번호
            assertThat(capturedUser.getPassword()).isNotEqualTo("password123"); // 평문 아님
            assertThat(capturedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
            log.debug("캡처된 User: {}", capturedUser);
        }

        @Test
        @DisplayName("중복 이메일로 생성 시 DuplicateEmailException 발생")
        void 중복_이메일_예외() {
            // Arrange
            CreateUserRequest request = new CreateUserRequest("새사용자", "exist@example.com", "password");
            given(userRepository.existsByEmail("exist@example.com")).willReturn(true);
            log.debug("중복 이메일 테스트: {}", request.getEmail());

            // Act & Assert
            assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessageContaining("이미 존재하는 이메일");

            // 중복 시 저장되지 않음
            then(userRepository).should(never()).save(any());
            then(passwordEncoder).should(never()).encode(any());
        }

        @Test
        @DisplayName("빈 이름으로 생성 시 IllegalArgumentException 발생")
        void 빈_이름_예외() {
            // Arrange
            CreateUserRequest request = new CreateUserRequest("", "test@example.com", "password");

            // Act & Assert
            assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이름은 필수입니다");
        }
    }

    // ========================================
    // 사용자 수정 테스트
    // ========================================

    @Nested
    @DisplayName("사용자 수정")
    class UpdateUser {

        @Test
        @DisplayName("이름 수정 성공")
        void 이름_수정_성공() {
            // Arrange
            Long userId = 1L;
            UpdateUserRequest request = new UpdateUserRequest("홍길동2", null);
            given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
            log.debug("수정 요청: userId={}, newName={}", userId, request.getName());

            // Act
            User result = userService.updateUser(userId, request);

            // Assert
            assertThat(result.getName()).isEqualTo("홍길동2");
            then(userRepository).should().findById(userId);
        }

        @Test
        @DisplayName("존재하지 않는 사용자 수정 시 예외")
        void 존재하지_않는_사용자_수정_예외() {
            // Arrange
            Long userId = 999L;
            UpdateUserRequest request = new UpdateUserRequest("새이름", null);
            given(userRepository.findById(userId)).willReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> userService.updateUser(userId, request))
                .isInstanceOf(UserNotFoundException.class);
        }
    }

    // ========================================
    // 사용자 삭제 테스트
    // ========================================

    @Nested
    @DisplayName("사용자 삭제")
    class DeleteUser {

        @Test
        @DisplayName("사용자 삭제 성공")
        void 사용자_삭제_성공() {
            // Arrange
            Long userId = 1L;
            given(userRepository.existsById(userId)).willReturn(true);
            willDoNothing().given(userRepository).deleteById(userId);

            // Act
            userService.deleteUser(userId);

            // Assert
            then(userRepository).should().existsById(userId);
            then(userRepository).should().deleteById(userId);
        }

        @Test
        @DisplayName("존재하지 않는 사용자 삭제 시 예외")
        void 존재하지_않는_사용자_삭제_예외() {
            // Arrange
            Long userId = 999L;
            given(userRepository.existsById(userId)).willReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> userService.deleteUser(userId))
                .isInstanceOf(UserNotFoundException.class);

            then(userRepository).should(never()).deleteById(any());
        }
    }

    // ========================================
    // 목록 조회 테스트
    // ========================================

    @Nested
    @DisplayName("사용자 목록 조회")
    class FindUsers {

        @Test
        @DisplayName("활성 사용자 목록 조회")
        void 활성_사용자_목록_조회() {
            // Arrange
            List<User> activeUsers = List.of(
                testUser,
                User.builder().id(2L).name("김철수").status(UserStatus.ACTIVE).build()
            );
            given(userRepository.findByStatus(UserStatus.ACTIVE)).willReturn(activeUsers);

            // Act
            List<User> result = userService.findActiveUsers();

            // Assert
            assertThat(result).hasSize(2);
            assertThat(result).extracting(User::getStatus)
                .containsOnly(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("빈 목록 반환")
        void 빈_목록_반환() {
            // Arrange
            given(userRepository.findByStatus(UserStatus.ACTIVE)).willReturn(List.of());

            // Act
            List<User> result = userService.findActiveUsers();

            // Assert
            assertThat(result).isEmpty();
        }
    }
}
