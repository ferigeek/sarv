package com.github.ferigeek.sarv.service;

import com.github.ferigeek.sarv.dto.response.PostResponse;
import com.github.ferigeek.sarv.entity.Media;
import com.github.ferigeek.sarv.entity.Post;
import com.github.ferigeek.sarv.entity.User;
import com.github.ferigeek.sarv.entity.type.PostCategory;
import com.github.ferigeek.sarv.repository.PostRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private FeedService feedService;

    private User owner;
    private Media media;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);
        owner.setUsername("owner");
        owner.setDisplayName("Owner");
        owner.setEmail("owner@example.com");
        owner.setPasswordHash("hash");
        owner.setCreatedAt(OffsetDateTime.now());
        owner.setStatus(com.github.ferigeek.sarv.entity.type.UserStatus.ACTIVE);
        owner.setGender(com.github.ferigeek.sarv.entity.type.Gender.MALE);

        media = new Media();
        media.setId(10L);
        media.setSize(100L);
        media.setMimeType("image/png");
        media.setSha256("abc");
        media.setCreatedAt(OffsetDateTime.now());
    }

    private Post post(Long id, Long userId, String content, Long mediaId, Long repostId, Long parentId, OffsetDateTime createdAt) {
        User user = new User();
        user.setId(userId);
        user.setUsername("user" + userId);
        user.setDisplayName("User " + userId);
        user.setEmail("user" + userId + "@example.com");
        user.setPasswordHash("hash");
        user.setCreatedAt(OffsetDateTime.now());
        user.setStatus(com.github.ferigeek.sarv.entity.type.UserStatus.ACTIVE);
        user.setGender(com.github.ferigeek.sarv.entity.type.Gender.MALE);

        Post p = new Post();
        p.setId(id);
        p.setUser(user);
        p.setPostCategory(PostCategory.NORMAL);
        p.setContent(content);
        p.setCreatedAt(createdAt != null ? createdAt : OffsetDateTime.now());
        p.setViewCount(5L);
        p.setLikeCount(2L);
        p.setDislikeCount(1L);
        if (mediaId != null) {
            Media m = new Media();
            m.setId(mediaId);
            p.setMedia(m);
        }
        if (repostId != null) {
            Post repost = new Post();
            repost.setId(repostId);
            repost.setUser(user);
            p.setRepostOf(repost);
        }
        if (parentId != null) {
            Post parent = new Post();
            parent.setId(parentId);
            parent.setUser(user);
            p.setParent(parent);
        }
        return p;
    }

    @Nested
    @DisplayName("getChronological")
    class GetChronological {

        @Test
        @DisplayName("should return mapped page preserving order and fields")
        void shouldReturnMappedPage() {
            OffsetDateTime now = OffsetDateTime.now();
            Post p1 = post(1L, 10L, "content1", 5L, null, null, now);
            Post p2 = post(2L, 11L, "content2", null, null, null, now.minusHours(1));
            Pageable pageable = PageRequest.of(0, 20);
            when(postRepository.findChronologicalFeed(pageable))
                    .thenReturn(new PageImpl<>(List.of(p1, p2), pageable, 2));

            Page<PostResponse> res = feedService.getChronological(pageable);

            assertThat(res.getContent()).hasSize(2);
            assertThat(res.getTotalElements()).isEqualTo(2);
            // order preserved
            assertThat(res.getContent().get(0).getId()).isEqualTo(1L);
            assertThat(res.getContent().get(1).getId()).isEqualTo(2L);
            // mapping checks
            assertThat(res.getContent().get(0).getUserId()).isEqualTo(10L);
            assertThat(res.getContent().get(0).getContent()).isEqualTo("content1");
            assertThat(res.getContent().get(0).getMediaId()).isEqualTo(5L);
            assertThat(res.getContent().get(0).getViewCount()).isEqualTo(6L);
            assertThat(res.getContent().get(0).getLikeCount()).isEqualTo(2L);
            assertThat(res.getContent().get(0).getDislikeCount()).isEqualTo(1L);
            assertThat(res.getContent().get(1).getMediaId()).isNull();
        }

        @Test
        @DisplayName("should map null media/repost/parent to null ids")
        void shouldMapNullRelations() {
            Post p = post(1L, 10L, "hello", null, null, null, OffsetDateTime.now());
            Pageable pageable = PageRequest.of(0, 20);
            when(postRepository.findChronologicalFeed(pageable))
                    .thenReturn(new PageImpl<>(List.of(p)));

            Page<PostResponse> res = feedService.getChronological(pageable);

            assertThat(res.getContent().get(0).getMediaId()).isNull();
            assertThat(res.getContent().get(0).getRepostOfId()).isNull();
            assertThat(res.getContent().get(0).getParentId()).isNull();
        }

        @Test
        @DisplayName("should map media, repost and parent ids when present")
        void shouldMapAllRelations() {
            Post p = post(1L, 10L, "hello", 10L, 20L, 30L, OffsetDateTime.now());
            Pageable pageable = PageRequest.of(0, 20);
            when(postRepository.findChronologicalFeed(pageable))
                    .thenReturn(new PageImpl<>(List.of(p)));

            Page<PostResponse> res = feedService.getChronological(pageable);

            assertThat(res.getContent().get(0).getMediaId()).isEqualTo(10L);
            assertThat(res.getContent().get(0).getRepostOfId()).isEqualTo(20L);
            assertThat(res.getContent().get(0).getParentId()).isEqualTo(30L);
        }

        @Test
        @DisplayName("should return empty page when no posts")
        void shouldReturnEmptyWhenNoPosts() {
            Pageable pageable = PageRequest.of(0, 20);
            when(postRepository.findChronologicalFeed(pageable)).thenReturn(Page.empty());

            Page<PostResponse> res = feedService.getChronological(pageable);

            assertThat(res).isEmpty();
            assertThat(res.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("should keep pagination metadata from repository")
        void shouldKeepPaginationMetadata() {
            Post p = post(1L, 10L, "hello", null, null, null, OffsetDateTime.now());
            Pageable pageable = PageRequest.of(1, 10);
            when(postRepository.findChronologicalFeed(pageable))
                    .thenReturn(new PageImpl<>(List.of(p), PageRequest.of(1, 10), 25));

            Page<PostResponse> res = feedService.getChronological(pageable);

            assertThat(res.getNumber()).isEqualTo(1);
            assertThat(res.getSize()).isEqualTo(10);
            assertThat(res.getTotalElements()).isEqualTo(25);
            assertThat(res.getTotalPages()).isEqualTo(3);
        }

        @Test
        @DisplayName("should delegate to repository with correct pageable")
        void shouldDelegateWithCorrectPageable() {
            Pageable pageable = PageRequest.of(2, 5);
            when(postRepository.findChronologicalFeed(pageable)).thenReturn(Page.empty());

            feedService.getChronological(pageable);

            verify(postRepository).findChronologicalFeed(pageable);
        }

        @Test
        @DisplayName("should preserve repository order (newest first is repository responsibility)")
        void shouldPreserveOrder() {
            OffsetDateTime base = OffsetDateTime.now();
            Post p1 = post(3L, 1L, "c", null, null, null, base);
            Post p2 = post(1L, 1L, "a", null, null, null, base.minusDays(2));
            Post p3 = post(2L, 1L, "b", null, null, null, base.minusDays(1));
            Pageable pageable = PageRequest.of(0, 20);
            when(postRepository.findChronologicalFeed(pageable))
                    .thenReturn(new PageImpl<>(List.of(p1, p3, p2)));

            Page<PostResponse> res = feedService.getChronological(pageable);

            assertThat(res.getContent()).extracting(PostResponse::getId).containsExactly(3L, 2L, 1L);
        }

        @Test
        @DisplayName("should handle different page requests correctly")
        void shouldHandleDifferentPages() {
            Pageable p0 = PageRequest.of(0, 10);
            Pageable p1 = PageRequest.of(1, 10);
            when(postRepository.findChronologicalFeed(p0)).thenReturn(new PageImpl<>(List.of(post(1L, 1L, "a", null, null, null, OffsetDateTime.now())), p0, 15));
            when(postRepository.findChronologicalFeed(p1)).thenReturn(new PageImpl<>(List.of(post(2L, 1L, "b", null, null, null, OffsetDateTime.now())), p1, 15));

            Page<PostResponse> r0 = feedService.getChronological(p0);
            Page<PostResponse> r1 = feedService.getChronological(p1);

            assertThat(r0.getNumber()).isZero();
            assertThat(r1.getNumber()).isEqualTo(1);
            assertThat(r0.getContent().get(0).getId()).isEqualTo(1L);
            assertThat(r1.getContent().get(0).getId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("should increment view counts of served posts and return incremented values")
        void shouldIncrementViewCounts() {
            Post p1 = post(1L, 10L, "content1", null, null, null, OffsetDateTime.now());
            Post p2 = post(2L, 11L, "content2", null, null, null, OffsetDateTime.now());
            Pageable pageable = PageRequest.of(0, 20);
            when(postRepository.findChronologicalFeed(pageable))
                    .thenReturn(new PageImpl<>(List.of(p1, p2), pageable, 2));

            Page<PostResponse> res = feedService.getChronological(pageable);

            verify(postRepository).incrementViewCounts(List.of(1L, 2L));
            assertThat(res.getContent()).extracting(PostResponse::getViewCount)
                    .containsExactly(6L, 6L);
        }

        @Test
        @DisplayName("should not increment view counts when feed is empty")
        void shouldNotIncrementWhenEmpty() {
            Pageable pageable = PageRequest.of(0, 20);
            when(postRepository.findChronologicalFeed(pageable)).thenReturn(Page.empty(pageable));

            Page<PostResponse> res = feedService.getChronological(pageable);

            assertThat(res).isEmpty();
            verify(postRepository, org.mockito.Mockito.never()).incrementViewCounts(any());
        }
    }
}
