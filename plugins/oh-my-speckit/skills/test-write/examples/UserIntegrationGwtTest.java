package com.example.integration;

import com.example.domain.User;
import com.example.dto.CreateUserRequest;
import com.example.dto.UpdateUserRequest;
import com.example.repository.UserRepository;
import com.example.support.BaseIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Given-When-Then 패턴 통합 테스트 예제
 *
 * 핵심 패턴:
 * 1. BaseIntegrationTest 상속 (공통 설정, 헬퍼 메서드)
 * 2. Given-When-Then 주석으로 구조 명확화
 * 3. MockMvc 호출 후 flushAndClear() → Repository로 Entity 검증
 * 4. @Nested로 기능별 그룹화
 * 5. @DisplayName으로 한글 테스트명
 * 6. log.debug()로 각 단계 로깅
 */
@DisplayName("사용자 API 통합 테스트")
class UserIntegrationGwtTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    // ========================================
    // POST /api/users - 사용자 생성
    // ========================================

    @Nested
    @DisplayName("POST /api/users")
    class CreateUserTests {

        @Test
        @WithMockUser
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
                .andExpect(jsonPath("$.password").doesNotExist())  // 응답에 비밀번호 미포함
                .andReturn();
            log.debug("When: POST /api/users 호출 완료");

            // Then - DB 상태 검증
            Long userId = extractIdFromResponse(result);
            flushAndClear();  // ⚠️ 영속성 컨텍스트 초기화

            User savedUser = userRepository.findById(userId)
                .orElseThrow(() -> new AssertionError("사용자가 DB에 저장되지 않았습니다"));

            assertThat(savedUser.getName()).isEqualTo("홍길동");
            assertThat(savedUser.getEmail()).isEqualTo("hong@example.com");
            assertThat(savedUser.getPassword()).isNotEqualTo("password123");  // 암호화됨
            assertThat(savedUser.getPassword()).startsWith("$2");  // BCrypt 형식
            assertThat(savedUser.getCreatedAt()).isNotNull();
            assertThat(savedUser.getUpdatedAt()).isNotNull();
            log.debug("Then: DB에서 사용자 확인 완료 - id={}", savedUser.getId());
        }

        @Test
        @WithMockUser
        @DisplayName("중복 이메일로 가입 시도하면 409 에러를 반환한다")
        void givenDuplicateEmail_whenCreateUser_thenReturnConflict() throws Exception {
            // Given
            userRepository.save(User.builder()
                .name("기존사용자")
                .email("exist@example.com")
                .password("encoded")
                .build());
            log.debug("Given: 기존 사용자 존재 - email=exist@example.com");

            CreateUserRequest request = new CreateUserRequest(
                "새사용자",
                "exist@example.com",  // 중복 이메일
                "password"
            );

            // When & Then
            performPost("/api/users", request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_EMAIL"))
                .andExpect(jsonPath("$.message").exists());
            log.debug("When: 중복 이메일로 POST /api/users 호출 → 409 확인");

            // DB 상태: 새 사용자가 생성되지 않음
            flushAndClear();
            long userCount = userRepository.count();
            assertThat(userCount).isEqualTo(1);  // 기존 1명만 존재
            log.debug("Then: DB에 기존 사용자 1명만 존재 확인");
        }

        @Test
        @WithMockUser
        @DisplayName("필수 필드가 비어있으면 400 에러를 반환한다")
        void givenEmptyRequiredField_whenCreateUser_thenReturnBadRequest() throws Exception {
            // Given
            CreateUserRequest request = new CreateUserRequest(
                "",  // 빈 이름 (유효성 검증 실패)
                "test@example.com",
                "password"
            );
            log.debug("Given: 유효하지 않은 요청 - 빈 이름");

            // When & Then
            performPost("/api/users", request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists());
            log.debug("When: POST /api/users 호출 → 400 확인");

            // DB 상태: 사용자가 생성되지 않음
            flushAndClear();
            assertThat(userRepository.count()).isEqualTo(0);
            log.debug("Then: DB에 사용자 없음 확인");
        }
    }

    // ========================================
    // PUT /api/users/{id} - 사용자 수정
    // ========================================

    @Nested
    @DisplayName("PUT /api/users/{id}")
    class UpdateUserTests {

        @Test
        @WithMockUser
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
                .andExpect(jsonPath("$.id").value(userId))
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

        @Test
        @WithMockUser
        @DisplayName("존재하지 않는 사용자 수정 시 404 에러를 반환한다")
        void givenNonExistentUser_whenUpdate_thenReturn404() throws Exception {
            // Given
            Long nonExistentId = 99999L;
            UpdateUserRequest request = new UpdateUserRequest("새이름", null);
            log.debug("Given: 존재하지 않는 사용자 ID - {}", nonExistentId);

            // When & Then
            performPut("/api/users/{id}", request, nonExistentId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"));
            log.debug("When: PUT /api/users/{} 호출 → 404 확인", nonExistentId);
        }
    }

    // ========================================
    // DELETE /api/users/{id} - 사용자 삭제
    // ========================================

    @Nested
    @DisplayName("DELETE /api/users/{id}")
    class DeleteUserTests {

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("관리자가 사용자 삭제 시 DB에서 제거된다")
        void givenAdminUser_whenDeleteUser_thenUserIsRemovedFromDatabase() throws Exception {
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

            // 추가 검증: 조회 시 404 반환
            performGet("/api/users/{id}", userId)
                .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser(roles = {"USER"})
        @DisplayName("일반 사용자가 삭제 시도 시 403 에러를 반환한다")
        void givenNormalUser_whenDeleteUser_thenReturn403() throws Exception {
            // Given
            User existingUser = userRepository.save(User.builder()
                .name("사용자")
                .email("user@example.com")
                .password("encoded")
                .build());
            Long userId = existingUser.getId();
            log.debug("Given: 일반 사용자 권한으로 삭제 시도");

            // When & Then
            performDelete("/api/users/{id}", userId)
                .andExpect(status().isForbidden());
            log.debug("When: DELETE /api/users/{} 호출 → 403 확인", userId);

            // DB 상태: 사용자가 삭제되지 않음
            flushAndClear();
            assertThat(userRepository.findById(userId)).isPresent();
            log.debug("Then: DB에 사용자 여전히 존재 확인");
        }
    }

    // ========================================
    // GET /api/users/{id} - 사용자 조회
    // ========================================

    @Nested
    @DisplayName("GET /api/users/{id}")
    class GetUserTests {

        @Test
        @WithMockUser
        @DisplayName("존재하는 사용자 조회 시 상세 정보를 반환한다")
        void givenExistingUser_whenGetUser_thenReturnUserDetails() throws Exception {
            // Given
            User existingUser = userRepository.save(User.builder()
                .name("홍길동")
                .email("hong@example.com")
                .password("encoded")
                .build());
            Long userId = existingUser.getId();
            log.debug("Given: 조회 대상 사용자 - id={}", userId);

            // When & Then
            performGet("/api/users/{id}", userId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.email").value("hong@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());  // 응답에 비밀번호 미포함
            log.debug("When & Then: GET /api/users/{} 호출 → 정상 응답 확인", userId);
        }

        @Test
        @DisplayName("인증 없이 조회 시 401 에러를 반환한다")
        void givenNoAuth_whenGetUser_thenReturn401() throws Exception {
            // Given
            User existingUser = userRepository.save(User.builder()
                .name("사용자")
                .email("user@example.com")
                .password("encoded")
                .build());
            log.debug("Given: 인증 없이 요청");

            // When & Then
            performGet("/api/users/{id}", existingUser.getId())
                .andExpect(status().isUnauthorized());
            log.debug("When & Then: GET /api/users/{} 호출 → 401 확인", existingUser.getId());
        }
    }

    // ========================================
    // 엣지케이스 테스트
    // ========================================

    @Nested
    @DisplayName("엣지케이스")
    class EdgeCaseTests {

        @Test
        @WithMockUser
        @DisplayName("특수문자가 포함된 이름도 정상 저장된다")
        void givenNameWithSpecialChars_whenCreate_thenPersistCorrectly() throws Exception {
            // Given
            CreateUserRequest request = new CreateUserRequest(
                "홍길동™ <특별> [2024]",
                "special@example.com",
                "password"
            );
            log.debug("Given: 특수문자 포함 이름");

            // When
            MvcResult result = performPost("/api/users", request)
                .andExpect(status().isCreated())
                .andReturn();

            // Then
            Long userId = extractIdFromResponse(result);
            flushAndClear();

            User savedUser = userRepository.findById(userId).orElseThrow();
            assertThat(savedUser.getName()).isEqualTo("홍길동™ <특별> [2024]");
            log.debug("Then: 특수문자 포함 이름 저장 확인");
        }

        @Test
        @WithMockUser
        @DisplayName("이메일 대소문자를 무시하고 중복 체크한다")
        void givenEmailWithDifferentCase_whenCreate_thenTreatAsDuplicate() throws Exception {
            // Given
            userRepository.save(User.builder()
                .name("기존")
                .email("TEST@example.com")
                .password("encoded")
                .build());

            CreateUserRequest request = new CreateUserRequest(
                "새사용자",
                "test@EXAMPLE.COM",  // 다른 대소문자
                "password"
            );

            // When & Then
            performPost("/api/users", request)
                .andExpect(status().isConflict());

            flushAndClear();
            assertThat(userRepository.count()).isEqualTo(1);
            log.debug("Then: 대소문자 무시 중복 체크 확인");
        }
    }
}
