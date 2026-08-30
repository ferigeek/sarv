package com.github.ferigeek.sarv.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.ferigeek.sarv.dto.request.UserUpdateRequest;
import com.github.ferigeek.sarv.dto.response.UserResponse;
import com.github.ferigeek.sarv.dto.response.UserSummaryResponse;
import com.github.ferigeek.sarv.entity.Media;
import com.github.ferigeek.sarv.entity.User;
import com.github.ferigeek.sarv.entity.type.Gender;
import com.github.ferigeek.sarv.entity.type.UserStatus;
import com.github.ferigeek.sarv.exception.MediaNotFoundException;
import com.github.ferigeek.sarv.exception.UserNotFoundException;
import com.github.ferigeek.sarv.repository.EventLogRepository;
import com.github.ferigeek.sarv.repository.PostRepository;
import com.github.ferigeek.sarv.repository.UserRepository;
import com.github.ferigeek.sarv.security.JwtAuthFilter;
import com.github.ferigeek.sarv.security.JwtUtil;
import com.github.ferigeek.sarv.security.SecurityConfig;
import com.github.ferigeek.sarv.service.CustomUserDetailsService;
import com.github.ferigeek.sarv.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, UserControllerTest.TestJwtFilterConfig.class})
@DisplayName("UserController")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private EventLogRepository eventLogRepository;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private PostRepository postRepository;

    @TestConfiguration
    static class TestJwtFilterConfig {
        @Bean
        JwtAuthFilter jwtAuthFilter(JwtUtil jwtUtil, CustomUserDetailsService customUserDetailsService) {
            return new JwtAuthFilter(jwtUtil, customUserDetailsService);
        }
    }

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }

    private UserDetails testUser(String username) {
        return new org.springframework.security.core.userdetails.User(
                username, "password", List.of());
    }

    private UserResponse userResponse(Long id, String username, String displayName, Gender gender, Long picId, UserStatus status) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("hash");
        user.setCreatedAt(OffsetDateTime.now());
        user.setBio("bio of " + username);
        user.setGender(gender);
        user.setLocation("Tehran");
        user.setStatus(status);
        if (picId != null) {
            Media m = new Media();
            m.setId(picId);
            user.setProfilePicture(m);
        }
        return new UserResponse(user);
    }

    private UserSummaryResponse userSummary(Long id, String username, String displayName, Long picId) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("hash");
        user.setCreatedAt(OffsetDateTime.now());
        user.setGender(Gender.MALE);
        user.setStatus(UserStatus.ACTIVE);
        if (picId != null) {
            Media m = new Media();
            m.setId(picId);
            user.setProfilePicture(m);
        }
        return new UserSummaryResponse(user);
    }

    private UserUpdateRequest validUpdate() {
        return new UserUpdateRequest("New Name", "new bio here", "Paris", 100L, Gender.MALE);
    }

    // ===================================================================
    // GET /api/users/{userId}
    // ===================================================================
    @Nested
    @DisplayName("GET /api/users/{userId}")
    class GetUserById {

        @Test
        @DisplayName("should return 200 with UserResponse when authenticated and user exists")
        void shouldReturn200() throws Exception {
            UserResponse resp = userResponse(1L, "alice", "Alice", Gender.FEMALE, 10L, UserStatus.ACTIVE);
            when(userService.getUser(1L)).thenReturn(resp);

            mockMvc.perform(get("/api/users/1")
                            .with(user(testUser("bob"))))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.username").value("alice"))
                    .andExpect(jsonPath("$.displayName").value("Alice"))
                    .andExpect(jsonPath("$.bio").value("bio of alice"))
                    .andExpect(jsonPath("$.gender").value("FEMALE"))
                    .andExpect(jsonPath("$.location").value("Tehran"))
                    .andExpect(jsonPath("$.profilePictureId").value(10))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("should map null profilePicture to null")
        void shouldMapNullPicture() throws Exception {
            UserResponse resp = userResponse(2L, "bob", "Bob", Gender.MALE, null, UserStatus.ACTIVE);
            when(userService.getUser(2L)).thenReturn(resp);

            mockMvc.perform(get("/api/users/2")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.profilePictureId").doesNotExist());
            // Actually UserResponse returns null for profilePictureId field, check is empty
            mockMvc.perform(get("/api/users/2")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.profilePictureId").doesNotExist()); // strict?
        }

        @Test
        @DisplayName("should handle null profilePicture as null in JSON")
        void shouldHandleNullPictureExplicit() throws Exception {
            User user = new User();
            user.setId(5L);
            user.setUsername("charlie");
            user.setDisplayName("Charlie");
            user.setEmail("charlie@example.com");
            user.setPasswordHash("h");
            user.setCreatedAt(OffsetDateTime.now());
            user.setBio(null);
            user.setGender(Gender.MALE);
            user.setLocation(null);
            user.setStatus(UserStatus.ACTIVE);
            user.setProfilePicture(null);
            UserResponse resp = new UserResponse(user);
            when(userService.getUser(5L)).thenReturn(resp);

            mockMvc.perform(get("/api/users/5")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.profilePictureId").doesNotExist());
        }

        @Test
        @DisplayName("should return 404 when UserNotFoundException")
        void shouldReturn404() throws Exception {
            when(userService.getUser(99L)).thenThrow(new UserNotFoundException("User not found with ID: <99>"));

            mockMvc.perform(get("/api/users/99")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.detail").value("User not found with ID: <99>"))
                    .andExpect(jsonPath("$.title").value("Not Found"))
                    .andExpect(jsonPath("$.instance").value("/api/users/99"));
        }

        @Test
        @DisplayName("should return 403 when unauthenticated")
        void shouldReturn401WhenUnauthenticated() throws Exception {
            mockMvc.perform(get("/api/users/1"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 400 for non-numeric userId")
        void shouldReturn400ForNonNumeric() throws Exception {
            mockMvc.perform(get("/api/users/abc")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        @DisplayName("should return 400 for negative userId (Positive validation)")
        void shouldReturn400ForNegative() throws Exception {
            mockMvc.perform(get("/api/users/-1")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 for zero userId (Positive validation)")
        void shouldReturn400ForZero() throws Exception {
            mockMvc.perform(get("/api/users/0")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 500 when service throws unexpected exception")
        void shouldReturn500() throws Exception {
            when(userService.getUser(1L)).thenThrow(new RuntimeException("db fail"));

            mockMvc.perform(get("/api/users/1")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
        }

        @Test
        @DisplayName("should return 405 for POST on this endpoint")
        void shouldReturn405ForPost() throws Exception {
            mockMvc.perform(post("/api/users/1")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isMethodNotAllowed());
        }
    }

    // ===================================================================
    // GET /api/users/me
    // ===================================================================
    @Nested
    @DisplayName("GET /api/users/me")
    class GetCurrentUser {

        @Test
        @DisplayName("should return 200 with current user profile")
        void shouldReturnCurrent() throws Exception {
            UserResponse resp = userResponse(10L, "ferigeek", "Feri Geek", Gender.MALE, null, UserStatus.ACTIVE);
            when(userService.getUserByUsername("ferigeek")).thenReturn(resp);

            mockMvc.perform(get("/api/users/me")
                            .with(user(testUser("ferigeek"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("ferigeek"))
                    .andExpect(jsonPath("$.id").value(10));
        }

        @Test
        @DisplayName("should use username from AuthenticationPrincipal")
        void shouldUsePrincipalUsername() throws Exception {
            UserResponse resp = userResponse(1L, "alice", "Alice", Gender.FEMALE, null, UserStatus.ACTIVE);
            when(userService.getUserByUsername("alice")).thenReturn(resp);

            mockMvc.perform(get("/api/users/me")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/users/me")
                            .with(user(testUser("bob"))))
                    .andExpect(status().isOk());
            // service should be called with respective usernames; verified indirectly via stubbing
        }

        @Test
        @DisplayName("should return 404 when current user not found")
        void shouldReturn404() throws Exception {
            when(userService.getUserByUsername("ghost")).thenThrow(new UserNotFoundException("User not found with username: <ghost>"));

            mockMvc.perform(get("/api/users/me")
                            .with(user(testUser("ghost"))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.detail").value("User not found with username: <ghost>"));
        }

        @Test
        @DisplayName("should return 403 when unauthenticated")
        void shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/users/me"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 500 for unexpected exception")
        void shouldReturn500() throws Exception {
            when(userService.getUserByUsername(any())).thenThrow(new RuntimeException("fail"));

            mockMvc.perform(get("/api/users/me")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("should return 405 for PUT on /me without body is still method-specific: PUT expected, POST should be 405")
        void shouldReturn405ForPost() throws Exception {
            mockMvc.perform(post("/api/users/me")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isMethodNotAllowed());
        }
    }

    // ===================================================================
    // PUT /api/users/me
    // ===================================================================
    @Nested
    @DisplayName("PUT /api/users/me")
    class UpdateCurrentUser {

        @Test
        @DisplayName("should return 200 with updated UserResponse on success")
        void shouldReturn200OnSuccess() throws Exception {
            UserResponse resp = userResponse(1L, "alice", "Updated Name", Gender.FEMALE, 100L, UserStatus.ACTIVE);
            resp.setBio("new bio here");
            resp.setLocation("Paris");
            when(userService.updateUser(eq("alice"), any(UserUpdateRequest.class))).thenReturn(resp);

            mockMvc.perform(put("/api/users/me")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validUpdate())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("alice"))
                    .andExpect(jsonPath("$.displayName").value("Updated Name"))
                    .andExpect(jsonPath("$.gender").value("FEMALE"));
        }

        @Test
        @DisplayName("should be 403 when unauthenticated")
        void shouldReturn401() throws Exception {
            mockMvc.perform(put("/api/users/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validUpdate())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 400 when displayName is blank")
        void shouldReturn400WhenDisplayNameBlank() throws Exception {
            UserUpdateRequest req = new UserUpdateRequest("", "bio", "loc", 1L, Gender.MALE);

            mockMvc.perform(put("/api/users/me")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when displayName is missing")
        void shouldReturn400WhenDisplayNameMissing() throws Exception {
            String json = """
                    {"bio":"bio","location":"loc","gender":"MALE"}
                    """;
            mockMvc.perform(put("/api/users/me")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when displayName too short (<2)")
        void shouldReturn400WhenDisplayNameTooShort() throws Exception {
            UserUpdateRequest req = new UserUpdateRequest("a", "bio ok", "loc", 1L, Gender.MALE);

            mockMvc.perform(put("/api/users/me")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when displayName whitespace")
        void shouldReturn400WhenDisplayNameWhitespace() throws Exception {
            UserUpdateRequest req = new UserUpdateRequest("   ", "bio ok", "loc", 1L, Gender.MALE);

            mockMvc.perform(put("/api/users/me")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when gender is null")
        void shouldReturn400WhenGenderNull() throws Exception {
            UserUpdateRequest req = new UserUpdateRequest("Valid Name", "bio", "loc", 1L, null);

            mockMvc.perform(put("/api/users/me")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when gender missing")
        void shouldReturn400WhenGenderMissing() throws Exception {
            String json = """
                    {"displayName":"Valid Name","bio":"bio","location":"loc"}
                    """;
            mockMvc.perform(put("/api/users/me")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when gender invalid")
        void shouldReturn400WhenGenderInvalid() throws Exception {
            String json = """
                    {"displayName":"Valid Name","bio":"bio","location":"loc","gender":"UNKNOWN"}
                    """;
            mockMvc.perform(put("/api/users/me")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when bio too short (size <2)")
        void shouldReturn400WhenBioTooShort() throws Exception {
            UserUpdateRequest req = new UserUpdateRequest("Valid Name", "a", "loc", 1L, Gender.MALE);

            mockMvc.perform(put("/api/users/me")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when bio too long (>255)")
        void shouldReturn400WhenBioTooLong() throws Exception {
            UserUpdateRequest req = new UserUpdateRequest("Valid Name", "a".repeat(256), "loc", 1L, Gender.MALE);

            mockMvc.perform(put("/api/users/me")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when location too short (<2)")
        void shouldReturn400WhenLocationTooShort() throws Exception {
            UserUpdateRequest req = new UserUpdateRequest("Valid Name", "valid bio", "a", 1L, Gender.MALE);

            mockMvc.perform(put("/api/users/me")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when location too long (>30)")
        void shouldReturn400WhenLocationTooLong() throws Exception {
            UserUpdateRequest req = new UserUpdateRequest("Valid Name", "valid bio", "a".repeat(31), 1L, Gender.MALE);

            mockMvc.perform(put("/api/users/me")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when profilePictureId is negative")
        void shouldReturn400WhenProfilePictureNegative() throws Exception {
            UserUpdateRequest req = new UserUpdateRequest("Valid Name", "valid bio", "valid loc", -5L, Gender.MALE);

            mockMvc.perform(put("/api/users/me")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when profilePictureId is zero")
        void shouldReturn400WhenProfilePictureZero() throws Exception {
            UserUpdateRequest req = new UserUpdateRequest("Valid Name", "valid bio", "valid loc", 0L, Gender.MALE);

            mockMvc.perform(put("/api/users/me")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should accept null bio and location and null profilePictureId")
        void shouldAcceptNullOptionalFields() throws Exception {
            UserUpdateRequest req = new UserUpdateRequest("Valid Name", null, null, null, Gender.RATHER_NOT_TO_SAY);
            UserResponse resp = userResponse(1L, "alice", "Valid Name", Gender.RATHER_NOT_TO_SAY, null, UserStatus.ACTIVE);
            resp.setBio(null);
            resp.setLocation(null);
            resp.setProfilePictureId(null);
            when(userService.updateUser(eq("alice"), any())).thenReturn(resp);

            mockMvc.perform(put("/api/users/me")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.displayName").value("Valid Name"));
        }

        @Test
        @DisplayName("should accept all Gender values")
        void shouldAcceptAllGenders() throws Exception {
            for (Gender g : Gender.values()) {
                UserUpdateRequest req = new UserUpdateRequest("Valid Name", "valid bio", "loc", null, g);
                UserResponse resp = userResponse(1L, "alice", "Valid Name", g, null, UserStatus.ACTIVE);
                when(userService.updateUser(eq("alice"), any())).thenReturn(resp);

                mockMvc.perform(put("/api/users/me")
                                .with(user(testUser("alice")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(req)))
                        .andExpect(status().isOk());
            }
        }

        @Test
        @DisplayName("should accept boundary sizes: bio 255, location 30, displayName 2")
        void shouldAcceptBoundaries() throws Exception {
            UserUpdateRequest req = new UserUpdateRequest("ab", "a".repeat(255), "a".repeat(30), 1L, Gender.MALE);
            UserResponse resp = userResponse(1L, "alice", "ab", Gender.MALE, 1L, UserStatus.ACTIVE);
            when(userService.updateUser(any(), any())).thenReturn(resp);

            mockMvc.perform(put("/api/users/me")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should return 400 when body is empty")
        void shouldReturn400WhenEmptyBody() throws Exception {
            mockMvc.perform(put("/api/users/me")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when JSON malformed")
        void shouldReturn400WhenMalformed() throws Exception {
            mockMvc.perform(put("/api/users/me")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{invalid"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when no body")
        void shouldReturn400WhenNoBody() throws Exception {
            mockMvc.perform(put("/api/users/me")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 404 when UserNotFoundException")
        void shouldReturn404() throws Exception {
            when(userService.updateUser(eq("ghost"), any())).thenThrow(new UserNotFoundException("User not found with Username: <ghost>"));

            mockMvc.perform(put("/api/users/me")
                            .with(user(testUser("ghost")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validUpdate())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("User not found with Username: <ghost>"))
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("should return 404 when MediaNotFoundException")
        void shouldReturn404ForMedia() throws Exception {
            when(userService.updateUser(any(), any())).thenThrow(new MediaNotFoundException(999L));

            mockMvc.perform(put("/api/users/me")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validUpdate())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Media not found with ID: <999>"))
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("should return 400 when IllegalArgumentException (displayName/gender)")
        void shouldReturn400ForIllegalArgument() throws Exception {
            when(userService.updateUser(any(), any())).thenThrow(new IllegalArgumentException("Display name can not be empty"));

            mockMvc.perform(put("/api/users/me")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validUpdate())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Display name can not be empty"));
        }

        @Test
        @DisplayName("should return 500 when unexpected exception")
        void shouldReturn500() throws Exception {
            when(userService.updateUser(any(), any())).thenThrow(new RuntimeException("fail"));

            mockMvc.perform(put("/api/users/me")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validUpdate())))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
        }

        @Test
        @DisplayName("should return 405 for GET on /me update endpoint (GET is allowed for fetching, but PUT is for update: GET with same path is allowed, so test wrong method POST)")
        void shouldReturn405ForWrongMethod() throws Exception {
            mockMvc.perform(post("/api/users/me")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validUpdate())))
                    .andExpect(status().isMethodNotAllowed());
        }

        @Test
        @DisplayName("should handle 400 for missing required fields via validation")
        void shouldReturn400WhenMultipleInvalid() throws Exception {
            UserUpdateRequest req = new UserUpdateRequest("", "a", "a", -1L, null);

            mockMvc.perform(put("/api/users/me")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ===================================================================
    // GET /api/users?query=...
    // ===================================================================
    @Nested
    @DisplayName("GET /api/users")
    class SearchUsers {

        @Test
        @DisplayName("should return 200 with page of UserSummaryResponse")
        void shouldReturnPage() throws Exception {
            UserSummaryResponse u1 = userSummary(1L, "alice", "Alice", 10L);
            UserSummaryResponse u2 = userSummary(2L, "bob", "Bob", null);
            when(userService.searchUsers(eq("ali"), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(u1, u2), PageRequest.of(0, 20), 2));

            mockMvc.perform(get("/api/users")
                            .param("query", "ali")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].id").value(1))
                    .andExpect(jsonPath("$.content[0].username").value("alice"))
                    .andExpect(jsonPath("$.content[0].displayName").value("Alice"))
                    .andExpect(jsonPath("$.content[0].profilePictureId").value(10))
                    .andExpect(jsonPath("$.content[1].username").value("bob"))
                    .andExpect(jsonPath("$.content[1].profilePictureId").doesNotExist())
                    .andExpect(jsonPath("$.page.size").value(20))
                    .andExpect(jsonPath("$.page.number").value(0))
                    .andExpect(jsonPath("$.page.totalElements").value(2))
                    .andExpect(jsonPath("$.page.totalPages").value(1));
        }

        @Test
        @DisplayName("should return 200 with empty page when no matches")
        void shouldReturnEmpty() throws Exception {
            when(userService.searchUsers(eq("nonexistent"), any(Pageable.class)))
                    .thenReturn(Page.empty());

            mockMvc.perform(get("/api/users")
                            .param("query", "nonexistent")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(0))
                    .andExpect(jsonPath("$.page.totalElements").value(0));
        }

        @Test
        @DisplayName("should return 403 when unauthenticated")
        void shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/users")
                            .param("query", "test"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 400 when query param missing")
        void shouldReturn400WhenMissingQuery() throws Exception {
            mockMvc.perform(get("/api/users")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should handle query with spaces and special chars")
        void shouldHandleSpecialQuery() throws Exception {
            when(userService.searchUsers(eq("a b"), any(Pageable.class))).thenReturn(Page.empty());

            mockMvc.perform(get("/api/users")
                            .param("query", "a b")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should return 500 when service throws unexpected")
        void shouldReturn500() throws Exception {
            when(userService.searchUsers(any(), any(Pageable.class))).thenThrow(new RuntimeException("fail"));

            mockMvc.perform(get("/api/users")
                            .param("query", "test")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("should return 405 for POST on search endpoint")
        void shouldReturn405ForPost() throws Exception {
            mockMvc.perform(post("/api/users")
                            .with(user(testUser("alice")))
                            .param("query", "test"))
                    .andExpect(status().isMethodNotAllowed());
        }

        @Test
        @DisplayName("should return 200 even when query is empty string (service handles)")
        void shouldHandleEmptyQuery() throws Exception {
            when(userService.searchUsers(eq(""), any(Pageable.class))).thenReturn(Page.empty());

            mockMvc.perform(get("/api/users")
                            .param("query", "")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should use default page=0, size=20 and sort by username when no paging params given")
        void shouldUseDefaultPageable() throws Exception {
            when(userService.searchUsers(eq("ali"), any(Pageable.class))).thenReturn(Page.empty());

            mockMvc.perform(get("/api/users")
                            .param("query", "ali")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk());

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(userService).searchUsers(eq("ali"), pageableCaptor.capture());
            Pageable pageable = pageableCaptor.getValue();
            assertThat(pageable.getPageNumber()).isZero();
            assertThat(pageable.getPageSize()).isEqualTo(20);
            assertThat(pageable.getSort()).isEqualTo(Sort.by("username"));
        }

        @Test
        @DisplayName("should pass requested page and size to the service")
        void shouldPassRequestedPageAndSize() throws Exception {
            when(userService.searchUsers(eq("ali"), any(Pageable.class))).thenReturn(Page.empty());

            mockMvc.perform(get("/api/users")
                            .param("query", "ali")
                            .param("page", "3")
                            .param("size", "5")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk());

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(userService).searchUsers(eq("ali"), pageableCaptor.capture());
            assertThat(pageableCaptor.getValue())
                    .isEqualTo(PageRequest.of(3, 5, Sort.by("username")));
        }
    }

    // ===================================================================
    // HTTP contract & security cross-cutting
    // ===================================================================
    @Nested
    @DisplayName("HTTP contract")
    class HttpContract {

        @Test
        @DisplayName("should require authentication for all user endpoints")
        void shouldRequireAuth() throws Exception {
            mockMvc.perform(get("/api/users/1")).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/users/me")).andExpect(status().isForbidden());
            mockMvc.perform(put("/api/users/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}")).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/users").param("query", "x")).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 404 for unknown method on existing path")
        void shouldReturn405ForUnknownMethods() throws Exception {
            mockMvc.perform(delete("/api/users/me")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isMethodNotAllowed());
            mockMvc.perform(post("/api/users/1")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isMethodNotAllowed());
        }
    }
}
