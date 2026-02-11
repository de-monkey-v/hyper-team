package com.example.integration;

import com.example.domain.User;
import com.example.dto.CreateUserRequest;
import com.example.dto.LoginRequest;
import com.example.dto.UserResponse;
import com.example.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 사용자 API 통합 테스트 예시 (JUnit + Spring Boot)
 *
 * 실행: ./gradlew test --tests "UserIntegrationTest"
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Nested
    @DisplayName("회원가입 API")
    class RegisterTests {

        @Test
        @DisplayName("유효한 정보로 회원가입하면 201을 반환한다")
        void 유효한_정보로_회원가입하면_201을_반환한다() throws Exception {
            // Arrange
            CreateUserRequest request = new CreateUserRequest(
                "홍길동",
                "hong@example.com",
                "SecurePass123!"
            );

            // Act & Assert
            mockMvc.perform(post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.email").value("hong@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist()); // 비밀번호 노출 안 됨

            // DB 검증
            assertThat(userRepository.findByEmail("hong@example.com")).isPresent();
        }

        @Test
        @DisplayName("이미 존재하는 이메일로 가입 시도하면 409를 반환한다")
        void 이미_존재하는_이메일로_가입_시도하면_409를_반환한다() throws Exception {
            // Arrange - 기존 사용자 생성
            userRepository.save(new User("기존사용자", "existing@example.com", "password"));

            CreateUserRequest request = new CreateUserRequest(
                "새사용자",
                "existing@example.com", // 중복 이메일
                "SecurePass123!"
            );

            // Act & Assert
            mockMvc.perform(post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 사용 중인 이메일입니다"));
        }

        @Test
        @DisplayName("잘못된 이메일 형식이면 400을 반환한다")
        void 잘못된_이메일_형식이면_400을_반환한다() throws Exception {
            CreateUserRequest request = new CreateUserRequest(
                "홍길동",
                "invalid-email", // 잘못된 형식
                "SecurePass123!"
            );

            mockMvc.perform(post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
        }
    }

    @Nested
    @DisplayName("로그인 API")
    class LoginTests {

        @BeforeEach
        void setUpUser() {
            // 테스트용 사용자 생성 (실제로는 암호화된 비밀번호 저장)
            User user = new User("홍길동", "hong@example.com", "encodedPassword");
            userRepository.save(user);
        }

        @Test
        @DisplayName("유효한 자격증명으로 로그인하면 토큰을 반환한다")
        void 유효한_자격증명으로_로그인하면_토큰을_반환한다() throws Exception {
            LoginRequest request = new LoginRequest("hong@example.com", "correctPassword");

            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
        }

        @Test
        @DisplayName("잘못된 비밀번호로 로그인하면 401을 반환한다")
        void 잘못된_비밀번호로_로그인하면_401을_반환한다() throws Exception {
            LoginRequest request = new LoginRequest("hong@example.com", "wrongPassword");

            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("비밀번호가 일치하지 않습니다"));
        }

        @Test
        @DisplayName("존재하지 않는 이메일로 로그인하면 401을 반환한다")
        void 존재하지_않는_이메일로_로그인하면_401을_반환한다() throws Exception {
            LoginRequest request = new LoginRequest("nonexistent@example.com", "password");

            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다"));
        }
    }

    @Nested
    @DisplayName("사용자 조회 API")
    class GetUserTests {

        private User savedUser;

        @BeforeEach
        void setUpUser() {
            savedUser = userRepository.save(new User("홍길동", "hong@example.com", "password"));
        }

        @Test
        @DisplayName("존재하는 사용자 ID로 조회하면 사용자 정보를 반환한다")
        void 존재하는_사용자_ID로_조회하면_사용자_정보를_반환한다() throws Exception {
            mockMvc.perform(get("/api/users/{id}", savedUser.getId())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedUser.getId()))
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.email").value("hong@example.com"));
        }

        @Test
        @DisplayName("존재하지 않는 사용자 ID로 조회하면 404를 반환한다")
        void 존재하지_않는_사용자_ID로_조회하면_404를_반환한다() throws Exception {
            mockMvc.perform(get("/api/users/{id}", 99999L)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("사용자 목록 조회 API")
    class ListUsersTests {

        @BeforeEach
        void setUpUsers() {
            userRepository.save(new User("홍길동", "hong@example.com", "password"));
            userRepository.save(new User("김철수", "kim@example.com", "password"));
            userRepository.save(new User("이영희", "lee@example.com", "password"));
        }

        @Test
        @DisplayName("사용자 목록을 페이지네이션으로 조회한다")
        void 사용자_목록을_페이지네이션으로_조회한다() throws Exception {
            mockMvc.perform(get("/api/users")
                    .param("page", "0")
                    .param("size", "2")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        }
    }
}
