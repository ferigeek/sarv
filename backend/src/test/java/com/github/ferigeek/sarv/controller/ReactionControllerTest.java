package com.github.ferigeek.sarv.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.ferigeek.sarv.dto.request.ReactionRequest;
import com.github.ferigeek.sarv.dto.response.ReactionResponse;
import com.github.ferigeek.sarv.exception.PostNotFoundException;
import com.github.ferigeek.sarv.exception.UserNotFoundException;
import com.github.ferigeek.sarv.repository.EventLogRepository;
import com.github.ferigeek.sarv.repository.PostRepository;
import com.github.ferigeek.sarv.repository.UserRepository;
import com.github.ferigeek.sarv.security.JwtAuthFilter;
import com.github.ferigeek.sarv.security.JwtUtil;
import com.github.ferigeek.sarv.security.SecurityConfig;
import com.github.ferigeek.sarv.service.CustomUserDetailsService;
import com.github.ferigeek.sarv.service.ReactionService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReactionController.class)
@Import({SecurityConfig.class, ReactionControllerTest.TestJwtFilterConfig.class})
@DisplayName("ReactionController")
class ReactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ReactionService reactionService;
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
        JwtAuthFilter jwtAuthFilter(JwtUtil jwtUtil, CustomUserDetailsService cds) {
            return new JwtAuthFilter(jwtUtil, cds);
        }
    }

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }

    private UserDetails testUser(String username) {
        return new org.springframework.security.core.userdetails.User(username, "password", List.of());
    }

    private ReactionRequest like() {
        ReactionRequest r = new ReactionRequest();
        r.setReactionType((short) 1);
        return r;
    }

    private ReactionRequest dislike() {
        ReactionRequest r = new ReactionRequest();
        r.setReactionType((short) -1);
        return r;
    }

    // ===================================================================
    // POST /api/posts/{postId}/reactions
    // ===================================================================
    @Nested
    @DisplayName("POST /api/posts/{postId}/reactions")
    class AddReaction {

        @Test
        @DisplayName("should return 200 with ReactionResponse on success (like)")
        void shouldReturn200Like() throws Exception {
            ReactionResponse resp = new ReactionResponse(10L, 2L, (short) 1);
            when(reactionService.addReaction(eq(1L), any(ReactionRequest.class), eq("alice"))).thenReturn(resp);

            mockMvc.perform(post("/api/posts/1/reactions")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(like())))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.likeCount").value(10))
                    .andExpect(jsonPath("$.dislikeCount").value(2))
                    .andExpect(jsonPath("$.userReaction").value(1));
        }

        @Test
        @DisplayName("should return 200 for dislike")
        void shouldReturn200Dislike() throws Exception {
            ReactionResponse resp = new ReactionResponse(3L, 5L, (short) -1);
            when(reactionService.addReaction(eq(1L), any(), eq("alice"))).thenReturn(resp);

            mockMvc.perform(post("/api/posts/1/reactions")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(dislike())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userReaction").value(-1))
                    .andExpect(jsonPath("$.dislikeCount").value(5));
        }

        @Test
        @DisplayName("should use principal username")
        void shouldUsePrincipal() throws Exception {
            ReactionResponse resp = new ReactionResponse(1L, 0L, (short) 1);
            when(reactionService.addReaction(eq(2L), any(), eq("bob"))).thenReturn(resp);

            mockMvc.perform(post("/api/posts/2/reactions")
                            .with(user(testUser("bob")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(like())))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should return 403 when unauthenticated")
        void shouldReturn403() throws Exception {
            mockMvc.perform(post("/api/posts/1/reactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(like())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 400 for non-numeric postId")
        void shouldReturn400NonNumeric() throws Exception {
            mockMvc.perform(post("/api/posts/abc/reactions")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(like())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 for negative postId")
        void shouldReturn400Negative() throws Exception {
            mockMvc.perform(post("/api/posts/-1/reactions")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(like())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 for zero postId")
        void shouldReturn400Zero() throws Exception {
            mockMvc.perform(post("/api/posts/0/reactions")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(like())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when reactionType missing (null)")
        void shouldReturn400WhenMissing() throws Exception {
            String json = "{}";
            mockMvc.perform(post("/api/posts/1/reactions")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when body is empty")
        void shouldReturn400Empty() throws Exception {
            mockMvc.perform(post("/api/posts/1/reactions")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when JSON malformed")
        void shouldReturn400Malformed() throws Exception {
            mockMvc.perform(post("/api/posts/1/reactions")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{invalid"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when no body")
        void shouldReturn400NoBody() throws Exception {
            mockMvc.perform(post("/api/posts/1/reactions")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 404 when PostNotFoundException")
        void shouldReturn404PostNotFound() throws Exception {
            when(reactionService.addReaction(any(), any(), any())).thenThrow(new PostNotFoundException(99L));

            mockMvc.perform(post("/api/posts/99/reactions")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(like())))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.detail").value("Post not found with ID: <99>"))
                    .andExpect(jsonPath("$.instance").value("/api/posts/99/reactions"));
        }

        @Test
        @DisplayName("should return 404 when UserNotFoundException")
        void shouldReturn404UserNotFound() throws Exception {
            when(reactionService.addReaction(any(), any(), any())).thenThrow(new UserNotFoundException("User not found with username: <ghost>"));

            mockMvc.perform(post("/api/posts/1/reactions")
                            .with(user(testUser("ghost")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(like())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("User not found with username: <ghost>"));
        }

        @Test
        @DisplayName("should return 500 when unexpected exception")
        void shouldReturn500() throws Exception {
            when(reactionService.addReaction(any(), any(), any())).thenThrow(new RuntimeException("fail"));

            mockMvc.perform(post("/api/posts/1/reactions")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(like())))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
        }

        @Test
        @DisplayName("should return 405 for GET on POST endpoint? GET is separate (counts) so not 405, test PUT instead")
        void shouldReturn405ForPut() throws Exception {
            mockMvc.perform(put("/api/posts/1/reactions")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(like())))
                    .andExpect(status().isMethodNotAllowed());
        }
    }

    // ===================================================================
    // DELETE /api/posts/{postId}/reactions
    // ===================================================================
    @Nested
    @DisplayName("DELETE /api/posts/{postId}/reactions")
    class RemoveReaction {

        @Test
        @DisplayName("should return 204 No Content on success")
        void shouldReturn204() throws Exception {
            mockMvc.perform(delete("/api/posts/1/reactions")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));
        }

        @Test
        @DisplayName("should return 403 when unauthenticated")
        void shouldReturn403() throws Exception {
            mockMvc.perform(delete("/api/posts/1/reactions"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 400 for non-numeric postId")
        void shouldReturn400NonNumeric() throws Exception {
            mockMvc.perform(delete("/api/posts/abc/reactions")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 for negative postId")
        void shouldReturn400Negative() throws Exception {
            mockMvc.perform(delete("/api/posts/-1/reactions")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 for zero")
        void shouldReturn400Zero() throws Exception {
            mockMvc.perform(delete("/api/posts/0/reactions")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 404 when PostNotFoundException")
        void shouldReturn404PostNotFound() throws Exception {
            org.mockito.Mockito.doThrow(new PostNotFoundException(99L)).when(reactionService).removeReaction(eq(99L), any());

            mockMvc.perform(delete("/api/posts/99/reactions")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Post not found with ID: <99>"));
        }

        @Test
        @DisplayName("should return 404 when UserNotFoundException")
        void shouldReturn404UserNotFound() throws Exception {
            org.mockito.Mockito.doThrow(new UserNotFoundException("User not found with username: <ghost>")).when(reactionService).removeReaction(any(), eq("ghost"));

            mockMvc.perform(delete("/api/posts/1/reactions")
                            .with(user(testUser("ghost"))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 204 even when no existing reaction (idempotent – service no-op)")
        void shouldReturn204WhenNoReaction() throws Exception {
            // service does not throw when no reaction, just no-op, so still 204
            mockMvc.perform(delete("/api/posts/1/reactions")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("should return 500 for unexpected")
        void shouldReturn500() throws Exception {
            org.mockito.Mockito.doThrow(new RuntimeException("x")).when(reactionService).removeReaction(any(), any());

            mockMvc.perform(delete("/api/posts/1/reactions")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("should return 405 for POST is for add, GET is for counts – test PUT not allowed")
        void shouldReturn405ForPut() throws Exception {
            mockMvc.perform(put("/api/posts/1/reactions")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isMethodNotAllowed());
        }
    }

    // ===================================================================
    // GET /api/posts/{postId}/reactions
    // ===================================================================
    @Nested
    @DisplayName("GET /api/posts/{postId}/reactions")
    class GetReactionCounts {

        @Test
        @DisplayName("should return 200 with ReactionResponse")
        void shouldReturn200() throws Exception {
            ReactionResponse resp = new ReactionResponse(100L, 5L, (short) 1);
            when(reactionService.getReactionCounts(eq(1L), eq("alice"))).thenReturn(resp);

            mockMvc.perform(get("/api/posts/1/reactions")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.likeCount").value(100))
                    .andExpect(jsonPath("$.dislikeCount").value(5))
                    .andExpect(jsonPath("$.userReaction").value(1));
        }

        @Test
        @DisplayName("should handle userReaction 0 (no reaction) and -1")
        void shouldHandleZeroAndDislike() throws Exception {
            ReactionResponse zero = new ReactionResponse(10L, 2L, (short) 0);
            when(reactionService.getReactionCounts(eq(1L), eq("alice"))).thenReturn(zero);
            mockMvc.perform(get("/api/posts/1/reactions")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userReaction").value(0));

            ReactionResponse dislike = new ReactionResponse(10L, 3L, (short) -1);
            when(reactionService.getReactionCounts(eq(1L), eq("alice"))).thenReturn(dislike);
            mockMvc.perform(get("/api/posts/1/reactions")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userReaction").value(-1));
        }

        @Test
        @DisplayName("should return 403 when unauthenticated")
        void shouldReturn403() throws Exception {
            mockMvc.perform(get("/api/posts/1/reactions"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 400 for non-numeric")
        void shouldReturn400NonNumeric() throws Exception {
            mockMvc.perform(get("/api/posts/abc/reactions")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 for negative/zero")
        void shouldReturn400NegativeZero() throws Exception {
            mockMvc.perform(get("/api/posts/-5/reactions")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(get("/api/posts/0/reactions")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 404 when PostNotFound")
        void shouldReturn404() throws Exception {
            when(reactionService.getReactionCounts(any(), any())).thenThrow(new PostNotFoundException(77L));

            mockMvc.perform(get("/api/posts/77/reactions")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Post not found with ID: <77>"));
        }

        @Test
        @DisplayName("should return 404 when UserNotFound")
        void shouldReturn404User() throws Exception {
            when(reactionService.getReactionCounts(any(), any())).thenThrow(new UserNotFoundException("User not found with username: <ghost>"));

            mockMvc.perform(get("/api/posts/1/reactions")
                            .with(user(testUser("ghost"))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 500 for unexpected")
        void shouldReturn500() throws Exception {
            when(reactionService.getReactionCounts(any(), any())).thenThrow(new RuntimeException("fail"));

            mockMvc.perform(get("/api/posts/1/reactions")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isInternalServerError());
        }
    }

    // ===================================================================
    // HTTP contract
    // ===================================================================
    @Nested
    @DisplayName("HTTP contract")
    class HttpContract {

        @Test
        @DisplayName("should require authentication for all reaction endpoints")
        void shouldRequireAuth() throws Exception {
            mockMvc.perform(post("/api/posts/1/reactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reactionType\":1}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/posts/1/reactions"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/posts/1/reactions"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 405 for unsupported methods")
        void shouldReturn405() throws Exception {
            mockMvc.perform(put("/api/posts/1/reactions")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isMethodNotAllowed());
            mockMvc.perform(patch("/api/posts/1/reactions")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isMethodNotAllowed());
        }
    }
}
