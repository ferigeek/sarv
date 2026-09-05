package com.github.ferigeek.sarv.repository;

import com.github.ferigeek.sarv.entity.Post;
import com.github.ferigeek.sarv.entity.Reaction;
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
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
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

    @Autowired
    private ReactionRepository reactionRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User owner;
    private User other;

    @BeforeEach
    void setUp() {
        reactionRepository.deleteAll();
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
        post.setCommentCount(0L);
        return postRepository.saveAndFlush(post);
    }

    private Post newComment(User user, Post parent, String content, OffsetDateTime createdAt) {
        Post post = new Post();
        post.setUser(user);
        post.setPostCategory(PostCategory.COMMENT);
        post.setContent(content);
        post.setCreatedAt(createdAt);
        post.setParent(parent);
        post.setViewCount(0L);
        post.setLikeCount(0L);
        post.setDislikeCount(0L);
        post.setCommentCount(0L);
        return postRepository.saveAndFlush(post);
    }

    private Reaction newReaction(User user, Post post, short reactionType, OffsetDateTime createdAt) {
        Reaction reaction = new Reaction();
        reaction.setUser(user);
        reaction.setPost(post);
        reaction.setReactionType(reactionType);
        reaction.setCreatedAt(createdAt);
        return reactionRepository.saveAndFlush(reaction);
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

    @Nested
    @DisplayName("findCommentsByParentId")
    class FindCommentsByParentId {

        @Test
        @DisplayName("should return only comments of the given post")
        void shouldReturnOnlyOwnComments() {
            Post parent = newPost(owner, "parent", OffsetDateTime.now().minusHours(3));
            Post otherParent = newPost(owner, "other-parent", OffsetDateTime.now().minusHours(2));
            newComment(owner, parent, "c1", OffsetDateTime.now().minusHours(1));
            newComment(other, parent, "c2", OffsetDateTime.now());
            newComment(owner, otherParent, "other", OffsetDateTime.now());
            newPost(owner, "standalone", OffsetDateTime.now());

            Page<Post> page = postRepository.findCommentsByParentId(
                    parent.getId(), PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent())
                    .allSatisfy(post -> assertThat(post.getParent().getId()).isEqualTo(parent.getId()));
            assertThat(page.getContent())
                    .map(Post::getContent)
                    .containsExactlyInAnyOrder("c1", "c2");
        }

        @Test
        @DisplayName("should exclude soft-deleted comments")
        void shouldExcludeSoftDeleted() {
            Post parent = newPost(owner, "parent", OffsetDateTime.now().minusHours(2));
            Post visible = newComment(owner, parent, "visible", OffsetDateTime.now().minusHours(1));
            Post deleted = newComment(owner, parent, "deleted", OffsetDateTime.now());
            deleted.setDeletedAt(OffsetDateTime.now());
            postRepository.saveAndFlush(deleted);

            Page<Post> page = postRepository.findCommentsByParentId(
                    parent.getId(), PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent()).map(Post::getId).containsExactly(visible.getId());
        }

        @Test
        @DisplayName("should return empty page for unknown post")
        void shouldReturnEmptyForUnknownPost() {
            Post parent = newPost(owner, "parent", OffsetDateTime.now());
            newComment(owner, parent, "c1", OffsetDateTime.now());

            Page<Post> page = postRepository.findCommentsByParentId(
                    999_999L, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isZero();
            assertThat(page.getContent()).isEmpty();
        }

        @Test
        @DisplayName("should honor page and size")
        void shouldHonorPageAndSize() {
            Post parent = newPost(owner, "parent", OffsetDateTime.now().minusHours(1));
            for (int i = 0; i < 5; i++) {
                newComment(owner, parent, "c-" + i, OffsetDateTime.now().minusMinutes(i));
            }

            Page<Post> first = postRepository.findCommentsByParentId(
                    parent.getId(), PageRequest.of(0, 2));
            Page<Post> second = postRepository.findCommentsByParentId(
                    parent.getId(), PageRequest.of(1, 2));
            Page<Post> third = postRepository.findCommentsByParentId(
                    parent.getId(), PageRequest.of(2, 2));

            assertThat(first.getContent()).hasSize(2);
            assertThat(second.getContent()).hasSize(2);
            assertThat(third.getContent()).hasSize(1);
            assertThat(first.getTotalElements()).isEqualTo(5);
            assertThat(first.getTotalPages()).isEqualTo(3);
        }

        @Test
        @DisplayName("should honor Pageable sort on createdAt")
        void shouldHonorSortOnCreatedAt() {
            Post parent = newPost(owner, "parent", OffsetDateTime.now().minusDays(3));
            OffsetDateTime now = OffsetDateTime.now();
            Post oldest = newComment(owner, parent, "oldest", now.minusDays(2));
            Post newest = newComment(owner, parent, "newest", now);
            Post middle = newComment(owner, parent, "middle", now.minusDays(1));

            Pageable desc = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
            List<Long> descIds = postRepository.findCommentsByParentId(parent.getId(), desc)
                    .map(Post::getId).getContent();
            assertThat(descIds).containsExactly(newest.getId(), middle.getId(), oldest.getId());

            Pageable asc = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "createdAt"));
            List<Long> ascIds = postRepository.findCommentsByParentId(parent.getId(), asc)
                    .map(Post::getId).getContent();
            assertThat(ascIds).containsExactly(oldest.getId(), middle.getId(), newest.getId());
        }

        @Test
        @DisplayName("should honor Pageable sort on likeCount")
        void shouldHonorSortOnLikeCount() {
            Post parent = newPost(owner, "parent", OffsetDateTime.now().minusDays(1));
            Post least = newComment(owner, parent, "least", OffsetDateTime.now().minusHours(3));
            least.setLikeCount(1L);
            postRepository.saveAndFlush(least);
            Post most = newComment(owner, parent, "most", OffsetDateTime.now().minusHours(2));
            most.setLikeCount(10L);
            postRepository.saveAndFlush(most);
            Post mid = newComment(owner, parent, "mid", OffsetDateTime.now().minusHours(1));
            mid.setLikeCount(5L);
            postRepository.saveAndFlush(mid);

            Pageable mostLiked = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "likeCount"));
            List<Long> ids = postRepository.findCommentsByParentId(parent.getId(), mostLiked)
                    .map(Post::getId).getContent();
            assertThat(ids).containsExactly(most.getId(), mid.getId(), least.getId());
        }
    }

    @Nested
    @DisplayName("incrementViewCounts")
    class IncrementViewCounts {

        @Test
        @DisplayName("should increment view counts of the given posts only")
        void shouldIncrementGivenPosts() {
            Post p1 = newPost(owner, "p1", OffsetDateTime.now().minusHours(2));
            Post p2 = newPost(owner, "p2", OffsetDateTime.now().minusHours(1));
            Post untouched = newPost(other, "other", OffsetDateTime.now());

            postRepository.incrementViewCounts(List.of(p1.getId(), p2.getId()));
            // Bulk updates bypass the persistence context, so clear it to read fresh state
            entityManager.clear();

            assertThat(postRepository.findById(p1.getId()).orElseThrow().getViewCount()).isEqualTo(1L);
            assertThat(postRepository.findById(p2.getId()).orElseThrow().getViewCount()).isEqualTo(1L);
            assertThat(postRepository.findById(untouched.getId()).orElseThrow().getViewCount()).isZero();
        }

        @Test
        @DisplayName("should accumulate on top of existing view counts")
        void shouldAccumulate() {
            Post p = newPost(owner, "p", OffsetDateTime.now());
            p.setViewCount(5L);
            postRepository.saveAndFlush(p);

            postRepository.incrementViewCounts(List.of(p.getId()));
            // Bulk updates bypass the persistence context, so clear it to read fresh state
            entityManager.clear();

            assertThat(postRepository.findById(p.getId()).orElseThrow().getViewCount()).isEqualTo(6L);
        }
    }

    @Nested
    @DisplayName("findReactedPosts")
    class FindReactedPosts {

        @Test
        @DisplayName("should return only posts the user reacted to, newest reactions first")
        void shouldReturnOwnReactedPostsNewestFirst() {
            Post first = newPost(other, "first", OffsetDateTime.now().minusDays(2));
            Post second = newPost(other, "second", OffsetDateTime.now().minusDays(1));
            Post unreacted = newPost(other, "unreacted", OffsetDateTime.now());
            newReaction(owner, first, Reaction.LIKE, OffsetDateTime.now().minusHours(2));
            newReaction(owner, second, Reaction.DISLIKE, OffsetDateTime.now().minusHours(1));

            Page<Post> page = postRepository.findReactedPosts(
                    owner.getId(), null, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent()).map(Post::getId)
                    .containsExactly(second.getId(), first.getId());
            assertThat(page.getContent()).map(Post::getId).doesNotContain(unreacted.getId());
        }

        @Test
        @DisplayName("should filter by like reaction type")
        void shouldFilterLikes() {
            Post liked = newPost(other, "liked", OffsetDateTime.now().minusHours(2));
            Post disliked = newPost(other, "disliked", OffsetDateTime.now().minusHours(1));
            newReaction(owner, liked, Reaction.LIKE, OffsetDateTime.now().minusHours(2));
            newReaction(owner, disliked, Reaction.DISLIKE, OffsetDateTime.now().minusHours(1));

            Page<Post> page = postRepository.findReactedPosts(
                    owner.getId(), Reaction.LIKE, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent()).map(Post::getId).containsExactly(liked.getId());
        }

        @Test
        @DisplayName("should filter by dislike reaction type")
        void shouldFilterDislikes() {
            Post liked = newPost(other, "liked", OffsetDateTime.now().minusHours(2));
            Post disliked = newPost(other, "disliked", OffsetDateTime.now().minusHours(1));
            newReaction(owner, liked, Reaction.LIKE, OffsetDateTime.now().minusHours(2));
            newReaction(owner, disliked, Reaction.DISLIKE, OffsetDateTime.now().minusHours(1));

            Page<Post> page = postRepository.findReactedPosts(
                    owner.getId(), Reaction.DISLIKE, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent()).map(Post::getId).containsExactly(disliked.getId());
        }

        @Test
        @DisplayName("should not return other users reactions")
        void shouldNotLeakOtherUsersReactions() {
            Post p = newPost(other, "p", OffsetDateTime.now());
            newReaction(other, p, Reaction.LIKE, OffsetDateTime.now());

            Page<Post> page = postRepository.findReactedPosts(
                    owner.getId(), null, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isZero();
            assertThat(page.getContent()).isEmpty();
        }

        @Test
        @DisplayName("should exclude soft-deleted posts")
        void shouldExcludeSoftDeleted() {
            Post visible = newPost(other, "visible", OffsetDateTime.now().minusHours(2));
            Post deleted = newPost(other, "deleted", OffsetDateTime.now().minusHours(1));
            newReaction(owner, visible, Reaction.LIKE, OffsetDateTime.now().minusHours(2));
            newReaction(owner, deleted, Reaction.LIKE, OffsetDateTime.now().minusHours(1));
            deleted.setDeletedAt(OffsetDateTime.now());
            postRepository.saveAndFlush(deleted);

            Page<Post> page = postRepository.findReactedPosts(
                    owner.getId(), null, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent()).map(Post::getId).containsExactly(visible.getId());
        }

        @Test
        @DisplayName("should return empty page for unknown user")
        void shouldReturnEmptyForUnknownUser() {
            Post p = newPost(owner, "p", OffsetDateTime.now());
            newReaction(owner, p, Reaction.LIKE, OffsetDateTime.now());

            Page<Post> page = postRepository.findReactedPosts(
                    999_999L, null, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isZero();
            assertThat(page.getContent()).isEmpty();
        }

        @Test
        @DisplayName("should honor page and size")
        void shouldHonorPageAndSize() {
            for (int i = 0; i < 5; i++) {
                Post p = newPost(other, "p-" + i, OffsetDateTime.now().minusDays(1));
                newReaction(owner, p, Reaction.LIKE, OffsetDateTime.now().minusMinutes(i));
            }

            Page<Post> first = postRepository.findReactedPosts(
                    owner.getId(), null, PageRequest.of(0, 2));
            Page<Post> second = postRepository.findReactedPosts(
                    owner.getId(), null, PageRequest.of(1, 2));
            Page<Post> third = postRepository.findReactedPosts(
                    owner.getId(), null, PageRequest.of(2, 2));

            assertThat(first.getContent()).hasSize(2);
            assertThat(second.getContent()).hasSize(2);
            assertThat(third.getContent()).hasSize(1);
            assertThat(first.getTotalElements()).isEqualTo(5);
            assertThat(first.getTotalPages()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("searchPosts")
    class SearchPosts {

        @Test
        @DisplayName("should return posts whose content contains the query")
        void shouldReturnMatchingPosts() {
            newPost(owner, "hello world", OffsetDateTime.now().minusHours(2));
            newPost(other, "say hello", OffsetDateTime.now().minusHours(1));
            newPost(owner, "unrelated", OffsetDateTime.now());

            Page<Post> page = postRepository.searchPosts("hello", PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent())
                    .map(Post::getContent)
                    .containsExactlyInAnyOrder("hello world", "say hello");
        }

        @Test
        @DisplayName("should match case-insensitively")
        void shouldMatchCaseInsensitively() {
            newPost(owner, "Hello World", OffsetDateTime.now().minusHours(1));
            newPost(other, "HELLO again", OffsetDateTime.now());

            Page<Post> page = postRepository.searchPosts("hello", PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("should exclude soft-deleted posts")
        void shouldExcludeSoftDeleted() {
            Post visible = newPost(owner, "hello visible", OffsetDateTime.now().minusHours(2));
            Post deleted = newPost(owner, "hello deleted", OffsetDateTime.now().minusHours(1));
            deleted.setDeletedAt(OffsetDateTime.now());
            postRepository.saveAndFlush(deleted);

            Page<Post> page = postRepository.searchPosts("hello", PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent()).map(Post::getId).containsExactly(visible.getId());
        }

        @Test
        @DisplayName("should return empty page when nothing matches")
        void shouldReturnEmptyWhenNoMatch() {
            newPost(owner, "hello world", OffsetDateTime.now());

            Page<Post> page = postRepository.searchPosts("nomatch", PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isZero();
            assertThat(page.getContent()).isEmpty();
        }

        @Test
        @DisplayName("should honor page and size")
        void shouldHonorPageAndSize() {
            for (int i = 0; i < 5; i++) {
                newPost(owner, "hello-" + i, OffsetDateTime.now().minusMinutes(i));
            }

            Page<Post> first = postRepository.searchPosts("hello", PageRequest.of(0, 2));
            Page<Post> second = postRepository.searchPosts("hello", PageRequest.of(1, 2));
            Page<Post> third = postRepository.searchPosts("hello", PageRequest.of(2, 2));

            assertThat(first.getContent()).hasSize(2);
            assertThat(second.getContent()).hasSize(2);
            assertThat(third.getContent()).hasSize(1);
            assertThat(first.getTotalElements()).isEqualTo(5);
            assertThat(first.getTotalPages()).isEqualTo(3);
        }
    }
}
