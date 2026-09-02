package com.github.ferigeek.sarv.service;

import com.github.ferigeek.sarv.client.RankedPost;
import com.github.ferigeek.sarv.client.RecommendationClient;
import com.github.ferigeek.sarv.client.RecommendationResponse;
import com.github.ferigeek.sarv.dto.response.PostResponse;
import com.github.ferigeek.sarv.entity.Post;
import com.github.ferigeek.sarv.entity.User;
import com.github.ferigeek.sarv.entity.type.PostCategory;
import com.github.ferigeek.sarv.exception.UserNotFoundException;
import com.github.ferigeek.sarv.repository.PostRepository;
import com.github.ferigeek.sarv.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedServiceRecommendedTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RecommendationClient recommendationClient;

    @InjectMocks
    private FeedService feedService;

    private User alice;

    @BeforeEach
    void setUp() {
        alice = new User();
        alice.setId(42L);
        alice.setUsername("alice");
        alice.setDisplayName("Alice");
        alice.setEmail("alice@example.com");
        alice.setPasswordHash("hash");
        alice.setCreatedAt(OffsetDateTime.now());
        alice.setStatus(com.github.ferigeek.sarv.entity.type.UserStatus.ACTIVE);
        alice.setGender(com.github.ferigeek.sarv.entity.type.Gender.MALE);
    }

    private Post post(Long id, Long userId) {
        User u = new User();
        u.setId(userId);
        u.setUsername("user" + userId);
        u.setDisplayName("User");
        u.setEmail("u@example.com");
        u.setPasswordHash("hash");
        u.setCreatedAt(OffsetDateTime.now());
        u.setStatus(com.github.ferigeek.sarv.entity.type.UserStatus.ACTIVE);
        u.setGender(com.github.ferigeek.sarv.entity.type.Gender.MALE);
        Post p = new Post();
        p.setId(id);
        p.setUser(u);
        p.setPostCategory(PostCategory.NORMAL);
        p.setContent("content" + id);
        p.setCreatedAt(OffsetDateTime.now());
        p.setViewCount(5L);
        p.setLikeCount(2L);
        p.setDislikeCount(1L);
        return p;
    }

    private RecommendationResponse recResponse(List<String> ids, int page, int size, int total) {
        List<RankedPost> posts = ids.stream().map(id -> new RankedPost(id, 10.0)).toList();
        return new RecommendationResponse("42", posts, page, size, total);
    }

    @Nested
    @DisplayName("getRecommended")
    class GetRecommended {

        @Test
        @DisplayName("should hydrate posts preserving rank order")
        void shouldHydratePreservingRankOrder() {
            Pageable pageable = PageRequest.of(0, 20);
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(recommendationClient.getRecommendations(42L, 0, 20))
                    .thenReturn(recResponse(List.of("3", "1", "2"), 0, 20, 3));
            // hyd returns out of order
            Post p1 = post(1L, 1L);
            Post p2 = post(2L, 1L);
            Post p3 = post(3L, 1L);
            when(postRepository.findAllByIdsFiltered(List.of(3L, 1L, 2L)))
                    .thenReturn(List.of(p1, p2, p3));

            Page<PostResponse> res = feedService.getRecommended("alice", pageable);

            assertThat(res.getContent()).extracting(PostResponse::getId).containsExactly(3L, 1L, 2L);
            assertThat(res.getTotalElements()).isEqualTo(3);
            assertThat(res.getContent().get(0).getContent()).isEqualTo("content3");
            verify(recommendationClient).getRecommendations(42L, 0, 20);
            verify(postRepository).findAllByIdsFiltered(List.of(3L, 1L, 2L));
        }

        @Test
        @DisplayName("should fallback to chronological when recommendation empty")
        void shouldFallbackWhenEmpty() {
            Pageable pageable = PageRequest.of(0, 10);
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(recommendationClient.getRecommendations(42L, 0, 10))
                    .thenReturn(recResponse(List.of(), 0, 10, 0));
            Post p = post(99L, 1L);
            Page<Post> chrono = new PageImpl<>(List.of(p), pageable, 1);
            when(postRepository.findChronologicalFeed(pageable)).thenReturn(chrono);

            Page<PostResponse> res = feedService.getRecommended("alice", pageable);

            assertThat(res.getContent()).hasSize(1);
            assertThat(res.getContent().get(0).getId()).isEqualTo(99L);
            verify(postRepository).findChronologicalFeed(pageable);
            verify(postRepository, never()).findAllByIdsFiltered(any());
        }

        @Test
        @DisplayName("should fallback to chronological on exception (timeout)")
        void shouldFallbackOnException() {
            Pageable pageable = PageRequest.of(1, 5);
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(recommendationClient.getRecommendations(42L, 1, 5))
                    .thenThrow(new RuntimeException("timeout"));
            Post p = post(1L, 1L);
            Page<Post> chrono = new PageImpl<>(List.of(p), pageable, 1);
            when(postRepository.findChronologicalFeed(pageable)).thenReturn(chrono);

            Page<PostResponse> res = feedService.getRecommended("alice", pageable);

            assertThat(res.getContent().get(0).getId()).isEqualTo(1L);
            verify(postRepository).findChronologicalFeed(pageable);
        }

        @Test
        @DisplayName("should fallback when recommendation returns null posts")
        void shouldFallbackWhenNullPosts() {
            Pageable pageable = PageRequest.of(0, 20);
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(recommendationClient.getRecommendations(42L, 0, 20))
                    .thenReturn(new RecommendationResponse("42", null, 0, 20, 0));
            Page<Post> chrono = new PageImpl<>(List.of(post(1L, 1L)), pageable, 1);
            when(postRepository.findChronologicalFeed(pageable)).thenReturn(chrono);

            Page<PostResponse> res = feedService.getRecommended("alice", pageable);

            assertThat(res.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("should skip invalid post_id strings")
        void shouldSkipInvalidPostId() {
            Pageable pageable = PageRequest.of(0, 20);
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(recommendationClient.getRecommendations(42L, 0, 20))
                    .thenReturn(recResponse(List.of("1", "abc", "2"), 0, 20, 3));
            Post p1 = post(1L, 1L);
            Post p2 = post(2L, 1L);
            when(postRepository.findAllByIdsFiltered(List.of(1L, 2L)))
                    .thenReturn(List.of(p1, p2));

            Page<PostResponse> res = feedService.getRecommended("alice", pageable);

            assertThat(res.getContent()).extracting(PostResponse::getId).containsExactly(1L, 2L);
        }

        @Test
        @DisplayName("should filter out deleted posts even if recommender returns them")
        void shouldFilterDeleted() {
            Pageable pageable = PageRequest.of(0, 20);
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(recommendationClient.getRecommendations(42L, 0, 20))
                    .thenReturn(recResponse(List.of("1", "2"), 0, 20, 2));
            Post p1 = post(1L, 1L);
            Post p2 = post(2L, 1L);
            p2.setDeletedAt(OffsetDateTime.now()); // simulate deleted but somehow returned from DB
            when(postRepository.findAllByIdsFiltered(List.of(1L, 2L)))
                    .thenReturn(List.of(p1, p2));

            Page<PostResponse> res = feedService.getRecommended("alice", pageable);

            assertThat(res.getContent()).extracting(PostResponse::getId).containsExactly(1L);
        }

        @Test
        @DisplayName("should handle missing hydrated posts (not found in DB)")
        void shouldHandleMissingHydrated() {
            Pageable pageable = PageRequest.of(0, 20);
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(recommendationClient.getRecommendations(42L, 0, 20))
                    .thenReturn(recResponse(List.of("1", "2", "3"), 0, 20, 3));
            // DB only has 1 and 3, 2 missing
            when(postRepository.findAllByIdsFiltered(List.of(1L, 2L, 3L)))
                    .thenReturn(List.of(post(1L, 1L), post(3L, 1L)));

            Page<PostResponse> res = feedService.getRecommended("alice", pageable);

            assertThat(res.getContent()).extracting(PostResponse::getId).containsExactly(1L, 3L);
        }

        @Test
        @DisplayName("should propagate UserNotFoundException, not fallback")
        void shouldPropagateUserNotFound() {
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            assertThrows(UserNotFoundException.class, () -> feedService.getRecommended("ghost", PageRequest.of(0, 20)));

            verify(recommendationClient, never()).getRecommendations(any(), anyInt(), anyInt());
            verify(postRepository, never()).findChronologicalFeed(any());
        }

        @Test
        @DisplayName("should use page and size from pageable for recommendation call")
        void shouldUsePageAndSize() {
            Pageable pageable = PageRequest.of(2, 5);
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(recommendationClient.getRecommendations(42L, 2, 5))
                    .thenReturn(recResponse(List.of("1"), 2, 5, 50));
            when(postRepository.findAllByIdsFiltered(List.of(1L)))
                    .thenReturn(List.of(post(1L, 1L)));

            Page<PostResponse> res = feedService.getRecommended("alice", pageable);

            assertThat(res.getPageable().getPageNumber()).isEqualTo(2);
            assertThat(res.getPageable().getPageSize()).isEqualTo(5);
            assertThat(res.getTotalElements()).isEqualTo(50);
            verify(recommendationClient).getRecommendations(42L, 2, 5);
        }

        @Test
        @DisplayName("should keep total from recommendation response")
        void shouldKeepTotalFromResponse() {
            Pageable pageable = PageRequest.of(0, 1);
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(recommendationClient.getRecommendations(42L, 0, 1))
                    .thenReturn(new RecommendationResponse("42", List.of(new RankedPost("1", 5.0)), 0, 1, 100));
            when(postRepository.findAllByIdsFiltered(List.of(1L)))
                    .thenReturn(List.of(post(1L, 1L)));

            Page<PostResponse> res = feedService.getRecommended("alice", pageable);

            assertThat(res.getTotalElements()).isEqualTo(100);
            assertThat(res.getTotalPages()).isEqualTo(100);
        }
    }
}
