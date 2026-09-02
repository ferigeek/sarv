package com.github.ferigeek.sarv.controller;

import com.github.ferigeek.sarv.dto.response.PostResponse;
import com.github.ferigeek.sarv.entity.Media;
import com.github.ferigeek.sarv.entity.Post;
import com.github.ferigeek.sarv.entity.User;
import com.github.ferigeek.sarv.entity.type.PostCategory;
import com.github.ferigeek.sarv.exception.UserNotFoundException;
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
@Import({SecurityConfig.class, FeedControllerRecommendedTest.TestJwtFilterConfig.class})
@DisplayName("FeedController - Recommended")
class FeedControllerRecommendedTest {

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

    private UserDetails testUser(String username) {
        return new org.springframework.security.core.userdetails.User(username, "password", List.of());
    }

    private PostResponse postResponse(Long id, Long userId) {
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
        post.setPostCategory(PostCategory.NORMAL);
        post.setContent("content" + id);
        post.setCreatedAt(OffsetDateTime.now());
        post.setViewCount(5L);
        post.setLikeCount(2L);
        post.setDislikeCount(1L);
        return new PostResponse(post);
    }

    @Nested
    @DisplayName("GET /api/feed/recommended")
    class GetRecommended {

        @Test
        @DisplayName("should return 200 with page when authenticated")
        void shouldReturn200() throws Exception {
            PostResponse r1 = postResponse(1L, 10L);
            PostResponse r2 = postResponse(2L, 11L);
            Page<PostResponse> page = new PageImpl<>(List.of(r1, r2), PageRequest.of(0, 20), 2);
            when(feedService.getRecommended(eq("alice"), any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get("/api/feed/recommended")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].id").value(1))
                    .andExpect(jsonPath("$.content[0].userId").value(10))
                    .andExpect(jsonPath("$.content[0].content").value("content1"))
                    .andExpect(jsonPath("$.content[1].id").value(2))
                    .andExpect(jsonPath("$.page.size").value(20))
                    .andExpect(jsonPath("$.page.number").value(0))
                    .andExpect(jsonPath("$.page.totalElements").value(2));
        }

        @Test
        @DisplayName("should return 200 with empty page (fallback or no recommendations)")
        void shouldReturnEmpty() throws Exception {
            when(feedService.getRecommended(eq("alice"), any(Pageable.class))).thenReturn(Page.empty());

            mockMvc.perform(get("/api/feed/recommended")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(0))
                    .andExpect(jsonPath("$.page.totalElements").value(0));
        }

        @Test
        @DisplayName("should return identical shape to chronological (Page<PostResponse>)")
        void shouldBeIdenticalShape() throws Exception {
            PostResponse r = postResponse(5L, 10L);
            Page<PostResponse> page = new PageImpl<>(List.of(r), PageRequest.of(0, 20), 1);
            when(feedService.getRecommended(any(), any())).thenReturn(page);

            mockMvc.perform(get("/api/feed/recommended")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").exists())
                    .andExpect(jsonPath("$.content[0].userId").exists())
                    .andExpect(jsonPath("$.content[0].postCategory").value("NORMAL"))
                    .andExpect(jsonPath("$.content[0].viewCount").exists())
                    .andExpect(jsonPath("$.content[0].likeCount").exists())
                    .andExpect(jsonPath("$.content[0].dislikeCount").exists())
                    .andExpect(jsonPath("$.page").exists());
        }

        @Test
        @DisplayName("should return 403 when unauthenticated")
        void shouldReturn403() throws Exception {
            mockMvc.perform(get("/api/feed/recommended"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 404 when UserNotFoundException")
        void shouldReturn404WhenUserNotFound() throws Exception {
            when(feedService.getRecommended(eq("ghost"), any(Pageable.class)))
                    .thenThrow(new UserNotFoundException("User not found with username: <ghost>"));

            mockMvc.perform(get("/api/feed/recommended")
                            .with(user(testUser("ghost"))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("User not found with username: <ghost>"));
        }

        @Test
        @DisplayName("should return 500 when unexpected exception (fallback also failed)")
        void shouldReturn500() throws Exception {
            when(feedService.getRecommended(any(), any())).thenThrow(new RuntimeException("fail"));

            mockMvc.perform(get("/api/feed/recommended")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
        }

        @Test
        @DisplayName("should use default page=0 size=20 unsorted when no paging params")
        void shouldUseDefaultPageable() throws Exception {
            when(feedService.getRecommended(any(), any(Pageable.class))).thenReturn(Page.empty());

            mockMvc.perform(get("/api/feed/recommended")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk());

            org.mockito.ArgumentCaptor<Pageable> captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
            verify(feedService).getRecommended(eq("alice"), captor.capture());
            Pageable pageable = captor.getValue();
            assertThat(pageable.getPageNumber()).isZero();
            assertThat(pageable.getPageSize()).isEqualTo(20);
            assertThat(pageable.getSort().isUnsorted()).isTrue();
        }

        @Test
        @DisplayName("should forward requested page and size to service ignoring sort")
        void shouldForwardPageAndSizeIgnoringSort() throws Exception {
            when(feedService.getRecommended(any(), any(Pageable.class))).thenReturn(Page.empty());

            mockMvc.perform(get("/api/feed/recommended")
                            .param("page", "2")
                            .param("size", "5")
                            .param("sort", "createdAt,desc")
                            .with(user(testUser("alice"))))
                    .andExpect(status().isOk());

            org.mockito.ArgumentCaptor<Pageable> captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
            verify(feedService).getRecommended(eq("alice"), captor.capture());
            Pageable pageable = captor.getValue();
            assertThat(pageable.getPageNumber()).isEqualTo(2);
            assertThat(pageable.getPageSize()).isEqualTo(5);
            // sorting is ignored for recommended
            assertThat(pageable.getSort().isUnsorted()).isTrue();
        }

        @Test
        @DisplayName("should use principal username for recommendation")
        void shouldUsePrincipal() throws Exception {
            Page<PostResponse> page = Page.empty();
            when(feedService.getRecommended(eq("bob"), any())).thenReturn(page);

            mockMvc.perform(get("/api/feed/recommended")
                            .with(user(testUser("bob"))))
                    .andExpect(status().isOk());

            verify(feedService).getRecommended(eq("bob"), any());
        }

        @Test
        @DisplayName("should preserve pagination metadata from service")
        void shouldPreservePaginationMetadata() throws Exception {
            PostResponse r = postResponse(1L, 1L);
            Page<PostResponse> servicePage = new PageImpl<>(List.of(r), PageRequest.of(1, 10), 25);
            when(feedService.getRecommended(any(), any())).thenReturn(servicePage);

            mockMvc.perform(get("/api/feed/recommended")
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
        @DisplayName("should return 405 for POST, PUT, DELETE on recommended")
        void shouldReturn405ForWrongMethods() throws Exception {
            mockMvc.perform(post("/api/feed/recommended").with(user(testUser("alice"))))
                    .andExpect(status().isMethodNotAllowed());
            mockMvc.perform(put("/api/feed/recommended").with(user(testUser("alice"))))
                    .andExpect(status().isMethodNotAllowed());
            mockMvc.perform(delete("/api/feed/recommended").with(user(testUser("alice"))))
                    .andExpect(status().isMethodNotAllowed());
        }
    }

    @Nested
    @DisplayName("HTTP contract")
    class HttpContract {

        @Test
        @DisplayName("should require authentication for recommended")
        void shouldRequireAuth() throws Exception {
            mockMvc.perform(get("/api/feed/recommended"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("chronological and recommended both require auth")
        void bothRequireAuth() throws Exception {
            mockMvc.perform(get("/api/feed/chronological"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/feed/recommended"))
                    .andExpect(status().isForbidden());
        }
    }
}
