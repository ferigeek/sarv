package com.github.ferigeek.sarv.repository;

import com.github.ferigeek.sarv.entity.Post;
import com.github.ferigeek.sarv.entity.User;
import com.github.ferigeek.sarv.entity.type.Gender;
import com.github.ferigeek.sarv.entity.type.PostCategory;
import com.github.ferigeek.sarv.entity.type.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("PostRepository#findPostsByUserId")
class PostRepositoryTest {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    private User owner;
    private User other;

    @BeforeEach
    void setUp() {
        postRepository.deleteAll();
        userRepository.deleteAll();

        owner = newUser("owner", "owner@example.com");
        other = newUser("other", "other@example.com");
    }

    private User newUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setDisplayName(username);
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setCreatedAt(OffsetDateTime.now());
        user.setStatus(UserStatus.ACTIVE);
        user.setGender(Gender.MALE);
        return userRepository.saveAndFlush(user);
    }

    private Post newPost(User user, String content, OffsetDateTime createdAt) {
        Post post = new Post();
        post.setUser(user);
        post.setPostCategory(PostCategory.NORMAL);
        post.setContent(content);
        post.setCreatedAt(createdAt);
        post.setViewCount(0L);
        post.setLikeCount(0L);
        post.setDislikeCount(0L);
        return postRepository.saveAndFlush(post);
    }

    @Nested
    @DisplayName("filtering")
    class Filtering {

        @Test
        @DisplayName("should return only posts of the given user")
        void shouldReturnOnlyOwnerPosts() {
            newPost(owner, "owner-1", OffsetDateTime.now().minusHours(2));
            newPost(owner, "owner-2", OffsetDateTime.now().minusHours(1));
            newPost(other, "other-1", OffsetDateTime.now());

            Page<Post> page = postRepository.findPostsByUserId(
                    owner.getId(), PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent())
                    .allSatisfy(post -> assertThat(post.getUser().getId()).isEqualTo(owner.getId()));
            assertThat(page.getContent())
                    .map(Post::getContent)
                    .containsExactlyInAnyOrder("owner-1", "owner-2");
        }

        @Test
        @DisplayName("should exclude soft-deleted posts")
        void shouldExcludeSoftDeleted() {
            Post visible = newPost(owner, "visible", OffsetDateTime.now().minusHours(1));
            Post deleted = newPost(owner, "deleted", OffsetDateTime.now());
            deleted.setDeletedAt(OffsetDateTime.now());
            postRepository.saveAndFlush(deleted);

            Page<Post> page = postRepository.findPostsByUserId(
                    owner.getId(), PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent()).map(Post::getId).containsExactly(visible.getId());
        }

        @Test
        @DisplayName("should not return other user's soft-deleted posts either")
        void shouldNotLeakOtherUsersPosts() {
            newPost(other, "other-1", OffsetDateTime.now());

            Page<Post> page = postRepository.findPostsByUserId(
                    owner.getId(), PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isZero();
            assertThat(page.getContent()).isEmpty();
        }

        @Test
        @DisplayName("should return empty page for unknown user")
        void shouldReturnEmptyForUnknownUser() {
            newPost(owner, "owner-1", OffsetDateTime.now());

            Page<Post> page = postRepository.findPostsByUserId(
                    999_999L, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isZero();
            assertThat(page.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("pagination and sorting")
    class PaginationAndSorting {

        @Test
        @DisplayName("should honor page and size")
        void shouldHonorPageAndSize() {
            for (int i = 0; i < 5; i++) {
                newPost(owner, "post-" + i, OffsetDateTime.now().minusMinutes(i));
            }

            Page<Post> first = postRepository.findPostsByUserId(
                    owner.getId(), PageRequest.of(0, 2));
            Page<Post> second = postRepository.findPostsByUserId(
                    owner.getId(), PageRequest.of(1, 2));
            Page<Post> third = postRepository.findPostsByUserId(
                    owner.getId(), PageRequest.of(2, 2));

            assertThat(first.getContent()).hasSize(2);
            assertThat(second.getContent()).hasSize(2);
            assertThat(third.getContent()).hasSize(1);
            assertThat(first.getTotalElements()).isEqualTo(5);
            assertThat(first.getTotalPages()).isEqualTo(3);
        }

        @Test
        @DisplayName("should honor Pageable sort on createdAt")
        void shouldHonorSort() {
            OffsetDateTime now = OffsetDateTime.now();
            Post oldest = newPost(owner, "oldest", now.minusDays(2));
            Post newest = newPost(owner, "newest", now);
            Post middle = newPost(owner, "middle", now.minusDays(1));

            Pageable desc = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
            List<Long> descIds = postRepository.findPostsByUserId(owner.getId(), desc)
                    .map(Post::getId).getContent();
            assertThat(descIds).containsExactly(newest.getId(), middle.getId(), oldest.getId());

            Pageable asc = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "createdAt"));
            List<Long> ascIds = postRepository.findPostsByUserId(owner.getId(), asc)
                    .map(Post::getId).getContent();
            assertThat(ascIds).containsExactly(oldest.getId(), middle.getId(), newest.getId());
        }
    }
}
