package com.github.ferigeek.sarv.controller;

import com.github.ferigeek.sarv.dto.response.UserSummaryResponse;
import com.github.ferigeek.sarv.entity.Media;
import com.github.ferigeek.sarv.entity.User;
import com.github.ferigeek.sarv.exception.FollowException;
import com.github.ferigeek.sarv.exception.UserNotFoundException;
import com.github.ferigeek.sarv.repository.EventLogRepository;
import com.github.ferigeek.sarv.repository.PostRepository;
import com.github.ferigeek.sarv.repository.UserRepository;
import com.github.ferigeek.sarv.security.JwtAuthFilter;
import com.github.ferigeek.sarv.security.JwtUtil;
import com.github.ferigeek.sarv.security.SecurityConfig;
import com.github.ferigeek.sarv.service.CustomUserDetailsService;
import com.github.ferigeek.sarv.service.FollowService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FollowController.class)
@Import({SecurityConfig.class, FollowControllerTest.TestJwtFilterConfig.class})
@DisplayName("FollowController")
class FollowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FollowService followService;

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

    private UserDetails testUser(String username) {
        return new org.springframework.security.core.userdetails.User(username, "password", List.of());
    }

    private UserSummaryResponse summary(Long id, String username, String displayName, Long picId) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("hash");
        user.setCreatedAt(OffsetDateTime.now());
        user.setGender(com.github.ferigeek.sarv.entity.type.Gender.MALE);
        user.setStatus(com.github.ferigeek.sarv.entity.type.UserStatus.ACTIVE);
        if (picId != null) {
            Media m = new Media();
            m.setId(picId);
            user.setProfilePicture(m);
        }
        return new UserSummaryResponse(user);
    }

    // ===================================================================
    // GET /api/users/{userId}/followers
    // ===================================================================
    @Nested
    @DisplayName("GET /api/users/{userId}/followers")
    class GetFollowers {

        @Test
        @DisplayName("should return 200 with list when authenticated")
        void shouldReturn200() throws Exception {
            UserSummaryResponse u1 = summary(1L, "alice", "Alice", 10L);
            UserSummaryResponse u2 = summary(3L, "charlie", "Charlie", null);
            when(followService.getFollowers(2L)).thenReturn(List.of(u1, u2));

            mockMvc.perform(get("/api/users/2/followers")
                            .with(user(testUser("bob"))))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value(1))
                    .andExpect(jsonPath("$[0].username").value("alice"))
                    .andExpect(jsonPath("$[0].displayName").value("Alice"))
                    .andExpect(jsonPath("$[0].profilePictureId").value(10))
                    .andExpect(jsonPath("$[1].id").value(3))
                    .andExpect(jsonPath("$[1].profilePictureId").doesNotExist());
        }

        @Test
        @DisplayName("should return 200 with empty list")
        void shouldReturnEmpty() throws Exception {
            when(followService.getFollowers(1L)).thenReturn(List.of());

            mockMvc.perform(get("/api/users/1/followers")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("should return 404 when user not found")
        void shouldReturn404() throws Exception {
            when(followService.getFollowers(99L)).thenThrow(new UserNotFoundException("User not found with ID: <99>"));

            mockMvc.perform(get("/api/users/99/followers")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.detail").value("User not found with ID: <99>"))
                    .andExpect(jsonPath("$.title").value("Not Found"))
                    .andExpect(jsonPath("$.instance").value("/api/users/99/followers"));
        }

        @Test
        @DisplayName("should return 403 when unauthenticated")
        void shouldReturn403() throws Exception {
            mockMvc.perform(get("/api/users/1/followers"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 400 for non-numeric userId")
        void shouldReturn400ForNonNumeric() throws Exception {
            mockMvc.perform(get("/api/users/abc/followers")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 500 when unexpected exception")
        void shouldReturn500() throws Exception {
            when(followService.getFollowers(1L)).thenThrow(new RuntimeException("fail"));

            mockMvc.perform(get("/api/users/1/followers")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
        }

        @Test
        @DisplayName("should return 405 for POST on followers list endpoint (POST is for follow, but with correct mapping? POST on /followers is follow, so GET vs POST distinct; POST without auth principal still requires auth)")
        void shouldCheckPostNotAllowedForGet() throws Exception {
            mockMvc.perform(put("/api/users/1/followers")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isMethodNotAllowed());
        }
    }

    // ===================================================================
    // GET /api/users/{userId}/following
    // ===================================================================
    @Nested
    @DisplayName("GET /api/users/{userId}/following")
    class GetFollowing {

        @Test
        @DisplayName("should return 200 with list")
        void shouldReturn200() throws Exception {
            UserSummaryResponse u1 = summary(2L, "bob", "Bob", null);
            when(followService.getFollowing(1L)).thenReturn(List.of(u1));

            mockMvc.perform(get("/api/users/1/following")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].username").value("bob"));
        }

        @Test
        @DisplayName("should return empty when none")
        void shouldReturnEmpty() throws Exception {
            when(followService.getFollowing(1L)).thenReturn(List.of());

            mockMvc.perform(get("/api/users/1/following")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("should return 404 when user not found")
        void shouldReturn404() throws Exception {
            when(followService.getFollowing(99L)).thenThrow(new UserNotFoundException("User not found with ID: <99>"));

            mockMvc.perform(get("/api/users/99/following")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("User not found with ID: <99>"));
        }

        @Test
        @DisplayName("should return 403 when unauthenticated")
        void shouldReturn403() throws Exception {
            mockMvc.perform(get("/api/users/1/following"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 400 for non-numeric")
        void shouldReturn400() throws Exception {
            mockMvc.perform(get("/api/users/abc/following")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 500 for unexpected")
        void shouldReturn500() throws Exception {
            when(followService.getFollowing(1L)).thenThrow(new RuntimeException("x"));

            mockMvc.perform(get("/api/users/1/following")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isInternalServerError());
        }
    }

    // ===================================================================
    // POST /api/users/{userId}/followers  (follow)
    // ===================================================================
    @Nested
    @DisplayName("POST /api/users/{userId}/followers")
    class FollowUser {

        @Test
        @DisplayName("should return 201 Created when follow succeeds")
        void shouldReturn201() throws Exception {
            mockMvc.perform(post("/api/users/2/followers")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("should return 403 when unauthenticated")
        void shouldReturn403() throws Exception {
            mockMvc.perform(post("/api/users/2/followers"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 404 when follower not found (username)")
        void shouldReturn404WhenFollowerNotFound() throws Exception {
            doThrow(new UserNotFoundException("Follower user not found with username: <ghost>"))
                    .when(followService).followUser(eq("ghost"), eq(2L));

            mockMvc.perform(post("/api/users/2/followers")
                            .with(user(testUser("ghost"))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Follower user not found with username: <ghost>"))
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("should return 404 when followed not found (id)")
        void shouldReturn404WhenFollowedNotFound() throws Exception {
            doThrow(new UserNotFoundException("Followed user not found with ID: <99>"))
                    .when(followService).followUser(eq("alice"), eq(99L));

            mockMvc.perform(post("/api/users/99/followers")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Followed user not found with ID: <99>"));
        }

        @Test
        @DisplayName("should return 500 when unexpected exception")
        void shouldReturn500() throws Exception {
            doThrow(new RuntimeException("db fail")).when(followService).followUser(any(), any());

            mockMvc.perform(post("/api/users/2/followers")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
        }

        @Test
        @DisplayName("should return 400 for non-numeric userId")
        void shouldReturn400ForNonNumeric() throws Exception {
            mockMvc.perform(post("/api/users/abc/followers")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 405 for GET on follow endpoint? GET is list, POST is follow – distinct but same path, so GET should be allowed for list, not considered 405. Test PUT instead")
        void shouldReturn405ForPut() throws Exception {
            mockMvc.perform(put("/api/users/2/followers")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isMethodNotAllowed());
        }

        @Test
        @DisplayName("should use principal username for follow")
        void shouldUsePrincipal() throws Exception {
            // alice follows bob
            mockMvc.perform(post("/api/users/2/followers")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isCreated());

            // verify indirectly by stubbing specific username – if service called with wrong username it would throw
            doThrow(new UserNotFoundException("Follower user not found with username: <bob>"))
                    .when(followService).followUser(eq("bob"), eq(2L));
            mockMvc.perform(post("/api/users/2/followers")
                            .with(user(testUser("bob"))))
                    .andExpect(status().isNotFound());
        }
    }

    // ===================================================================
    // DELETE /api/users/{userId}/followers (unfollow)
    // ===================================================================
    @Nested
    @DisplayName("DELETE /api/users/{userId}/followers")
    class UnfollowUser {

        @Test
        @DisplayName("should return 204 No Content when unfollow succeeds")
        void shouldReturn204() throws Exception {
            mockMvc.perform(delete("/api/users/2/followers")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));
        }

        @Test
        @DisplayName("should return 403 when unauthenticated")
        void shouldReturn403() throws Exception {
            mockMvc.perform(delete("/api/users/2/followers"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 404 when follower not found")
        void shouldReturn404WhenFollowerNotFound() throws Exception {
            doThrow(new UserNotFoundException("Follower user not found with username: <ghost>"))
                    .when(followService).unfollowUser(eq("ghost"), eq(2L));

            mockMvc.perform(delete("/api/users/2/followers")
                            .with(user(testUser("ghost"))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Follower user not found with username: <ghost>"));
        }

        @Test
        @DisplayName("should return 404 when followed not found")
        void shouldReturn404WhenFollowedNotFound() throws Exception {
            doThrow(new UserNotFoundException("Followed user not found with ID: <99>"))
                    .when(followService).unfollowUser(eq("alice"), eq(99L));

            mockMvc.perform(delete("/api/users/99/followers")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 400 when FollowException (not following)")
        void shouldReturn400WhenFollowException() throws Exception {
            doThrow(new FollowException("A follow from user with ID: <1>, following user with ID: <2>, doesn't exist"))
                    .when(followService).unfollowUser(eq("alice"), eq(2L));

            mockMvc.perform(delete("/api/users/2/followers")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.detail").value("A follow from user with ID: <1>, following user with ID: <2>, doesn't exist"))
                    .andExpect(jsonPath("$.title").value("Bad Request"))
                    .andExpect(jsonPath("$.instance").value("/api/users/2/followers"));
        }

        @Test
        @DisplayName("should return 500 when unexpected")
        void shouldReturn500() throws Exception {
            doThrow(new RuntimeException("fail")).when(followService).unfollowUser(any(), any());

            mockMvc.perform(delete("/api/users/2/followers")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
        }

        @Test
        @DisplayName("should return 400 for non-numeric userId")
        void shouldReturn400ForNonNumeric() throws Exception {
            mockMvc.perform(delete("/api/users/abc/followers")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 405 for GET is not for unfollow? DELETE is correct, PUT should be 405")
        void shouldReturn405ForPut() throws Exception {
            mockMvc.perform(put("/api/users/2/followers")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isMethodNotAllowed());
        }
    }

    // ===================================================================
    // HTTP contract & security
    // ===================================================================
    @Nested
    @DisplayName("HTTP contract")
    class HttpContract {

        @Test
        @DisplayName("should require authentication for all follow endpoints")
        void shouldRequireAuth() throws Exception {
            mockMvc.perform(get("/api/users/1/followers")).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/users/1/following")).andExpect(status().isForbidden());
            mockMvc.perform(post("/api/users/1/followers")).andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/users/1/followers")).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 405 for unsupported methods")
        void shouldReturn405() throws Exception {
            mockMvc.perform(put("/api/users/1/following")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isMethodNotAllowed());
            mockMvc.perform(delete("/api/users/1/following")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isMethodNotAllowed());
            mockMvc.perform(get("/api/users/1/followers")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk()); // GET is allowed, so not 405
        }
    }
}
