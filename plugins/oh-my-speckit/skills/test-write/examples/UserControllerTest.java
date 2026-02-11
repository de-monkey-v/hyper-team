package com.example.controller;

import com.example.domain.User;
import com.example.dto.CreateUserRequest;
import com.example.dto.UpdateUserRequest;
import com.example.exception.DuplicateEmailException;
import com.example.exception.UserNotFoundException;
import com.example.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UserController E2E 테스트 예제
 *
 * - @WebMvcTest: Controller 레이어만 로드 (빠른 테스트)
 * - @MockBean: Service를 Mock으로 대체
 * - MockMvc: HTTP 요청/응답 테스트
 * - @WithMockUser: 인증된 사용자 시뮬레이션
 * - @Nested: 엔드포인트별 그룹화
 * - andDo(print()): 요청/응답 로깅
 */
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

    // 테스트 시간 측정
    private long testStartTime;

    @BeforeEach
    void setUp(TestInfo testInfo) {
        testStartTime = System.currentTimeMillis();
        log.info("=== Test Started: {} ===", testInfo.getDisplayName());
    }

    @AfterEach
    void tearDown(TestInfo testInfo) {
        long duration = System.currentTimeMillis() - testStartTime;
        log.info("=== Test Completed: {} ({}ms) ===", testInfo.getDisplayName(), duration);
    }

    // ========================================
    // GET /api/users/{id} - 사용자 조회
    // ========================================

    @Nested
    @DisplayName("GET /api/users/{id}")
    class GetUser {

        @Test
        @WithMockUser
        @DisplayName("사용자 조회 성공 - 200 OK")
        void 사용자_조회_성공_200() throws Exception {
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
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.email").value("hong@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())  // 비밀번호 미노출
                .andReturn();

            log.debug("응답: {}", result.getResponse().getContentAsString());
            then(userService).should().findById(userId);
        }

        @Test
        @WithMockUser
        @DisplayName("존재하지 않는 사용자 - 404 Not Found")
        void 사용자_미존재_404() throws Exception {
            // Arrange
            Long userId = 999L;
            given(userService.findById(userId))
                .willThrow(new UserNotFoundException("사용자를 찾을 수 없습니다: " + userId));

            // Act & Assert
            mockMvc.perform(get("/api/users/{id}", userId)
                    .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다: 999"))
                .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        @DisplayName("인증 없이 접근 - 401 Unauthorized")
        void 인증없이_접근_401() throws Exception {
            mockMvc.perform(get("/api/users/1"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
        }
    }

    // ========================================
    // GET /api/users - 사용자 목록 조회
    // ========================================

    @Nested
    @DisplayName("GET /api/users")
    class GetUsers {

        @Test
        @WithMockUser
        @DisplayName("사용자 목록 조회 - 페이징")
        void 사용자_목록_조회_페이징() throws Exception {
            // Arrange
            List<User> users = List.of(
                User.builder().id(1L).name("User1").email("user1@example.com").build(),
                User.builder().id(2L).name("User2").email("user2@example.com").build()
            );
            Page<User> page = new PageImpl<>(users, PageRequest.of(0, 10), 100);
            given(userService.findAll(any(Pageable.class))).willReturn(page);

            // Act & Assert
            mockMvc.perform(get("/api/users")
                    .param("page", "0")
                    .param("size", "10"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(100))
                .andExpect(jsonPath("$.totalPages").value(10))
                .andExpect(jsonPath("$.number").value(0));
        }

        @Test
        @WithMockUser
        @DisplayName("빈 목록 조회")
        void 빈_목록_조회() throws Exception {
            // Arrange
            Page<User> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
            given(userService.findAll(any(Pageable.class))).willReturn(emptyPage);

            // Act & Assert
            mockMvc.perform(get("/api/users"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    // ========================================
    // POST /api/users - 사용자 생성
    // ========================================

    @Nested
    @DisplayName("POST /api/users")
    class CreateUser {

        @Test
        @WithMockUser
        @DisplayName("사용자 생성 성공 - 201 Created")
        void 사용자_생성_성공_201() throws Exception {
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
                .andExpect(header().string("Location", "/api/users/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.email").value("hong@example.com"));

            then(userService).should().createUser(any(CreateUserRequest.class));
        }

        @Test
        @WithMockUser
        @DisplayName("유효성 검증 실패 - 빈 이름 - 400 Bad Request")
        void 유효성_검증_실패_빈이름_400() throws Exception {
            // Arrange
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
                .andExpect(jsonPath("$.errors[?(@.field=='name')]").exists());

            // Service 호출되지 않음
            then(userService).should(never()).createUser(any());
        }

        @Test
        @WithMockUser
        @DisplayName("유효성 검증 실패 - 잘못된 이메일 형식 - 400 Bad Request")
        void 유효성_검증_실패_이메일형식_400() throws Exception {
            // Arrange
            CreateUserRequest request = new CreateUserRequest(
                "홍길동", "invalid-email", "password123"
            );

            // Act & Assert
            mockMvc.perform(post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='email')]").exists());
        }

        @Test
        @WithMockUser
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

        @Test
        @WithMockUser
        @DisplayName("잘못된 JSON 형식 - 400 Bad Request")
        void 잘못된_JSON_형식_400() throws Exception {
            // Act & Assert
            mockMvc.perform(post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{invalid json}"))
                .andDo(print())
                .andExpect(status().isBadRequest());
        }
    }

    // ========================================
    // PUT /api/users/{id} - 사용자 수정
    // ========================================

    @Nested
    @DisplayName("PUT /api/users/{id}")
    class UpdateUser {

        @Test
        @WithMockUser
        @DisplayName("사용자 수정 성공 - 200 OK")
        void 사용자_수정_성공_200() throws Exception {
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
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.name").value("홍길동2"));

            then(userService).should().updateUser(eq(userId), any());
        }

        @Test
        @WithMockUser
        @DisplayName("존재하지 않는 사용자 수정 - 404 Not Found")
        void 존재하지_않는_사용자_수정_404() throws Exception {
            // Arrange
            Long userId = 999L;
            UpdateUserRequest request = new UpdateUserRequest("새이름", null);
            given(userService.updateUser(eq(userId), any()))
                .willThrow(new UserNotFoundException("사용자를 찾을 수 없습니다"));

            // Act & Assert
            mockMvc.perform(put("/api/users/{id}", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isNotFound());
        }
    }

    // ========================================
    // DELETE /api/users/{id} - 사용자 삭제
    // ========================================

    @Nested
    @DisplayName("DELETE /api/users/{id}")
    class DeleteUser {

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("사용자 삭제 성공 - 204 No Content")
        void 사용자_삭제_성공_204() throws Exception {
            // Arrange
            Long userId = 1L;
            willDoNothing().given(userService).deleteUser(userId);

            // Act & Assert
            mockMvc.perform(delete("/api/users/{id}", userId))
                .andDo(print())
                .andExpect(status().isNoContent());

            then(userService).should().deleteUser(userId);
        }

        @Test
        @WithMockUser(roles = {"USER"})
        @DisplayName("일반 사용자 삭제 권한 없음 - 403 Forbidden")
        void 삭제_권한없음_403() throws Exception {
            mockMvc.perform(delete("/api/users/1"))
                .andDo(print())
                .andExpect(status().isForbidden());

            then(userService).should(never()).deleteUser(any());
        }

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("존재하지 않는 사용자 삭제 - 404 Not Found")
        void 존재하지_않는_사용자_삭제_404() throws Exception {
            // Arrange
            Long userId = 999L;
            willThrow(new UserNotFoundException("사용자를 찾을 수 없습니다"))
                .given(userService).deleteUser(userId);

            // Act & Assert
            mockMvc.perform(delete("/api/users/{id}", userId))
                .andDo(print())
                .andExpect(status().isNotFound());
        }
    }

    // ========================================
    // 검색 API
    // ========================================

    @Nested
    @DisplayName("GET /api/users/search")
    class SearchUsers {

        @Test
        @WithMockUser
        @DisplayName("키워드로 사용자 검색")
        void 키워드로_사용자_검색() throws Exception {
            // Arrange
            List<User> users = List.of(
                User.builder().id(1L).name("홍길동").email("hong@example.com").build()
            );
            given(userService.search(eq("홍"), any(Pageable.class)))
                .willReturn(new PageImpl<>(users));

            // Act & Assert
            mockMvc.perform(get("/api/users/search")
                    .param("keyword", "홍")
                    .param("page", "0")
                    .param("size", "10"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("홍길동"));
        }
    }
}
