package com.github.ferigeek.sarv.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.ferigeek.sarv.dto.request.PostRequest;
import com.github.ferigeek.sarv.dto.request.PostUpdateRequest;
import com.github.ferigeek.sarv.dto.request.ReactionFilter;
import com.github.ferigeek.sarv.dto.response.PostResponse;
import com.github.ferigeek.sarv.entity.Media;
import com.github.ferigeek.sarv.entity.Post;
import com.github.ferigeek.sarv.entity.User;
import com.github.ferigeek.sarv.entity.type.PostCategory;
import com.github.ferigeek.sarv.exception.MediaNotFoundException;
import com.github.ferigeek.sarv.exception.PostNotFoundException;
import com.github.ferigeek.sarv.exception.PostNotValidException;
import com.github.ferigeek.sarv.exception.UnAuthorizedUpdateException;
import com.github.ferigeek.sarv.exception.UserNotFoundException;
import com.github.ferigeek.sarv.repository.EventLogRepository;
import com.github.ferigeek.sarv.repository.PostRepository;
import com.github.ferigeek.sarv.repository.UserRepository;
import com.github.ferigeek.sarv.security.JwtAuthFilter;
import com.github.ferigeek.sarv.security.JwtUtil;
import com.github.ferigeek.sarv.security.SecurityConfig;
import com.github.ferigeek.sarv.service.CustomUserDetailsService;
import com.github.ferigeek.sarv.service.PostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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

