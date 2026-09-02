package com.github.ferigeek.sarv.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.ferigeek.sarv.dto.response.PostResponse;
import com.github.ferigeek.sarv.entity.Media;
import com.github.ferigeek.sarv.entity.Post;
import com.github.ferigeek.sarv.entity.User;
import com.github.ferigeek.sarv.entity.type.PostCategory;
import com.github.ferigeek.sarv.repository.EventLogRepository;
import com.github.ferigeek.sarv.repository.PostRepository;
import com.github.ferigeek.sarv.repository.UserRepository;
import com.github.ferigeek.sarv.security.JwtAuthFilter;
import com.github.ferigeek.sarv.security.JwtUtil;
import com.github.ferigeek.sarv.security.SecurityConfig;
import com.github.ferigeek.sarv.service.CustomUserDetailsService;
import com.github.ferigeek.sarv.service.FeedService;
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

@WebMvcTest(FeedController.class)
@Import({SecurityConfig.class, FeedControllerTest.TestJwtFilterConfig.class})
@DisplayName("FeedController")
class FeedControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FeedService feedService;

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

    private final ObjectMapper objectMapper = new ObjectMapper();

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

    // ===================================================================
    // GET /api/feed/chronological
    // ===================================================================
    @Nested
    @DisplayName("GET /api/feed/chronological")
    class GetChronological {

        @Test
        @DisplayName("should return 200 with page when authenticated")
        void shouldReturn200() throws Exception {
            PostResponse r1 = postResponse(1L, 10L, PostCategory.NORMAL, "content1", 5L, null, null);
            PostResponse r2 = postResponse(2L, 11L, PostCategory.NORMAL, "content2", null, null, null);
            Page<PostResponse> page = new PageImpl<>(List.of(r1, r2), PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")), 2);
            when(feedService.getChronological(any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get("/api/feed/chronological")
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
                    .andExpect(jsonPath("$.content[0].viewCount").value(5))
                    .andExpect(jsonPath("$.content[1].id").value(2))
                    .andExpect(jsonPath("$.page.size").value(20))
                    .andExpect(jsonPath("$.page.number").value(0))
                    .andExpect(jsonPath("$.page.totalElements").value(2))
                    .andExpect(jsonPath("$.page.totalPages").value(1));
        }

        @Test
        @DisplayName("should return 200 with empty page")
        void shouldReturnEmpty() throws Exception {
            when(feedService.getChronological(any(Pageable.class))).thenReturn(Page.empty());

            mockMvc.perform(get("/api/feed/chronological")
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
            when(feedService.getChronological(any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get("/api/feed/chronological")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].mediaId").doesNotExist())
                    .andExpect(jsonPath("$.content[0].repostOfId").doesNotExist())
                    .andExpect(jsonPath("$.content[0].parentId").doesNotExist());
        }

        @Test
        @DisplayName("should return 403 when unauthenticated")
        void shouldReturn403() throws Exception {
            mockMvc.perform(get("/api/feed/chronological"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 500 when service throws unexpected")
        void shouldReturn500() throws Exception {
            when(feedService.getChronological(any(Pageable.class))).thenThrow(new RuntimeException("fail"));

            mockMvc.perform(get("/api/feed/chronological")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
        }

        @Test
        @DisplayName("should use default page=0 size=20 sort createdAt DESC when no paging params")
        void shouldUseDefaultPageable() throws Exception {
            when(feedService.getChronological(any(Pageable.class))).thenReturn(Page.empty());

            mockMvc.perform(get("/api/feed/chronological")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk());

            org.mockito.ArgumentCaptor<Pageable> captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
            verify(feedService).getChronological(captor.capture());
            Pageable pageable = captor.getValue();
            assertThat(pageable.getPageNumber()).isZero();
            assertThat(pageable.getPageSize()).isEqualTo(20);
            assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        @Test
        @DisplayName("should pass requested page and size to the service preserving default sort when sort not specified")
        void shouldPassRequestedPageAndSize() throws Exception {
            when(feedService.getChronological(any(Pageable.class))).thenReturn(Page.empty());

            mockMvc.perform(get("/api/feed/chronological")
                            .param("page", "2")
                            .param("size", "5")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk());

            org.mockito.ArgumentCaptor<Pageable> captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
            verify(feedService).getChronological(captor.capture());
            Pageable pageable = captor.getValue();
            assertThat(pageable.getPageNumber()).isEqualTo(2);
            assertThat(pageable.getPageSize()).isEqualTo(5);
            // default sort should still apply
            assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        @Test
        @DisplayName("should allow client sort override")
        void shouldAllowSortOverride() throws Exception {
            when(feedService.getChronological(any(Pageable.class))).thenReturn(Page.empty());

            mockMvc.perform(get("/api/feed/chronological")
                            .param("sort", "createdAt,asc")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk());

            org.mockito.ArgumentCaptor<Pageable> captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
            verify(feedService).getChronological(captor.capture());
            assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "createdAt"));
        }

        @Test
        @DisplayName("should preserve pagination metadata from service")
        void shouldPreservePaginationMetadata() throws Exception {
            PostResponse r = postResponse(1L, 1L, PostCategory.NORMAL, "c", null, null, null);
            Page<PostResponse> servicePage = new PageImpl<>(List.of(r), PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "createdAt")), 25);
            when(feedService.getChronological(any(Pageable.class))).thenReturn(servicePage);

            mockMvc.perform(get("/api/feed/chronological")
                            .param("page", "1")
                            .param("size", "10")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page.number").value(1))
                    .andExpect(jsonPath("$.page.size").value(10))
                    .andExpect(jsonPath("$.page.totalElements").value(25))
                    .andExpect(jsonPath("$.page.totalPages").value(3));
        }

        @Test
        @DisplayName("should return 405 for POST on chronological feed endpoint")
        void shouldReturn405ForPost() throws Exception {
            mockMvc.perform(post("/api/feed/chronological")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isMethodNotAllowed());
        }

        @Test
        @DisplayName("should return 405 for PUT and DELETE")
        void shouldReturn405ForPutDelete() throws Exception {
            mockMvc.perform(put("/api/feed/chronological")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isMethodNotAllowed());
            mockMvc.perform(delete("/api/feed/chronological")
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
        @DisplayName("should require authentication for feed endpoint")
        void shouldRequireAuth() throws Exception {
            mockMvc.perform(get("/api/feed/chronological"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/feed/chronological"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should handle page with many elements")
        void shouldHandleManyElements() throws Exception {
            List<PostResponse> many = List.of(
                    postResponse(1L, 1L, PostCategory.NORMAL, "a", null, null, null),
                    postResponse(2L, 1L, PostCategory.NORMAL, "b", null, null, null),
                    postResponse(3L, 1L, PostCategory.NORMAL, "c", null, null, null)
            );
            Page<PostResponse> page = new PageImpl<>(many, PageRequest.of(0, 20), 100);
            when(feedService.getChronological(any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get("/api/feed/chronological")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(3))
                    .andExpect(jsonPath("$.page.totalElements").value(100))
                    .andExpect(jsonPath("$.page.totalPages").value(5));
        }
    }
}
