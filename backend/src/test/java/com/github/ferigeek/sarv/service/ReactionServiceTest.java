package com.github.ferigeek.sarv.service;

import com.github.ferigeek.sarv.dto.request.ReactionRequest;
import com.github.ferigeek.sarv.dto.response.ReactionResponse;
import com.github.ferigeek.sarv.entity.Post;
import com.github.ferigeek.sarv.entity.Reaction;
import com.github.ferigeek.sarv.entity.User;
import com.github.ferigeek.sarv.entity.type.PostCategory;
import com.github.ferigeek.sarv.exception.PostNotFoundException;
import com.github.ferigeek.sarv.exception.UserNotFoundException;
import com.github.ferigeek.sarv.repository.PostRepository;
import com.github.ferigeek.sarv.repository.ReactionRepository;
import com.github.ferigeek.sarv.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReactionServiceTest {

    @Mock
    private ReactionRepository reactionRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReactionService reactionService;

    private User alice;
    private Post post;

    @BeforeEach
    void setUp() {
        alice = new User();
        alice.setId(1L);
        alice.setUsername("alice");
        alice.setDisplayName("Alice");
        alice.setEmail("alice@example.com");
        alice.setPasswordHash("hash");
        alice.setCreatedAt(OffsetDateTime.now());

        post = new Post();
        post.setId(100L);
        post.setUser(alice);
        post.setPostCategory(PostCategory.NORMAL);
        post.setContent("hello");
        post.setCreatedAt(OffsetDateTime.now());
        post.setLikeCount(5L);
        post.setDislikeCount(2L);
        post.setViewCount(10L);
    }

    private Reaction existingReaction(short type) {
        Reaction r = new Reaction();
        r.setId(10L);
        r.setPost(post);
        r.setUser(alice);
        r.setReactionType(type);
        r.setCreatedAt(OffsetDateTime.now().minusDays(1));
        return r;
    }

    private ReactionRequest req(short type) {
        ReactionRequest r = new ReactionRequest();
        r.setReactionType(type);
        return r;
    }

    // -----------------------------------------------------------------------
    // addReaction
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("addReaction")
    class AddReaction {

        @Test
        @DisplayName("should throw PostNotFoundException when post not found")
        void shouldThrowWhenPostNotFound() {
            when(postRepository.findById(99L)).thenReturn(Optional.empty());

            ReactionRequest request = req(Reaction.LIKE);
            assertThrows(PostNotFoundException.class, () -> reactionService.addReaction(99L, request, "alice"));
            verify(reactionRepository, never()).findByPostAndUser(any(), any());
        }

        @Test
        @DisplayName("should throw UserNotFoundException when user not found")
        void shouldThrowWhenUserNotFound() {
            when(postRepository.findById(100L)).thenReturn(Optional.of(post));
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            ReactionRequest request = req(Reaction.LIKE);
            UserNotFoundException ex = assertThrows(UserNotFoundException.class,
                    () -> reactionService.addReaction(100L, request, "ghost"));
            assertThat(ex.getMessage()).contains("ghost");
        }

        @Test
        @DisplayName("should create new LIKE and increment likeCount")
        void shouldCreateLike() {
            when(postRepository.findById(100L)).thenReturn(Optional.of(post));
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(reactionRepository.findByPostAndUser(post, alice)).thenReturn(Optional.empty());
            when(reactionRepository.save(any(Reaction.class))).thenAnswer(inv -> inv.getArgument(0));
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

            ReactionRequest request = req(Reaction.LIKE);
            ReactionResponse res = reactionService.addReaction(100L, request, "alice");

            assertThat(res.getLikeCount()).isEqualTo(6L);
            assertThat(res.getDislikeCount()).isEqualTo(2L);
            assertThat(res.getUserReaction()).isEqualTo(Reaction.LIKE);
            assertThat(post.getLikeCount()).isEqualTo(6L);

            ArgumentCaptor<Reaction> captor = ArgumentCaptor.forClass(Reaction.class);
            verify(reactionRepository).save(captor.capture());
            Reaction saved = captor.getValue();
            assertThat(saved.getPost()).isEqualTo(post);
            assertThat(saved.getUser()).isEqualTo(alice);
            assertThat(saved.getReactionType()).isEqualTo(Reaction.LIKE);
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getCreatedAt()).isAfter(OffsetDateTime.now().minusSeconds(5));

            verify(postRepository).save(post);
        }

        @Test
        @DisplayName("should create new DISLIKE and increment dislikeCount")
        void shouldCreateDislike() {
            when(postRepository.findById(100L)).thenReturn(Optional.of(post));
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(reactionRepository.findByPostAndUser(post, alice)).thenReturn(Optional.empty());
            when(reactionRepository.save(any(Reaction.class))).thenAnswer(inv -> inv.getArgument(0));
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

            ReactionRequest request = req(Reaction.DISLIKE);
            ReactionResponse res = reactionService.addReaction(100L, request, "alice");

            assertThat(res.getLikeCount()).isEqualTo(5L);
            assertThat(res.getDislikeCount()).isEqualTo(3L);
            assertThat(res.getUserReaction()).isEqualTo(Reaction.DISLIKE);
            assertThat(post.getDislikeCount()).isEqualTo(3L);
        }

        @Test
        @DisplayName("should be idempotent when existing same LIKE")
        void shouldBeIdempotentLike() {
            Reaction existing = existingReaction(Reaction.LIKE);
            when(postRepository.findById(100L)).thenReturn(Optional.of(post));
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(reactionRepository.findByPostAndUser(post, alice)).thenReturn(Optional.of(existing));

            ReactionRequest request = req(Reaction.LIKE);
            ReactionResponse res = reactionService.addReaction(100L, request, "alice");

            assertThat(res.getLikeCount()).isEqualTo(5L);
            assertThat(res.getDislikeCount()).isEqualTo(2L);
            assertThat(res.getUserReaction()).isEqualTo(Reaction.LIKE);
            // no saves
            verify(reactionRepository, never()).save(any());
            verify(postRepository, never()).save(any());
            assertThat(post.getLikeCount()).isEqualTo(5L);
            assertThat(post.getDislikeCount()).isEqualTo(2L);
        }

        @Test
        @DisplayName("should be idempotent when existing same DISLIKE")
        void shouldBeIdempotentDislike() {
            Reaction existing = existingReaction(Reaction.DISLIKE);
            when(postRepository.findById(100L)).thenReturn(Optional.of(post));
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(reactionRepository.findByPostAndUser(post, alice)).thenReturn(Optional.of(existing));

            ReactionRequest request = req(Reaction.DISLIKE);
            ReactionResponse res = reactionService.addReaction(100L, request, "alice");

            assertThat(res.getUserReaction()).isEqualTo(Reaction.DISLIKE);
            verify(reactionRepository, never()).save(any());
            verify(postRepository, never()).save(any());
        }

        @Test
        @DisplayName("should change LIKE to DISLIKE and adjust counts")
        void shouldChangeLikeToDislike() {
            Reaction existing = existingReaction(Reaction.LIKE);
            when(postRepository.findById(100L)).thenReturn(Optional.of(post));
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(reactionRepository.findByPostAndUser(post, alice)).thenReturn(Optional.of(existing));
            when(reactionRepository.save(any(Reaction.class))).thenAnswer(inv -> inv.getArgument(0));
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

            // post starts 5 likes, 2 dislikes
            ReactionRequest request = req(Reaction.DISLIKE);
            ReactionResponse res = reactionService.addReaction(100L, request, "alice");

            assertThat(existing.getReactionType()).isEqualTo(Reaction.DISLIKE);
            assertThat(post.getLikeCount()).isEqualTo(4L);
            assertThat(post.getDislikeCount()).isEqualTo(3L);
            assertThat(res.getLikeCount()).isEqualTo(4L);
            assertThat(res.getDislikeCount()).isEqualTo(3L);
            assertThat(res.getUserReaction()).isEqualTo(Reaction.DISLIKE);
            verify(reactionRepository).save(existing);
            verify(postRepository).save(post);
        }

        @Test
        @DisplayName("should change DISLIKE to LIKE and adjust counts")
        void shouldChangeDislikeToLike() {
            Reaction existing = existingReaction(Reaction.DISLIKE);
            when(postRepository.findById(100L)).thenReturn(Optional.of(post));
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(reactionRepository.findByPostAndUser(post, alice)).thenReturn(Optional.of(existing));
            when(reactionRepository.save(any(Reaction.class))).thenAnswer(inv -> inv.getArgument(0));
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

            ReactionRequest request = req(Reaction.LIKE);
            ReactionResponse res = reactionService.addReaction(100L, request, "alice");

            assertThat(post.getLikeCount()).isEqualTo(6L);
            assertThat(post.getDislikeCount()).isEqualTo(1L);
            assertThat(res.getLikeCount()).isEqualTo(6L);
            assertThat(res.getDislikeCount()).isEqualTo(1L);
            assertThat(res.getUserReaction()).isEqualTo(Reaction.LIKE);
        }

        @Test
        @DisplayName("should handle 0 reactionType as no count change (current behavior)")
        void shouldHandleZeroTypeAsNoCountChange() {
            when(postRepository.findById(100L)).thenReturn(Optional.of(post));
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(reactionRepository.findByPostAndUser(post, alice)).thenReturn(Optional.empty());
            when(reactionRepository.save(any(Reaction.class))).thenAnswer(inv -> inv.getArgument(0));
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

            ReactionRequest request = req((short) 0);
            ReactionResponse res = reactionService.addReaction(100L, request, "alice");

            // no increment for 0
            assertThat(post.getLikeCount()).isEqualTo(5L);
            assertThat(post.getDislikeCount()).isEqualTo(2L);
            assertThat(res.getUserReaction()).isEqualTo((short) 0);
        }

        @Test
        @DisplayName("should save reaction with correct createdAt")
        void shouldSaveCreatedAt() {
            when(postRepository.findById(100L)).thenReturn(Optional.of(post));
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(reactionRepository.findByPostAndUser(post, alice)).thenReturn(Optional.empty());
            when(reactionRepository.save(any(Reaction.class))).thenAnswer(inv -> inv.getArgument(0));
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

            reactionService.addReaction(100L, req(Reaction.LIKE), "alice");

            ArgumentCaptor<Reaction> captor = ArgumentCaptor.forClass(Reaction.class);
            verify(reactionRepository).save(captor.capture());
            assertThat(captor.getValue().getCreatedAt()).isAfter(OffsetDateTime.now().minusSeconds(5));
        }
    }

    // -----------------------------------------------------------------------
    // removeReaction
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("removeReaction")
    class RemoveReaction {

        @Test
        @DisplayName("should throw PostNotFoundException when post not found")
        void shouldThrowWhenPostNotFound() {
            when(postRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(PostNotFoundException.class, () -> reactionService.removeReaction(99L, "alice"));
        }

        @Test
        @DisplayName("should throw UserNotFoundException when user not found")
        void shouldThrowWhenUserNotFound() {
            when(postRepository.findById(100L)).thenReturn(Optional.of(post));
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            assertThrows(UserNotFoundException.class, () -> reactionService.removeReaction(100L, "ghost"));
        }

        @Test
        @DisplayName("should do nothing when no existing reaction")
        void shouldDoNothingWhenNoExisting() {
            when(postRepository.findById(100L)).thenReturn(Optional.of(post));
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(reactionRepository.findByPostAndUser(post, alice)).thenReturn(Optional.empty());

            reactionService.removeReaction(100L, "alice");

            verify(reactionRepository, never()).delete(any());
            verify(postRepository, never()).save(any());
            assertThat(post.getLikeCount()).isEqualTo(5L);
            assertThat(post.getDislikeCount()).isEqualTo(2L);
        }

        @Test
        @DisplayName("should delete LIKE and decrement likeCount")
        void shouldDeleteLike() {
            Reaction existing = existingReaction(Reaction.LIKE);
            when(postRepository.findById(100L)).thenReturn(Optional.of(post));
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(reactionRepository.findByPostAndUser(post, alice)).thenReturn(Optional.of(existing));
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

            reactionService.removeReaction(100L, "alice");

            verify(reactionRepository).delete(existing);
            assertThat(post.getLikeCount()).isEqualTo(4L);
            assertThat(post.getDislikeCount()).isEqualTo(2L);
            verify(postRepository).save(post);
        }

        @Test
        @DisplayName("should delete DISLIKE and decrement dislikeCount")
        void shouldDeleteDislike() {
            Reaction existing = existingReaction(Reaction.DISLIKE);
            when(postRepository.findById(100L)).thenReturn(Optional.of(post));
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(reactionRepository.findByPostAndUser(post, alice)).thenReturn(Optional.of(existing));
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

            reactionService.removeReaction(100L, "alice");

            verify(reactionRepository).delete(existing);
            assertThat(post.getDislikeCount()).isEqualTo(1L);
            assertThat(post.getLikeCount()).isEqualTo(5L);
        }

        @Test
        @DisplayName("should decrement even when count is zero (current behavior allows negative)")
        void shouldAllowNegative() {
            post.setLikeCount(0L);
            Reaction existing = existingReaction(Reaction.LIKE);
            when(postRepository.findById(100L)).thenReturn(Optional.of(post));
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(reactionRepository.findByPostAndUser(post, alice)).thenReturn(Optional.of(existing));
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

            reactionService.removeReaction(100L, "alice");

            assertThat(post.getLikeCount()).isEqualTo(-1L);
        }
    }

    // -----------------------------------------------------------------------
    // getReactionCounts
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("getReactionCounts")
    class GetReactionCounts {

        @Test
        @DisplayName("should throw PostNotFoundException when post not found")
        void shouldThrowWhenPostNotFound() {
            when(postRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(PostNotFoundException.class, () -> reactionService.getReactionCounts(99L, "alice"));
        }

        @Test
        @DisplayName("should throw UserNotFoundException when user not found")
        void shouldThrowWhenUserNotFound() {
            when(postRepository.findById(100L)).thenReturn(Optional.of(post));
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            assertThrows(UserNotFoundException.class, () -> reactionService.getReactionCounts(100L, "ghost"));
        }

        @Test
        @DisplayName("should return counts and 0 when no user reaction")
        void shouldReturnZeroWhenNoReaction() {
            when(postRepository.findById(100L)).thenReturn(Optional.of(post));
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(reactionRepository.findByPostAndUser(post, alice)).thenReturn(Optional.empty());

            ReactionResponse res = reactionService.getReactionCounts(100L, "alice");

            assertThat(res.getLikeCount()).isEqualTo(5L);
            assertThat(res.getDislikeCount()).isEqualTo(2L);
            assertThat(res.getUserReaction()).isEqualTo((short) 0);
        }

        @Test
        @DisplayName("should return LIKE when user reacted with LIKE")
        void shouldReturnLike() {
            Reaction existing = existingReaction(Reaction.LIKE);
            when(postRepository.findById(100L)).thenReturn(Optional.of(post));
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(reactionRepository.findByPostAndUser(post, alice)).thenReturn(Optional.of(existing));

            ReactionResponse res = reactionService.getReactionCounts(100L, "alice");

            assertThat(res.getLikeCount()).isEqualTo(5L);
            assertThat(res.getDislikeCount()).isEqualTo(2L);
            assertThat(res.getUserReaction()).isEqualTo(Reaction.LIKE);
        }

        @Test
        @DisplayName("should return DISLIKE when user reacted with DISLIKE")
        void shouldReturnDislike() {
            Reaction existing = existingReaction(Reaction.DISLIKE);
            when(postRepository.findById(100L)).thenReturn(Optional.of(post));
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(reactionRepository.findByPostAndUser(post, alice)).thenReturn(Optional.of(existing));

            ReactionResponse res = reactionService.getReactionCounts(100L, "alice");

            assertThat(res.getUserReaction()).isEqualTo(Reaction.DISLIKE);
        }

        @Test
        @DisplayName("should return correct counts regardless of user reaction")
        void shouldReturnCountsWithReaction() {
            post.setLikeCount(10L);
            post.setDislikeCount(0L);
            Reaction existing = existingReaction(Reaction.DISLIKE);
            when(postRepository.findById(100L)).thenReturn(Optional.of(post));
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(reactionRepository.findByPostAndUser(post, alice)).thenReturn(Optional.of(existing));

            ReactionResponse res = reactionService.getReactionCounts(100L, "alice");

            assertThat(res.getLikeCount()).isEqualTo(10L);
            assertThat(res.getDislikeCount()).isEqualTo(0L);
            assertThat(res.getUserReaction()).isEqualTo(Reaction.DISLIKE);
        }
    }
}