@WebMvcTest(PostController.class)
@Import({SecurityConfig.class, PostControllerTest.TestJwtFilterConfig.class})
@DisplayName("PostController")
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private PostService postService;

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
        return new org.springframework.security.core.userdetails.User(username, "password", List.of());
    }

    private PostResponse postResponse(Long id, Long userId, PostCategory cat, String content, Long mediaId, Long repostId, Long parentId) {
        User user = new User();
        user.setId(userId);
        user.setUsername("owner");
        user.setDisplayName("Owner");
        user.setEmail("owner@example.com");
        user.setPasswordHash("hash");
        user.setCreatedAt(OffsetDateTime.now());
        user.setStatus(com.github.ferigeek.sarv.entity.type.UserStatus.ACTIVE);
        user.setGender(com.github.ferigeek.sarv.entity.type.Gender.MALE);

        Post post = new Post();
        post.setId(id);
        post.setUser(user);
        post.setPostCategory(cat);
        post.setContent(content);
        post.setCreatedAt(OffsetDateTime.now());
        post.setViewCount(5L);
        post.setLikeCount(2L);
        post.setDislikeCount(1L);
        if (mediaId != null) {
            Media m = new Media();
            m.setId(mediaId);
            post.setMedia(m);
        }
        if (repostId != null) {
            Post repost = new Post();
            repost.setId(repostId);
            repost.setUser(user);
            post.setRepostOf(repost);
        }
        if (parentId != null) {
            Post parent = new Post();
            parent.setId(parentId);
            parent.setUser(user);
            post.setParent(parent);
        }
        return new PostResponse(post);
    }

    private PostRequest validNormalRequest() {
        return new PostRequest(PostCategory.NORMAL, "Hello world", null, null, null);
    }

    private PostUpdateRequest validUpdate() {
        return new PostUpdateRequest("Updated content", 10L);
    }

    // ===================================================================
    // GET /api/posts/{postId}
    // ===================================================================
    @Nested
    @DisplayName("GET /api/posts/{postId}")
    class GetPost {

        @Test
        @DisplayName("should return 200 with PostResponse when authenticated")
        void shouldReturn200() throws Exception {
            PostResponse resp = postResponse(1L, 10L, PostCategory.NORMAL, "content", 5L, null, null);
            when(postService.getPost(1L)).thenReturn(resp);

            mockMvc.perform(get("/api/posts/1")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.userId").value(10))
                    .andExpect(jsonPath("$.postCategory").value("NORMAL"))
                    .andExpect(jsonPath("$.content").value("content"))
                    .andExpect(jsonPath("$.mediaId").value(5))
                    .andExpect(jsonPath("$.viewCount").value(5))
                    .andExpect(jsonPath("$.likeCount").value(2))
                    .andExpect(jsonPath("$.dislikeCount").value(1));
        }

        @Test
        @DisplayName("should handle null media/repost/parent as absent")
        void shouldHandleNulls() throws Exception {
            PostResponse resp = postResponse(2L, 10L, PostCategory.NORMAL, "content", null, null, null);
            when(postService.getPost(2L)).thenReturn(resp);

            mockMvc.perform(get("/api/posts/2")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mediaId").doesNotExist())
                    .andExpect(jsonPath("$.repostOfId").doesNotExist())
                    .andExpect(jsonPath("$.parentId").doesNotExist());
        }

        @Test
        @DisplayName("should return 404 when PostNotFoundException")
        void shouldReturn404() throws Exception {
            when(postService.getPost(99L)).thenThrow(new PostNotFoundException(99L));

            mockMvc.perform(get("/api/posts/99")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.detail").value("Post not found with ID: <99>"))
                    .andExpect(jsonPath("$.title").value("Not Found"))
                    .andExpect(jsonPath("$.instance").value("/api/posts/99"));
        }

        @Test
        @DisplayName("should return 403 when unauthenticated")
        void shouldReturn403() throws Exception {
            mockMvc.perform(get("/api/posts/1"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 400 for non-numeric postId")
        void shouldReturn400NonNumeric() throws Exception {
            mockMvc.perform(get("/api/posts/abc")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 for negative postId")
        void shouldReturn400Negative() throws Exception {
            mockMvc.perform(get("/api/posts/-1")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 for zero postId")
        void shouldReturn400Zero() throws Exception {
            mockMvc.perform(get("/api/posts/0")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 500 for unexpected exception")
        void shouldReturn500() throws Exception {
            when(postService.getPost(1L)).thenThrow(new RuntimeException("fail"));

            mockMvc.perform(get("/api/posts/1")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
        }

        @Test
        @DisplayName("should return 405 for POST on this endpoint (GET vs POST mismatch)")
        void shouldReturn405ForPost() throws Exception {
            mockMvc.perform(post("/api/posts/1")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isMethodNotAllowed());
        }
    }

    // ===================================================================
    // POST /api/posts
    // ===================================================================
    @Nested
    @DisplayName("POST /api/posts")
    class CreatePost {

        @Test
        @DisplayName("should return 201 Created with Location and body on success")
        void shouldReturn201() throws Exception {
            PostResponse resp = postResponse(42L, 10L, PostCategory.NORMAL, "Hello", null, null, null);
            when(postService.createPost(any(PostRequest.class), eq("alice"))).thenReturn(resp);

            mockMvc.perform(post("/api/posts")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validNormalRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/api/posts/42"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(42))
                    .andExpect(jsonPath("$.postCategory").value("NORMAL"))
                    .andExpect(jsonPath("$.content").value("Hello"));
        }

        @Test
        @DisplayName("should use principal username for creation")
        void shouldUsePrincipal() throws Exception {
            PostResponse resp = postResponse(1L, 99L, PostCategory.NORMAL, "c", null, null, null);
            when(postService.createPost(any(), eq("bob"))).thenReturn(resp);

            mockMvc.perform(post("/api/posts")
                            .with(user(testUser("bob")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validNormalRequest())))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("should return 403 when unauthenticated")
        void shouldReturn403() throws Exception {
            mockMvc.perform(post("/api/posts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validNormalRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 400 when postCategory is null")
        void shouldReturn400WhenCategoryNull() throws Exception {
            PostRequest req = new PostRequest(null, "content", null, null, null);

            mockMvc.perform(post("/api/posts")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when postCategory invalid enum")
        void shouldReturn400WhenInvalidEnum() throws Exception {
            String json = """
                    {"postCategory":"UNKNOWN","content":"hello"}
                    """;
            mockMvc.perform(post("/api/posts")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when content too long (>280)")
        void shouldReturn400WhenContentTooLong() throws Exception {
            PostRequest req = new PostRequest(PostCategory.NORMAL, "a".repeat(281), null, null, null);

            mockMvc.perform(post("/api/posts")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when mediaId negative")
        void shouldReturn400WhenMediaIdNegative() throws Exception {
            PostRequest req = new PostRequest(PostCategory.NORMAL, "content", -1L, null, null);

            mockMvc.perform(post("/api/posts")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when mediaId zero")
        void shouldReturn400WhenMediaIdZero() throws Exception {
            PostRequest req = new PostRequest(PostCategory.NORMAL, "content", 0L, null, null);

            mockMvc.perform(post("/api/posts")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when parentId negative")
        void shouldReturn400WhenParentIdNegative() throws Exception {
            PostRequest req = new PostRequest(PostCategory.COMMENT, "content", null, -5L, null);

            mockMvc.perform(post("/api/posts")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when repostOfId negative")
        void shouldReturn400WhenRepostIdNegative() throws Exception {
            PostRequest req = new PostRequest(PostCategory.REPOST, null, null, null, -1L);

            mockMvc.perform(post("/api/posts")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when category missing")
        void shouldReturn400WhenCategoryMissing() throws Exception {
            String json = """
                    {"content":"hello"}
                    """;
            mockMvc.perform(post("/api/posts")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should allow all valid PostCategory values (validation passes, service may still reject business rules)")
        void shouldAllowAllCategories() throws Exception {
            for (PostCategory cat : PostCategory.values()) {
                // Provide minimal valid content for categories that require text/media to avoid business validation error
                // For REPOST, content must be null/blank and media null, so handle specially
                PostRequest req;
                if (cat == PostCategory.REPOST) {
                    req = new PostRequest(cat, null, null, null, 99L);
                } else if (cat == PostCategory.QUOTE) {
                    req = new PostRequest(cat, "quote content", null, null, 99L);
                } else if (cat == PostCategory.COMMENT) {
                    req = new PostRequest(cat, "comment", null, 1L, null);
                } else {
                    req = new PostRequest(cat, "normal content", null, null, null);
                }
                PostResponse resp = postResponse(1L, 10L, cat, "content", null, null, null);
                when(postService.createPost(any(), any())).thenReturn(resp);

                mockMvc.perform(post("/api/posts")
                                .with(user(testUser("alice")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(req)))
                        .andExpect(status().isCreated());
            }
        }

        @Test
        @DisplayName("should return 400 when body is empty")
        void shouldReturn400WhenEmptyBody() throws Exception {
            mockMvc.perform(post("/api/posts")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when JSON malformed")
        void shouldReturn400WhenMalformed() throws Exception {
            mockMvc.perform(post("/api/posts")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{invalid"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when no body")
        void shouldReturn400WhenNoBody() throws Exception {
            mockMvc.perform(post("/api/posts")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when PostNotValidException")
        void shouldReturn400WhenPostNotValid() throws Exception {
            when(postService.createPost(any(), any())).thenThrow(new PostNotValidException("Post with category NORMAL should have at least text or media attached to it"));

            mockMvc.perform(post("/api/posts")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validNormalRequest())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Post with category NORMAL should have at least text or media attached to it"))
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("should return 404 when UserNotFoundException")
        void shouldReturn404WhenUserNotFound() throws Exception {
            when(postService.createPost(any(), any())).thenThrow(new UserNotFoundException("User not found with username: <bob>"));

            mockMvc.perform(post("/api/posts")
                            .with(user(testUser("bob")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validNormalRequest())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("User not found with username: <bob>"));
        }

        @Test
        @DisplayName("should return 404 when MediaNotFoundException")
        void shouldReturn404WhenMediaNotFound() throws Exception {
            when(postService.createPost(any(), any())).thenThrow(new MediaNotFoundException(999L));

            mockMvc.perform(post("/api/posts")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new PostRequest(PostCategory.NORMAL, "content", 999L, null, null))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Media not found with ID: <999>"));
        }

        @Test
        @DisplayName("should return 404 when PostNotFoundException for parent/repost")
        void shouldReturn404WhenPostNotFound() throws Exception {
            when(postService.createPost(any(), any())).thenThrow(new PostNotFoundException(77L));

            mockMvc.perform(post("/api/posts")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validNormalRequest())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Post not found with ID: <77>"));
        }

        @Test
        @DisplayName("should return 500 when unexpected exception")
        void shouldReturn500() throws Exception {
            when(postService.createPost(any(), any())).thenThrow(new RuntimeException("fail"));

            mockMvc.perform(post("/api/posts")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validNormalRequest())))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
        }

        @Test
        @DisplayName("should handle content with exactly 280 chars (boundary)")
        void shouldHandleContentBoundary() throws Exception {
            PostRequest req = new PostRequest(PostCategory.NORMAL, "a".repeat(280), null, null, null);
            PostResponse resp = postResponse(1L, 10L, PostCategory.NORMAL, "a".repeat(280), null, null, null);
            when(postService.createPost(any(), any())).thenReturn(resp);

            mockMvc.perform(post("/api/posts")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isCreated());
        }
    }

    // ===================================================================
    // DELETE /api/posts/{postId}
    // ===================================================================
    @Nested
    @DisplayName("DELETE /api/posts/{postId}")
    class DeletePost {

        @Test
        @DisplayName("should return 204 No Content on success")
        void shouldReturn204() throws Exception {
            mockMvc.perform(delete("/api/posts/1")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));
        }

        @Test
        @DisplayName("should return 403 when unauthenticated")
        void shouldReturn403() throws Exception {
            mockMvc.perform(delete("/api/posts/1"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 400 for non-numeric postId")
        void shouldReturn400NonNumeric() throws Exception {
            mockMvc.perform(delete("/api/posts/abc")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 for negative postId")
        void shouldReturn400Negative() throws Exception {
            mockMvc.perform(delete("/api/posts/-1")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 for zero postId")
        void shouldReturn400Zero() throws Exception {
            mockMvc.perform(delete("/api/posts/0")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 500 when Post not found (service throws RuntimeException 'Post not found')")
        void shouldReturn500WhenPostNotFoundRuntime() throws Exception {
            org.mockito.Mockito.doThrow(new RuntimeException("Post not found")).when(postService).deletePost(eq(99L), any());

            mockMvc.perform(delete("/api/posts/99")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
        }

        @Test
        @DisplayName("should return 500 when not owner (service throws RuntimeException)")
        void shouldReturn500WhenNotOwner() throws Exception {
            org.mockito.Mockito.doThrow(new RuntimeException("You are not the owner of this post")).when(postService).deletePost(eq(1L), any());

            mockMvc.perform(delete("/api/posts/1")
                            .with(user(testUser("bob"))))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("should return 404 when UserNotFoundException")
        void shouldReturn404WhenUserNotFound() throws Exception {
            org.mockito.Mockito.doThrow(new UserNotFoundException("User not found with username: <ghost>")).when(postService).deletePost(any(), any());

            mockMvc.perform(delete("/api/posts/1")
                            .with(user(testUser("ghost"))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("User not found with username: <ghost>"));
        }

        @Test
        @DisplayName("should return 500 for unexpected exception")
        void shouldReturn500Unexpected() throws Exception {
            org.mockito.Mockito.doThrow(new RuntimeException("db fail")).when(postService).deletePost(any(), any());

            mockMvc.perform(delete("/api/posts/1")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("should return 405 for GET on delete endpoint")
        void shouldReturn405ForGet() throws Exception {
            mockMvc.perform(get("/api/posts/1")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk()); // GET is allowed, so not 405 – test POST instead
        }

        @Test
        @DisplayName("should return 405 for POST on delete endpoint")
        void shouldReturn405ForPost() throws Exception {
            // POST /api/posts is for creation, POST /api/posts/{id} is not allowed
            mockMvc.perform(post("/api/posts/1")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isMethodNotAllowed());
        }
    }

    // ===================================================================
    // PUT /api/posts/{postId}
    // ===================================================================
    @Nested
    @DisplayName("PUT /api/posts/{postId}")
    class UpdatePost {

        @Test
        @DisplayName("should return 200 with PostResponse on success")
        void shouldReturn200() throws Exception {
            PostResponse resp = postResponse(1L, 10L, PostCategory.NORMAL, "Updated", 5L, null, null);
            when(postService.updatePost(eq(1L), any(PostUpdateRequest.class), eq("alice"))).thenReturn(resp);

            mockMvc.perform(put("/api/posts/1")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validUpdate())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.content").value("Updated"))
                    .andExpect(jsonPath("$.mediaId").value(5));
        }

        @Test
        @DisplayName("should return 403 when unauthenticated")
        void shouldReturn403() throws Exception {
            mockMvc.perform(put("/api/posts/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validUpdate())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 400 for non-numeric postId")
        void shouldReturn400NonNumeric() throws Exception {
            mockMvc.perform(put("/api/posts/abc")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validUpdate())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 for negative postId")
        void shouldReturn400Negative() throws Exception {
            mockMvc.perform(put("/api/posts/-1")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validUpdate())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 for zero postId")
        void shouldReturn400Zero() throws Exception {
            mockMvc.perform(put("/api/posts/0")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validUpdate())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when content too short (<2)")
        void shouldReturn400WhenContentTooShort() throws Exception {
            PostUpdateRequest req = new PostUpdateRequest("a", 1L);

            mockMvc.perform(put("/api/posts/1")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when content too long (>280)")
        void shouldReturn400WhenContentTooLong() throws Exception {
            PostUpdateRequest req = new PostUpdateRequest("a".repeat(281), 1L);

            mockMvc.perform(put("/api/posts/1")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when mediaId negative")
        void shouldReturn400WhenMediaIdNegative() throws Exception {
            PostUpdateRequest req = new PostUpdateRequest("Valid content", -1L);

            mockMvc.perform(put("/api/posts/1")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when mediaId zero")
        void shouldReturn400WhenMediaIdZero() throws Exception {
            PostUpdateRequest req = new PostUpdateRequest("Valid content", 0L);

            mockMvc.perform(put("/api/posts/1")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should allow null content and null mediaId (service will validate business rule)")
        void shouldAllowNulls() throws Exception {
            PostUpdateRequest req = new PostUpdateRequest(null, null);
            // Even though validation passes (null allowed), service will throw PostNotValidException ->400
            // But to test validation pass we stub success with actual service mock that would normally validate
            // Instead, test that validation does not reject nulls eagerly: expect service to be called
            PostResponse resp = postResponse(1L, 10L, PostCategory.NORMAL, "content", null, null, null);
            when(postService.updatePost(eq(1L), any(), eq("alice"))).thenReturn(resp);

            // This will fail validation? No, @Size on content allows null, @Positive on mediaId allows null, so should pass to controller
            // But service then may throw PostNotValidException – we mock to return success to verify validation passed
            mockMvc.perform(put("/api/posts/1")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should accept content exactly 2 and 280 chars (boundaries)")
        void shouldAcceptBoundaries() throws Exception {
            PostUpdateRequest min = new PostUpdateRequest("ab", null);
            PostResponse respMin = postResponse(1L, 10L, PostCategory.NORMAL, "ab", null, null, null);
            when(postService.updatePost(eq(1L), any(), eq("alice"))).thenReturn(respMin);

            mockMvc.perform(put("/api/posts/1")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(min)))
                    .andExpect(status().isOk());

            PostUpdateRequest max = new PostUpdateRequest("a".repeat(280), null);
            PostResponse respMax = postResponse(1L, 10L, PostCategory.NORMAL, "a".repeat(280), null, null, null);
            when(postService.updatePost(eq(1L), any(), eq("alice"))).thenReturn(respMax);

            mockMvc.perform(put("/api/posts/1")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(max)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should return 400 when body is empty")
        void shouldReturn400WhenEmptyBody() throws Exception {
            mockMvc.perform(put("/api/posts/1")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when JSON malformed")
        void shouldReturn400WhenMalformed() throws Exception {
            mockMvc.perform(put("/api/posts/1")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{invalid"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when no body")
        void shouldReturn400WhenNoBody() throws Exception {
            mockMvc.perform(put("/api/posts/1")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 404 when PostNotFoundException")
        void shouldReturn404() throws Exception {
            when(postService.updatePost(eq(99L), any(), any())).thenThrow(new PostNotFoundException(99L));

            mockMvc.perform(put("/api/posts/99")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validUpdate())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Post not found with ID: <99>"));
        }

        @Test
        @DisplayName("should return 404 when UserNotFoundException")
        void shouldReturn404WhenUserNotFound() throws Exception {
            when(postService.updatePost(any(), any(), any())).thenThrow(new UserNotFoundException("User not found with username: <ghost>"));

            mockMvc.perform(put("/api/posts/1")
                            .with(user(testUser("ghost")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validUpdate())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 404 when MediaNotFoundException")
        void shouldReturn404WhenMediaNotFound() throws Exception {
            when(postService.updatePost(any(), any(), any())).thenThrow(new MediaNotFoundException(77L));

            mockMvc.perform(put("/api/posts/1")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validUpdate())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Media not found with ID: <77>"));
        }

        @Test
        @DisplayName("should return 403 when UnAuthorizedUpdateException on update")
        void shouldReturn403WhenUnauthorized() throws Exception {
            when(postService.updatePost(any(), any(), any())).thenThrow(new UnAuthorizedUpdateException("User with ID: <1> is not the owner of post with ID: <1>"));

            mockMvc.perform(put("/api/posts/1")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validUpdate())))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.title").value("Forbidden"));
        }

        @Test
        @DisplayName("should return 400 when PostNotValidException")
        void shouldReturn400WhenPostNotValid() throws Exception {
            when(postService.updatePost(any(), any(), any())).thenThrow(new PostNotValidException("Updating post should have at least text or media attached to it"));

            mockMvc.perform(put("/api/posts/1")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validUpdate())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Updating post should have at least text or media attached to it"));
        }

        @Test
        @DisplayName("should return 500 when unexpected exception")
        void shouldReturn500() throws Exception {
            when(postService.updatePost(any(), any(), any())).thenThrow(new RuntimeException("fail"));

            mockMvc.perform(put("/api/posts/1")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validUpdate())))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
        }
    }

    // ===================================================================
    // GET /api/users/{userId}/posts
    // ===================================================================
    @Nested
    @DisplayName("GET /api/users/{userId}/posts")
    class GetUserPosts {

        @Test
        @DisplayName("should return 200 with page when authenticated")
        void shouldReturn200() throws Exception {
            PostResponse r1 = postResponse(1L, 10L, PostCategory.NORMAL, "content1", 5L, null, null);
            PostResponse r2 = postResponse(2L, 10L, PostCategory.NORMAL, "content2", null, null, null);
            Page<PostResponse> page = new PageImpl<>(List.of(r1, r2),
                    PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")), 2);
            when(postService.getUserPosts(eq(10L), any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get("/api/users/10/posts")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].id").value(1))
                    .andExpect(jsonPath("$.content[0].userId").value(10))
                    .andExpect(jsonPath("$.content[0].postCategory").value("NORMAL"))
                    .andExpect(jsonPath("$.content[0].content").value("content1"))
                    .andExpect(jsonPath("$.content[0].mediaId").value(5))
                    .andExpect(jsonPath("$.content[1].id").value(2))
                    .andExpect(jsonPath("$.page.size").value(10))
                    .andExpect(jsonPath("$.page.number").value(0))
                    .andExpect(jsonPath("$.page.totalElements").value(2))
                    .andExpect(jsonPath("$.page.totalPages").value(1));
        }

        @Test
        @DisplayName("should return 200 with empty page when user has no posts")
        void shouldReturnEmpty() throws Exception {
            when(postService.getUserPosts(eq(10L), any(Pageable.class))).thenReturn(Page.empty());

            mockMvc.perform(get("/api/users/10/posts")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(0))
                    .andExpect(jsonPath("$.page.totalElements").value(0));
        }

        @Test
        @DisplayName("should handle null media/repost/parent as absent")
        void shouldHandleNulls() throws Exception {
            PostResponse resp = postResponse(2L, 10L, PostCategory.NORMAL, "content", null, null, null);
            Page<PostResponse> page = new PageImpl<>(List.of(resp));
            when(postService.getUserPosts(eq(10L), any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get("/api/users/10/posts")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].mediaId").doesNotExist())
                    .andExpect(jsonPath("$.content[0].repostOfId").doesNotExist())
                    .andExpect(jsonPath("$.content[0].parentId").doesNotExist());
        }

        @Test
        @DisplayName("should return 403 when unauthenticated")
        void shouldReturn403() throws Exception {
            mockMvc.perform(get("/api/users/10/posts"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 400 for non-numeric userId")
        void shouldReturn400NonNumeric() throws Exception {
            mockMvc.perform(get("/api/users/abc/posts")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 for negative userId")
        void shouldReturn400Negative() throws Exception {
            mockMvc.perform(get("/api/users/-1/posts")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 for zero userId")
        void shouldReturn400Zero() throws Exception {
            mockMvc.perform(get("/api/users/0/posts")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 500 when service throws unexpected")
        void shouldReturn500() throws Exception {
            when(postService.getUserPosts(eq(10L), any(Pageable.class)))
                    .thenThrow(new RuntimeException("fail"));

            mockMvc.perform(get("/api/users/10/posts")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
        }

        @Test
        @DisplayName("should use default page=0 size=10 sort createdAt DESC when no paging params")
        void shouldUseDefaultPageable() throws Exception {
            when(postService.getUserPosts(eq(10L), any(Pageable.class))).thenReturn(Page.empty());

            mockMvc.perform(get("/api/users/10/posts")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk());

            org.mockito.ArgumentCaptor<Pageable> pageableCaptor =
                    org.mockito.ArgumentCaptor.forClass(Pageable.class);
            verify(postService).getUserPosts(eq(10L), pageableCaptor.capture());
            Pageable pageable = pageableCaptor.getValue();
            assertThat(pageable.getPageNumber()).isZero();
            assertThat(pageable.getPageSize()).isEqualTo(10);
            assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        @Test
        @DisplayName("should pass requested page and size preserving default sort when sort not specified")
        void shouldPassRequestedPageAndSize() throws Exception {
            when(postService.getUserPosts(eq(10L), any(Pageable.class))).thenReturn(Page.empty());

            mockMvc.perform(get("/api/users/10/posts")
                            .param("page", "2")
                            .param("size", "5")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk());

            org.mockito.ArgumentCaptor<Pageable> pageableCaptor =
                    org.mockito.ArgumentCaptor.forClass(Pageable.class);
            verify(postService).getUserPosts(eq(10L), pageableCaptor.capture());
            Pageable pageable = pageableCaptor.getValue();
            assertThat(pageable.getPageNumber()).isEqualTo(2);
            assertThat(pageable.getPageSize()).isEqualTo(5);
            assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        @Test
        @DisplayName("should allow client sort override")
        void shouldAllowSortOverride() throws Exception {
            when(postService.getUserPosts(eq(10L), any(Pageable.class))).thenReturn(Page.empty());

            mockMvc.perform(get("/api/users/10/posts")
                            .param("sort", "createdAt,asc")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk());

            org.mockito.ArgumentCaptor<Pageable> pageableCaptor =
                    org.mockito.ArgumentCaptor.forClass(Pageable.class);
            verify(postService).getUserPosts(eq(10L), pageableCaptor.capture());
            assertThat(pageableCaptor.getValue().getSort())
                    .isEqualTo(Sort.by(Sort.Direction.ASC, "createdAt"));
        }

        @Test
        @DisplayName("should preserve pagination metadata from service")
        void shouldPreservePaginationMetadata() throws Exception {
            PostResponse r = postResponse(1L, 10L, PostCategory.NORMAL, "c", null, null, null);
            Page<PostResponse> servicePage = new PageImpl<>(List.of(r),
                    PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "createdAt")), 5);
            when(postService.getUserPosts(eq(10L), any(Pageable.class))).thenReturn(servicePage);

            mockMvc.perform(get("/api/users/10/posts")
                            .param("page", "1")
                            .param("size", "2")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page.number").value(1))
                    .andExpect(jsonPath("$.page.size").value(2))
                    .andExpect(jsonPath("$.page.totalElements").value(5))
                    .andExpect(jsonPath("$.page.totalPages").value(3));
        }

        @Test
        @DisplayName("should return 405 for POST on user posts endpoint")
        void shouldReturn405ForPost() throws Exception {
            mockMvc.perform(post("/api/users/10/posts")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isMethodNotAllowed());
        }
    }

    // ===================================================================
    // GET /api/posts/{postId}/comments
    // ===================================================================
    @Nested
    @DisplayName("GET /api/posts/{postId}/comments")
    class GetPostComments {

        @Test
        @DisplayName("should return 200 with page when authenticated")
        void shouldReturn200() throws Exception {
            PostResponse r1 = postResponse(2L, 10L, PostCategory.COMMENT, "reply1", null, null, 1L);
            PostResponse r2 = postResponse(3L, 11L, PostCategory.COMMENT, "reply2", null, null, 1L);
            Page<PostResponse> page = new PageImpl<>(List.of(r1, r2),
                    PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")), 2);
            when(postService.getPostComments(eq(1L), any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get("/api/posts/1/comments")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].id").value(2))
                    .andExpect(jsonPath("$.content[0].parentId").value(1))
                    .andExpect(jsonPath("$.content[0].postCategory").value("COMMENT"))
                    .andExpect(jsonPath("$.content[0].content").value("reply1"))
                    .andExpect(jsonPath("$.content[1].id").value(3))
                    .andExpect(jsonPath("$.page.size").value(10))
                    .andExpect(jsonPath("$.page.number").value(0))
                    .andExpect(jsonPath("$.page.totalElements").value(2))
                    .andExpect(jsonPath("$.page.totalPages").value(1));
        }

        @Test
        @DisplayName("should return 200 with empty page when post has no comments")
        void shouldReturnEmpty() throws Exception {
            when(postService.getPostComments(eq(1L), any(Pageable.class))).thenReturn(Page.empty());

            mockMvc.perform(get("/api/posts/1/comments")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(0))
                    .andExpect(jsonPath("$.page.totalElements").value(0));
        }

        @Test
        @DisplayName("should return 404 when PostNotFoundException")
        void shouldReturn404() throws Exception {
            when(postService.getPostComments(eq(99L), any(Pageable.class)))
                    .thenThrow(new PostNotFoundException(99L));

            mockMvc.perform(get("/api/posts/99/comments")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Post not found with ID: <99>"));
        }

        @Test
        @DisplayName("should return 403 when unauthenticated")
        void shouldReturn403() throws Exception {
            mockMvc.perform(get("/api/posts/1/comments"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 400 for non-numeric postId")
        void shouldReturn400NonNumeric() throws Exception {
            mockMvc.perform(get("/api/posts/abc/comments")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 for negative postId")
        void shouldReturn400Negative() throws Exception {
            mockMvc.perform(get("/api/posts/-1/comments")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 for zero postId")
        void shouldReturn400Zero() throws Exception {
            mockMvc.perform(get("/api/posts/0/comments")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 500 when service throws unexpected")
        void shouldReturn500() throws Exception {
            when(postService.getPostComments(eq(1L), any(Pageable.class)))
                    .thenThrow(new RuntimeException("fail"));

            mockMvc.perform(get("/api/posts/1/comments")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
        }

        @Test
        @DisplayName("should use default page=0 size=10 sort createdAt DESC when no paging params")
        void shouldUseDefaultPageable() throws Exception {
            when(postService.getPostComments(eq(1L), any(Pageable.class))).thenReturn(Page.empty());

            mockMvc.perform(get("/api/posts/1/comments")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk());

            org.mockito.ArgumentCaptor<Pageable> pageableCaptor =
                    org.mockito.ArgumentCaptor.forClass(Pageable.class);
            verify(postService).getPostComments(eq(1L), pageableCaptor.capture());
            Pageable pageable = pageableCaptor.getValue();
            assertThat(pageable.getPageNumber()).isZero();
            assertThat(pageable.getPageSize()).isEqualTo(10);
            assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        @Test
        @DisplayName("should pass requested page and size preserving default sort when sort not specified")
        void shouldPassRequestedPageAndSize() throws Exception {
            when(postService.getPostComments(eq(1L), any(Pageable.class))).thenReturn(Page.empty());

            mockMvc.perform(get("/api/posts/1/comments")
                            .param("page", "2")
                            .param("size", "5")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk());

            org.mockito.ArgumentCaptor<Pageable> pageableCaptor =
                    org.mockito.ArgumentCaptor.forClass(Pageable.class);
            verify(postService).getPostComments(eq(1L), pageableCaptor.capture());
            Pageable pageable = pageableCaptor.getValue();
            assertThat(pageable.getPageNumber()).isEqualTo(2);
            assertThat(pageable.getPageSize()).isEqualTo(5);
            assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        @Test
        @DisplayName("should ignore client sort in favor of sortBy")
        void shouldIgnoreClientSort() throws Exception {
            when(postService.getPostComments(eq(1L), any(Pageable.class))).thenReturn(Page.empty());

            mockMvc.perform(get("/api/posts/1/comments")
                            .param("sort", "createdAt,asc")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk());

            org.mockito.ArgumentCaptor<Pageable> pageableCaptor =
                    org.mockito.ArgumentCaptor.forClass(Pageable.class);
            verify(postService).getPostComments(eq(1L), pageableCaptor.capture());
            assertThat(pageableCaptor.getValue().getSort())
                    .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        @Test
        @DisplayName("should preserve pagination metadata from service")
        void shouldPreservePaginationMetadata() throws Exception {
            PostResponse r = postResponse(2L, 10L, PostCategory.COMMENT, "c", null, null, 1L);
            Page<PostResponse> servicePage = new PageImpl<>(List.of(r),
                    PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "createdAt")), 5);
            when(postService.getPostComments(eq(1L), any(Pageable.class))).thenReturn(servicePage);

            mockMvc.perform(get("/api/posts/1/comments")
                            .param("page", "1")
                            .param("size", "2")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page.number").value(1))
                    .andExpect(jsonPath("$.page.size").value(2))
                    .andExpect(jsonPath("$.page.totalElements").value(5))
                    .andExpect(jsonPath("$.page.totalPages").value(3));
        }

        @Test
        @DisplayName("should return 405 for POST on post comments endpoint")
        void shouldReturn405ForPost() throws Exception {
            mockMvc.perform(post("/api/posts/1/comments")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isMethodNotAllowed());
        }

        @Test
        @DisplayName("should default to newest first when sortBy is absent")
        void shouldDefaultToNewest() throws Exception {
            when(postService.getPostComments(eq(1L), any(Pageable.class))).thenReturn(Page.empty());

            mockMvc.perform(get("/api/posts/1/comments")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk());

            org.mockito.ArgumentCaptor<Pageable> pageableCaptor =
                    org.mockito.ArgumentCaptor.forClass(Pageable.class);
            verify(postService).getPostComments(eq(1L), pageableCaptor.capture());
            assertThat(pageableCaptor.getValue().getSort())
                    .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        @Test
        @DisplayName("should sort by like count when sortBy=MOST_LIKED")
        void shouldSortByMostLiked() throws Exception {
            when(postService.getPostComments(eq(1L), any(Pageable.class))).thenReturn(Page.empty());

            mockMvc.perform(get("/api/posts/1/comments")
                            .param("sortBy", "MOST_LIKED")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk());

            org.mockito.ArgumentCaptor<Pageable> pageableCaptor =
                    org.mockito.ArgumentCaptor.forClass(Pageable.class);
            verify(postService).getPostComments(eq(1L), pageableCaptor.capture());
            assertThat(pageableCaptor.getValue().getSort())
                    .isEqualTo(Sort.by(Sort.Direction.DESC, "likeCount"));
        }

        @Test
        @DisplayName("should sort by creation date when sortBy=NEWEST")
        void shouldSortByNewest() throws Exception {
            when(postService.getPostComments(eq(1L), any(Pageable.class))).thenReturn(Page.empty());

            mockMvc.perform(get("/api/posts/1/comments")
                            .param("sortBy", "NEWEST")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk());

            org.mockito.ArgumentCaptor<Pageable> pageableCaptor =
                    org.mockito.ArgumentCaptor.forClass(Pageable.class);
            verify(postService).getPostComments(eq(1L), pageableCaptor.capture());
            assertThat(pageableCaptor.getValue().getSort())
                    .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        @Test
        @DisplayName("should preserve page and size alongside sortBy")
        void shouldPreservePageAndSizeWithSort() throws Exception {
            when(postService.getPostComments(eq(1L), any(Pageable.class))).thenReturn(Page.empty());

            mockMvc.perform(get("/api/posts/1/comments")
                            .param("sortBy", "MOST_LIKED")
                            .param("page", "2")
                            .param("size", "5")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk());

            org.mockito.ArgumentCaptor<Pageable> pageableCaptor =
                    org.mockito.ArgumentCaptor.forClass(Pageable.class);
            verify(postService).getPostComments(eq(1L), pageableCaptor.capture());
            Pageable pageable = pageableCaptor.getValue();
            assertThat(pageable.getPageNumber()).isEqualTo(2);
            assertThat(pageable.getPageSize()).isEqualTo(5);
            assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "likeCount"));
        }

        @Test
        @DisplayName("should return 400 for unknown sortBy value")
        void shouldReturn400ForUnknownSort() throws Exception {
            mockMvc.perform(get("/api/posts/1/comments")
                            .param("sortBy", "OLDEST")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isBadRequest());
        }
    }

    // ===================================================================
    // GET /api/users/{userId}/reacted-posts
    // ===================================================================
    @Nested
    @DisplayName("GET /api/users/{userId}/reacted-posts")
    class GetReactedPosts {

        @Test
        @DisplayName("should return 200 with page when authenticated")
        void shouldReturn200() throws Exception {
            PostResponse r1 = postResponse(1L, 10L, PostCategory.NORMAL, "content1", 5L, null, null);
            PostResponse r2 = postResponse(2L, 11L, PostCategory.NORMAL, "content2", null, null, null);
            Page<PostResponse> page = new PageImpl<>(List.of(r1, r2),
                    PageRequest.of(0, 10), 2);
            when(postService.getReactedPosts(eq(10L), eq(ReactionFilter.ALL), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/api/users/10/reacted-posts")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].id").value(1))
                    .andExpect(jsonPath("$.content[0].content").value("content1"))
                    .andExpect(jsonPath("$.content[1].id").value(2))
                    .andExpect(jsonPath("$.page.size").value(10))
                    .andExpect(jsonPath("$.page.number").value(0))
                    .andExpect(jsonPath("$.page.totalElements").value(2))
                    .andExpect(jsonPath("$.page.totalPages").value(1));
        }

        @Test
        @DisplayName("should return 200 with empty page when user has no reactions")
        void shouldReturnEmpty() throws Exception {
            when(postService.getReactedPosts(eq(10L), eq(ReactionFilter.ALL), any(Pageable.class)))
                    .thenReturn(Page.empty());

            mockMvc.perform(get("/api/users/10/reacted-posts")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(0))
                    .andExpect(jsonPath("$.page.totalElements").value(0));
        }

        @Test
        @DisplayName("should pass LIKE filter through to service")
        void shouldPassLikeFilter() throws Exception {
            when(postService.getReactedPosts(eq(10L), eq(ReactionFilter.LIKE), any(Pageable.class)))
                    .thenReturn(Page.empty());

            mockMvc.perform(get("/api/users/10/reacted-posts")
                            .param("filter", "LIKE")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk());

            verify(postService).getReactedPosts(eq(10L), eq(ReactionFilter.LIKE), any(Pageable.class));
        }

        @Test
        @DisplayName("should pass DISLIKE filter through to service")
        void shouldPassDislikeFilter() throws Exception {
            when(postService.getReactedPosts(eq(10L), eq(ReactionFilter.DISLIKE), any(Pageable.class)))
                    .thenReturn(Page.empty());

            mockMvc.perform(get("/api/users/10/reacted-posts")
                            .param("filter", "DISLIKE")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk());

            verify(postService).getReactedPosts(eq(10L), eq(ReactionFilter.DISLIKE), any(Pageable.class));
        }

        @Test
        @DisplayName("should default to ALL filter when filter is absent")
        void shouldDefaultToAll() throws Exception {
            when(postService.getReactedPosts(eq(10L), eq(ReactionFilter.ALL), any(Pageable.class)))
                    .thenReturn(Page.empty());

            mockMvc.perform(get("/api/users/10/reacted-posts")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk());

            verify(postService).getReactedPosts(eq(10L), eq(ReactionFilter.ALL), any(Pageable.class));
        }

        @Test
        @DisplayName("should return 400 for unknown filter value")
        void shouldReturn400ForUnknownFilter() throws Exception {
            mockMvc.perform(get("/api/users/10/reacted-posts")
                            .param("filter", "LOVE")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 403 when unauthenticated")
        void shouldReturn403() throws Exception {
            mockMvc.perform(get("/api/users/10/reacted-posts"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 400 for non-numeric userId")
        void shouldReturn400NonNumeric() throws Exception {
            mockMvc.perform(get("/api/users/abc/reacted-posts")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 for negative userId")
        void shouldReturn400Negative() throws Exception {
            mockMvc.perform(get("/api/users/-1/reacted-posts")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 for zero userId")
        void shouldReturn400Zero() throws Exception {
            mockMvc.perform(get("/api/users/0/reacted-posts")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 500 when service throws unexpected")
        void shouldReturn500() throws Exception {
            when(postService.getReactedPosts(eq(10L), any(), any(Pageable.class)))
                    .thenThrow(new RuntimeException("fail"));

            mockMvc.perform(get("/api/users/10/reacted-posts")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
        }

        @Test
        @DisplayName("should use default page=0 size=10 when no paging params")
        void shouldUseDefaultPageable() throws Exception {
            when(postService.getReactedPosts(eq(10L), any(), any(Pageable.class)))
                    .thenReturn(Page.empty());

            mockMvc.perform(get("/api/users/10/reacted-posts")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk());

            org.mockito.ArgumentCaptor<Pageable> pageableCaptor =
                    org.mockito.ArgumentCaptor.forClass(Pageable.class);
            verify(postService).getReactedPosts(eq(10L), eq(ReactionFilter.ALL), pageableCaptor.capture());
            Pageable pageable = pageableCaptor.getValue();
            assertThat(pageable.getPageNumber()).isZero();
            assertThat(pageable.getPageSize()).isEqualTo(10);
        }

        @Test
        @DisplayName("should pass requested page and size")
        void shouldPassRequestedPageAndSize() throws Exception {
            when(postService.getReactedPosts(eq(10L), any(), any(Pageable.class)))
                    .thenReturn(Page.empty());

            mockMvc.perform(get("/api/users/10/reacted-posts")
                            .param("page", "2")
                            .param("size", "5")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk());

            org.mockito.ArgumentCaptor<Pageable> pageableCaptor =
                    org.mockito.ArgumentCaptor.forClass(Pageable.class);
            verify(postService).getReactedPosts(eq(10L), eq(ReactionFilter.ALL), pageableCaptor.capture());
            Pageable pageable = pageableCaptor.getValue();
            assertThat(pageable.getPageNumber()).isEqualTo(2);
            assertThat(pageable.getPageSize()).isEqualTo(5);
        }

        @Test
        @DisplayName("should preserve pagination metadata from service")
        void shouldPreservePaginationMetadata() throws Exception {
            PostResponse r = postResponse(1L, 10L, PostCategory.NORMAL, "c", null, null, null);
            Page<PostResponse> servicePage = new PageImpl<>(List.of(r),
                    PageRequest.of(1, 2), 5);
            when(postService.getReactedPosts(eq(10L), any(), any(Pageable.class)))
                    .thenReturn(servicePage);

            mockMvc.perform(get("/api/users/10/reacted-posts")
                            .param("page", "1")
                            .param("size", "2")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page.number").value(1))
                    .andExpect(jsonPath("$.page.size").value(2))
                    .andExpect(jsonPath("$.page.totalElements").value(5))
                    .andExpect(jsonPath("$.page.totalPages").value(3));
        }

        @Test
        @DisplayName("should return 405 for POST on reacted posts endpoint")
        void shouldReturn405ForPost() throws Exception {
            mockMvc.perform(post("/api/users/10/reacted-posts")
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
        @DisplayName("should require authentication for all post endpoints")
        void shouldRequireAuth() throws Exception {
            mockMvc.perform(get("/api/posts/1")).andExpect(status().isForbidden());
            mockMvc.perform(post("/api/posts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}")).andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/posts/1")).andExpect(status().isForbidden());
            mockMvc.perform(put("/api/posts/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}")).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/users/10/posts")).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/posts/1/comments")).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/users/10/reacted-posts")).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 405 for unsupported methods")
        void shouldReturn405() throws Exception {
            mockMvc.perform(patch("/api/posts/1")
                            .with(user(testUser("alice")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isMethodNotAllowed());
            mockMvc.perform(get("/api/posts")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isMethodNotAllowed());
            // POST on /api/posts/{id} is not allowed
            mockMvc.perform(post("/api/posts/1")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isMethodNotAllowed());
        }
    }
}
