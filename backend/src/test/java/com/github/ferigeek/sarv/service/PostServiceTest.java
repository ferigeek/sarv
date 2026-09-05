package com.github.ferigeek.sarv.service;

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
import com.github.ferigeek.sarv.repository.MediaRepository;
import com.github.ferigeek.sarv.repository.PostRepository;
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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MediaRepository mediaRepository;

    @InjectMocks
    private PostService postService;

    private User owner;
    private User otherUser;
    private Media media;
    private Post basePost;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);
        owner.setUsername("owner");
        owner.setDisplayName("Owner");
        owner.setEmail("owner@example.com");
        owner.setPasswordHash("hash");
        owner.setCreatedAt(OffsetDateTime.now());

        otherUser = new User();
        otherUser.setId(2L);
        otherUser.setUsername("other");
        otherUser.setDisplayName("Other");
        otherUser.setEmail("other@example.com");
        otherUser.setPasswordHash("hash");
        otherUser.setCreatedAt(OffsetDateTime.now());

        media = new Media();
        media.setId(10L);
        media.setSize(100L);
        media.setMimeType("image/png");
        media.setSha256("abc");
        media.setCreatedAt(OffsetDateTime.now());

        basePost = new Post();
        basePost.setId(100L);
        basePost.setUser(owner);
        basePost.setPostCategory(PostCategory.NORMAL);
        basePost.setContent("hello world");
        basePost.setCreatedAt(OffsetDateTime.now());
        basePost.setViewCount(5L);
        basePost.setLikeCount(2L);
        basePost.setDislikeCount(1L);
    }

    private PostRequest req(PostCategory cat, String content, Long mediaId, Long parentId, Long repostOfId) {
        return new PostRequest(cat, content, mediaId, parentId, repostOfId);
    }

    // -----------------------------------------------------------------------
    // getPost
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("getPost")
    class GetPost {

        @Test
        @DisplayName("should increment viewCount, save and return mapped response")
        void shouldIncrementAndReturn() {
            basePost.setViewCount(0L);
            when(postRepository.findById(100L)).thenReturn(Optional.of(basePost));
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

            PostResponse res = postService.getPost(100L);

            assertThat(res.getId()).isEqualTo(100L);
            assertThat(res.getViewCount()).isEqualTo(1L);
            assertThat(res.getUserId()).isEqualTo(1L);
            assertThat(res.getContent()).isEqualTo("hello world");
            ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
            verify(postRepository).save(captor.capture());
            assertThat(captor.getValue().getViewCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should increment from existing viewCount")
        void shouldIncrementFromExisting() {
            basePost.setViewCount(5L);
            when(postRepository.findById(1L)).thenReturn(Optional.of(basePost));
            when(postRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PostResponse res = postService.getPost(1L);

            assertThat(res.getViewCount()).isEqualTo(6L);
        }

        @Test
        @DisplayName("should throw PostNotFoundException when not found")
        void shouldThrowWhenNotFound() {
            when(postRepository.findById(99L)).thenReturn(Optional.empty());

            PostNotFoundException ex = assertThrows(PostNotFoundException.class, () -> postService.getPost(99L));

            assertThat(ex.getMessage()).contains("99");
            verify(postRepository, never()).save(any());
        }

        @Test
        @DisplayName("should map media, parent, repost correctly")
        void shouldMapRelations() {
            Post parent = new Post(); parent.setId(200L);
            parent.setUser(owner); parent.setPostCategory(PostCategory.NORMAL); parent.setCreatedAt(OffsetDateTime.now());
            Post repost = new Post(); repost.setId(300L);
            repost.setUser(owner); repost.setPostCategory(PostCategory.NORMAL); repost.setCreatedAt(OffsetDateTime.now());
            basePost.setMedia(media);
            basePost.setParent(parent);
            basePost.setRepostOf(repost);
            when(postRepository.findById(100L)).thenReturn(Optional.of(basePost));
            when(postRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PostResponse res = postService.getPost(100L);

            assertThat(res.getMediaId()).isEqualTo(10L);
            assertThat(res.getParentId()).isEqualTo(200L);
            assertThat(res.getRepostOfId()).isEqualTo(300L);
        }

        @Test
        @DisplayName("should map null media/parent/repost to null ids")
        void shouldMapNullRelations() {
            basePost.setMedia(null);
            basePost.setParent(null);
            basePost.setRepostOf(null);
            when(postRepository.findById(100L)).thenReturn(Optional.of(basePost));
            when(postRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PostResponse res = postService.getPost(100L);

            assertThat(res.getMediaId()).isNull();
            assertThat(res.getParentId()).isNull();
            assertThat(res.getRepostOfId()).isNull();
        }
    }

    // -----------------------------------------------------------------------
    // createPost
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("createPost")
    class CreatePost {

        @Test
        @DisplayName("NORMAL happy path with content only")
        void normalHappyWithContent() {
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> {
                Post p = inv.getArgument(0);
                p.setId(1L);
                p.setViewCount(0L); p.setLikeCount(0L); p.setDislikeCount(0L);
                return p;
            });

            PostRequest r = req(PostCategory.NORMAL, "hello", null, null, null);
            PostResponse res = postService.createPost(r, "owner");

            assertThat(res.getPostCategory()).isEqualTo(PostCategory.NORMAL);
            assertThat(res.getContent()).isEqualTo("hello");
            assertThat(res.getUserId()).isEqualTo(1L);
            ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
            verify(postRepository).save(captor.capture());
            Post saved = captor.getValue();
            assertThat(saved.getPostCategory()).isEqualTo(PostCategory.NORMAL);
            assertThat(saved.getContent()).isEqualTo("hello");
            assertThat(saved.getUser()).isEqualTo(owner);
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getMedia()).isNull();
            assertThat(saved.getParent()).isNull();
            assertThat(saved.getRepostOf()).isNull();
        }

        @Test
        @DisplayName("NORMAL happy path with media only")
        void normalHappyWithMedia() {
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
            when(mediaRepository.findById(10L)).thenReturn(Optional.of(media));
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> {
                Post p = inv.getArgument(0); p.setId(1L); return p;
            });

            PostRequest r = req(PostCategory.NORMAL, null, 10L, null, null);
            PostResponse res = postService.createPost(r, "owner");

            assertThat(res.getMediaId()).isEqualTo(10L);
            assertThat(res.getContent()).isNull();
        }

        @Test
        @DisplayName("NORMAL happy path with both content and media")
        void normalHappyWithBoth() {
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
            when(mediaRepository.findById(10L)).thenReturn(Optional.of(media));
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> { Post p=inv.getArgument(0); p.setId(1L); return p; });

            PostRequest r = req(PostCategory.NORMAL, "text", 10L, null, null);
            PostResponse res = postService.createPost(r, "owner");

            assertThat(res.getContent()).isEqualTo("text");
            assertThat(res.getMediaId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("NORMAL should fail when both content blank and media null")
        void normalFailsWhenEmpty() {
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));

            PostRequest r1 = req(PostCategory.NORMAL, null, null, null, null);
            assertThrows(PostNotValidException.class, () -> postService.createPost(r1, "owner"));

            PostRequest r2 = req(PostCategory.NORMAL, "   ", null, null, null);
            assertThrows(PostNotValidException.class, () -> postService.createPost(r2, "owner"));

            PostRequest r3 = req(PostCategory.NORMAL, "", null, null, null);
            assertThrows(PostNotValidException.class, () -> postService.createPost(r3, "owner"));
        }

        @Test
        @DisplayName("NORMAL should fail when parentId present")
        void normalFailsWithParent() {
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));

            PostRequest r = req(PostCategory.NORMAL, "hello", null, 5L, null);
            PostNotValidException ex = assertThrows(PostNotValidException.class, () -> postService.createPost(r, "owner"));
            assertThat(ex.getMessage()).contains("NORMAL cannot have a parent");
        }

        @Test
        @DisplayName("NORMAL should fail when repostOfId present")
        void normalFailsWithRepost() {
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));

            PostRequest r = req(PostCategory.NORMAL, "hello", null, null, 5L);
            PostNotValidException ex = assertThrows(PostNotValidException.class, () -> postService.createPost(r, "owner"));
            assertThat(ex.getMessage()).contains("NORMAL cannot be a repost");
        }

        // COMMENT
        @Test
        @DisplayName("COMMENT happy path with parent")
        void commentHappy() {
            Post parent = new Post(); parent.setId(200L); parent.setUser(owner); parent.setPostCategory(PostCategory.NORMAL);
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
            when(postRepository.findById(200L)).thenReturn(Optional.of(parent));
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> { Post p=inv.getArgument(0); p.setId(2L); return p; });

            PostRequest r = req(PostCategory.COMMENT, "reply", null, 200L, null);
            PostResponse res = postService.createPost(r, "owner");

            assertThat(res.getParentId()).isEqualTo(200L);
            assertThat(res.getPostCategory()).isEqualTo(PostCategory.COMMENT);
        }

        @Test
        @DisplayName("COMMENT should fail when parent missing")
        void commentFailsWithoutParent() {
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));

            PostRequest r = req(PostCategory.COMMENT, "hello", null, null, null);
            PostNotValidException ex = assertThrows(PostNotValidException.class, () -> postService.createPost(r, "owner"));
            assertThat(ex.getMessage()).contains("COMMENT should have a parent");
        }

        @Test
        @DisplayName("COMMENT should fail when both content and media missing")
        void commentFailsWhenEmpty() {
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));

            PostRequest r = req(PostCategory.COMMENT, "   ", null, 200L, null);
            assertThrows(PostNotValidException.class, () -> postService.createPost(r, "owner"));
        }

        @Test
        @DisplayName("COMMENT should allow repostOfId (quote as comment)")
        void commentAllowsRepost() {
            Post parent = new Post(); parent.setId(200L); parent.setUser(owner); parent.setPostCategory(PostCategory.NORMAL);
            Post repost = new Post(); repost.setId(300L); repost.setUser(owner); repost.setPostCategory(PostCategory.NORMAL);
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
            when(postRepository.findById(200L)).thenReturn(Optional.of(parent));
            when(postRepository.findById(300L)).thenReturn(Optional.of(repost));
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> { Post p=inv.getArgument(0); p.setId(2L); return p; });

            PostRequest r = req(PostCategory.COMMENT, "hello", null, 200L, 300L);
            PostResponse res = postService.createPost(r, "owner");

            assertThat(res.getParentId()).isEqualTo(200L);
            assertThat(res.getRepostOfId()).isEqualTo(300L);
        }

        // QUOTE
        @Test
        @DisplayName("QUOTE happy path with repost")
        void quoteHappy() {
            Post repost = new Post(); repost.setId(300L); repost.setUser(owner); repost.setPostCategory(PostCategory.NORMAL);
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
            when(postRepository.findById(300L)).thenReturn(Optional.of(repost));
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> { Post p=inv.getArgument(0); p.setId(3L); return p; });

            PostRequest r = req(PostCategory.QUOTE, "quote text", null, null, 300L);
            PostResponse res = postService.createPost(r, "owner");

            assertThat(res.getRepostOfId()).isEqualTo(300L);
            assertThat(res.getContent()).isEqualTo("quote text");
        }

        @Test
        @DisplayName("QUOTE should fail when repostOf null")
        void quoteFailsWithoutRepost() {
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));

            PostRequest r = req(PostCategory.QUOTE, "hello", null, null, null);
            assertThrows(PostNotValidException.class, () -> postService.createPost(r, "owner"));
        }

        @Test
        @DisplayName("QUOTE should fail when parent present")
        void quoteFailsWithParent() {
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));

            PostRequest r = req(PostCategory.QUOTE, "hello", null, 5L, 300L);
            assertThrows(PostNotValidException.class, () -> postService.createPost(r, "owner"));
        }

        @Test
        @DisplayName("QUOTE should fail when both content blank and media null")
        void quoteFailsWhenEmpty() {
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));

            PostRequest r = req(PostCategory.QUOTE, "   ", null, null, 300L);
            assertThrows(PostNotValidException.class, () -> postService.createPost(r, "owner"));
        }

        // REPOST
        @Test
        @DisplayName("REPOST happy path")
        void repostHappy() {
            Post repost = new Post(); repost.setId(300L); repost.setUser(owner); repost.setPostCategory(PostCategory.NORMAL);
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
            when(postRepository.findById(300L)).thenReturn(Optional.of(repost));
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> { Post p=inv.getArgument(0); p.setId(4L); return p; });

            PostRequest r = req(PostCategory.REPOST, null, null, null, 300L);
            PostResponse res = postService.createPost(r, "owner");

            assertThat(res.getRepostOfId()).isEqualTo(300L);
            assertThat(res.getContent()).isNull();
        }

        @Test
        @DisplayName("REPOST should allow blank content")
        void repostAllowsBlankContent() {
            Post repost = new Post(); repost.setId(300L); repost.setUser(owner); repost.setPostCategory(PostCategory.NORMAL);
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
            when(postRepository.findById(300L)).thenReturn(Optional.of(repost));
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> { Post p=inv.getArgument(0); p.setId(4L); return p; });

            PostRequest r = req(PostCategory.REPOST, "   ", null, null, 300L);
            PostResponse res = postService.createPost(r, "owner");

            assertThat(res.getRepostOfId()).isEqualTo(300L);
        }

        @Test
        @DisplayName("REPOST should fail when content present")
        void repostFailsWithContent() {
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));

            PostRequest r = req(PostCategory.REPOST, "hello", null, null, 300L);
            assertThrows(PostNotValidException.class, () -> postService.createPost(r, "owner"));
        }

        @Test
        @DisplayName("REPOST should fail when media present")
        void repostFailsWithMedia() {
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));

            PostRequest r = req(PostCategory.REPOST, null, 10L, null, 300L);
            assertThrows(PostNotValidException.class, () -> postService.createPost(r, "owner"));
        }

        @Test
        @DisplayName("REPOST should fail when repostOf null")
        void repostFailsWithoutRepost() {
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));

            PostRequest r = req(PostCategory.REPOST, null, null, null, null);
            assertThrows(PostNotValidException.class, () -> postService.createPost(r, "owner"));
        }

        @Test
        @DisplayName("REPOST should fail when parent present")
        void repostFailsWithParent() {
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));

            PostRequest r = req(PostCategory.REPOST, null, null, 5L, 300L);
            assertThrows(PostNotValidException.class, () -> postService.createPost(r, "owner"));
        }

        // Common exception paths
        @Test
        @DisplayName("should throw UserNotFoundException when user not found")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            PostRequest r = req(PostCategory.NORMAL, "hello", null, null, null);
            assertThrows(UserNotFoundException.class, () -> postService.createPost(r, "ghost"));
        }

        @Test
        @DisplayName("should throw MediaNotFoundException when media not found")
        void shouldThrowWhenMediaNotFound() {
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
            when(mediaRepository.findById(999L)).thenReturn(Optional.empty());

            PostRequest r = req(PostCategory.NORMAL, "hello", 999L, null, null);
            assertThrows(MediaNotFoundException.class, () -> postService.createPost(r, "owner"));
        }

        @Test
        @DisplayName("should throw PostNotFoundException when parent not found")
        void shouldThrowWhenParentNotFound() {
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
            when(postRepository.findById(999L)).thenReturn(Optional.empty());

            PostRequest r = req(PostCategory.COMMENT, "hello", null, 999L, null);
            assertThrows(PostNotFoundException.class, () -> postService.createPost(r, "owner"));
        }

        @Test
        @DisplayName("should throw PostNotFoundException when repost not found")
        void shouldThrowWhenRepostNotFound() {
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
            when(postRepository.findById(999L)).thenReturn(Optional.empty());

            PostRequest r = req(PostCategory.REPOST, null, null, null, 999L);
            assertThrows(PostNotFoundException.class, () -> postService.createPost(r, "owner"));
        }

        @Test
        @DisplayName("should save with correct content and category for COMMENT with media")
        void shouldSaveCommentWithMedia() {
            Post parent = new Post(); parent.setId(200L); parent.setUser(owner); parent.setPostCategory(PostCategory.NORMAL);
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
            when(mediaRepository.findById(10L)).thenReturn(Optional.of(media));
            when(postRepository.findById(200L)).thenReturn(Optional.of(parent));
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> { Post p=inv.getArgument(0); p.setId(5L); return p; });

            PostRequest r = req(PostCategory.COMMENT, null, 10L, 200L, null);
            PostResponse res = postService.createPost(r, "owner");

            assertThat(res.getMediaId()).isEqualTo(10L);
            assertThat(res.getParentId()).isEqualTo(200L);
            ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
            verify(postRepository).save(captor.capture());
            assertThat(captor.getValue().getMedia()).isEqualTo(media);
        }
    }

    // -----------------------------------------------------------------------
    // deletePost
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("deletePost")
    class DeletePost {

        @Test
        @DisplayName("happy path: soft delete by owner (id equality)")
        void happyPath() {
            Post post = new Post();
            post.setId(100L);
            User postUser = new User(); postUser.setId(1L); postUser.setUsername("owner");
            post.setUser(postUser);
            post.setPostCategory(PostCategory.NORMAL);
            post.setCreatedAt(OffsetDateTime.now());

            // owner is different instance but same id -> should succeed after fix (id equality)
            User requester = new User(); requester.setId(1L); requester.setUsername("owner");

            when(postRepository.findById(100L)).thenReturn(Optional.of(post));
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(requester));
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

            postService.deletePost(100L, "owner");

            ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
            verify(postRepository).save(captor.capture());
            Post saved = captor.getValue();
            assertThat(saved.getDeletedAt()).isNotNull();
            assertThat(saved.getDeletedAt()).isAfter(OffsetDateTime.now().minusSeconds(5));
            assertThat(saved.getUser()).isNull();
        }

        @Test
        @DisplayName("should throw RuntimeException when post not found")
        void shouldThrowWhenPostNotFound() {
            when(postRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class, () -> postService.deletePost(99L, "owner"));
        }

        @Test
        @DisplayName("should throw UserNotFoundException when user not found")
        void shouldThrowWhenUserNotFound() {
            when(postRepository.findById(100L)).thenReturn(Optional.of(basePost));
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            assertThrows(UserNotFoundException.class, () -> postService.deletePost(100L, "ghost"));
        }

        @Test
        @DisplayName("should throw RuntimeException when not owner (different id)")
        void shouldThrowWhenNotOwner() {
            Post post = new Post();
            post.setId(100L);
            post.setUser(owner); // id 1
            when(postRepository.findById(100L)).thenReturn(Optional.of(post));
            when(userRepository.findByUsername("other")).thenReturn(Optional.of(otherUser)); // id 2

            RuntimeException ex = assertThrows(RuntimeException.class, () -> postService.deletePost(100L, "other"));
            assertThat(ex.getMessage()).contains("You are not the owner");
            verify(postRepository, never()).save(any());
        }

        @Test
        @DisplayName("should not call save when not owner")
        void shouldNotSaveWhenNotOwner() {
            when(postRepository.findById(100L)).thenReturn(Optional.of(basePost));
            when(userRepository.findByUsername("other")).thenReturn(Optional.of(otherUser));

            assertThrows(RuntimeException.class, () -> postService.deletePost(100L, "other"));

            verify(postRepository, never()).save(any());
        }
    }

    // -----------------------------------------------------------------------
    // updatePost
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("updatePost")
    class UpdatePost {

        @Test
        @DisplayName("happy path: update content only")
        void happyPathContentOnly() {
            Post post = new Post();
            post.setId(100L);
            post.setUser(owner);
            post.setPostCategory(PostCategory.NORMAL);
            post.setContent("old");
            post.setCreatedAt(OffsetDateTime.now());
            post.setViewCount(1L);
            when(postRepository.findById(100L)).thenReturn(Optional.of(post));
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

            PostUpdateRequest req = new PostUpdateRequest("new content", null);
            PostResponse res = postService.updatePost(100L, req, "owner");

            assertThat(res.getContent()).isEqualTo("new content");
            assertThat(res.getMediaId()).isNull();
            ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
            verify(postRepository).save(captor.capture());
            assertThat(captor.getValue().getContent()).isEqualTo("new content");
            assertThat(captor.getValue().getMedia()).isNull();
        }

        @Test
        @DisplayName("happy path: update media only (clears content when blank)")
        void happyPathMediaOnly() {
            Post post = new Post();
            post.setId(100L);
            post.setUser(owner);
            post.setContent("old");
            post.setPostCategory(PostCategory.NORMAL);
            post.setCreatedAt(OffsetDateTime.now());
            when(postRepository.findById(100L)).thenReturn(Optional.of(post));
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
            when(mediaRepository.findById(10L)).thenReturn(Optional.of(media));
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

            PostUpdateRequest req = new PostUpdateRequest(null, 10L);
            PostResponse res = postService.updatePost(100L, req, "owner");

            assertThat(res.getMediaId()).isEqualTo(10L);
            assertThat(res.getContent()).isNull();
        }

        @Test
        @DisplayName("happy path: update both content and media")
        void happyPathBoth() {
            Post post = new Post();
            post.setId(100L);
            post.setUser(owner);
            post.setPostCategory(PostCategory.NORMAL);
            post.setCreatedAt(OffsetDateTime.now());
            when(postRepository.findById(100L)).thenReturn(Optional.of(post));
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
            when(mediaRepository.findById(10L)).thenReturn(Optional.of(media));
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

            PostUpdateRequest req = new PostUpdateRequest("new", 10L);
            PostResponse res = postService.updatePost(100L, req, "owner");

            assertThat(res.getContent()).isEqualTo("new");
            assertThat(res.getMediaId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("should clear content when blank")
        void shouldClearWhenBlank() {
            Post post = new Post();
            post.setId(100L);
            post.setUser(owner);
            post.setContent("old");
            post.setMedia(media);
            when(postRepository.findById(100L)).thenReturn(Optional.of(post));
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
            when(mediaRepository.findById(10L)).thenReturn(Optional.of(media));
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

            PostUpdateRequest req = new PostUpdateRequest("   ", 10L);
            PostResponse res = postService.updatePost(100L, req, "owner");

            assertThat(res.getContent()).isNull();
            ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
            verify(postRepository).save(captor.capture());
            assertThat(captor.getValue().getContent()).isNull();
        }

        @Test
        @DisplayName("should clear media when null")
        void shouldClearMediaWhenNull() {
            Post post = new Post();
            post.setId(100L);
            post.setUser(owner);
            post.setMedia(media);
            post.setContent("old");
            when(postRepository.findById(100L)).thenReturn(Optional.of(post));
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

            PostUpdateRequest req = new PostUpdateRequest("new content", null);
            postService.updatePost(100L, req, "owner");

            ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
            verify(postRepository).save(captor.capture());
            assertThat(captor.getValue().getMedia()).isNull();
        }

        @Test
        @DisplayName("should throw PostNotFoundException when post not found")
        void shouldThrowWhenPostNotFound() {
            when(postRepository.findById(99L)).thenReturn(Optional.empty());

            PostUpdateRequest req = new PostUpdateRequest("hello", null);
            assertThrows(PostNotFoundException.class, () -> postService.updatePost(99L, req, "owner"));
        }

        @Test
        @DisplayName("should throw UserNotFoundException when user not found")
        void shouldThrowWhenUserNotFound() {
            when(postRepository.findById(100L)).thenReturn(Optional.of(basePost));
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            PostUpdateRequest req = new PostUpdateRequest("hello", null);
            assertThrows(UserNotFoundException.class, () -> postService.updatePost(100L, req, "ghost"));
        }

        @Test
        @DisplayName("should throw UnAuthorizedUpdateException when not owner (id check)")
        void shouldThrowWhenNotOwner() {
            Post post = new Post();
            post.setId(100L);
            post.setUser(owner); // id 1
            when(postRepository.findById(100L)).thenReturn(Optional.of(post));
            // otherUser id 2, different instance with same id would still fail if different id
            when(userRepository.findByUsername("other")).thenReturn(Optional.of(otherUser));

            PostUpdateRequest req = new PostUpdateRequest("hello", null);
            UnAuthorizedUpdateException ex = assertThrows(UnAuthorizedUpdateException.class,
                    () -> postService.updatePost(100L, req, "other"));
            assertThat(ex.getMessage()).contains("1").contains("100"); // actually message contains user id and post id
            // message is "User with ID: <2> is not the owner of post with ID: <100>"
            assertThat(ex.getMessage()).contains("<2>");
            assertThat(ex.getMessage()).contains("<100>");
        }

        @Test
        @DisplayName("should succeed when owner same id but different instance (id equality)")
        void shouldSucceedWithSameIdDifferentInstance() {
            Post post = new Post();
            post.setId(100L);
            User postUser = new User(); postUser.setId(1L);
            post.setUser(postUser);
            User requester = new User(); requester.setId(1L); requester.setUsername("owner");
            when(postRepository.findById(100L)).thenReturn(Optional.of(post));
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(requester));
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

            PostUpdateRequest req = new PostUpdateRequest("ok", null);
            PostResponse res = postService.updatePost(100L, req, "owner");

            assertThat(res.getContent()).isEqualTo("ok");
        }

        @Test
        @DisplayName("should throw PostNotValidException when both content blank and media null")
        void shouldThrowWhenEmpty() {
            Post post = new Post();
            post.setId(100L);
            post.setUser(owner);
            when(postRepository.findById(100L)).thenReturn(Optional.of(post));
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));

            PostUpdateRequest r1 = new PostUpdateRequest(null, null);
            assertThrows(PostNotValidException.class, () -> postService.updatePost(100L, r1, "owner"));

            PostUpdateRequest r2 = new PostUpdateRequest("   ", null);
            assertThrows(PostNotValidException.class, () -> postService.updatePost(100L, r2, "owner"));

            PostUpdateRequest r3 = new PostUpdateRequest("", null);
            assertThrows(PostNotValidException.class, () -> postService.updatePost(100L, r3, "owner"));
        }

        @Test
        @DisplayName("should throw MediaNotFoundException when media not found")
        void shouldThrowWhenMediaNotFound() {
            Post post = new Post();
            post.setId(100L);
            post.setUser(owner);
            when(postRepository.findById(100L)).thenReturn(Optional.of(post));
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
            when(mediaRepository.findById(999L)).thenReturn(Optional.empty());

            PostUpdateRequest req = new PostUpdateRequest("hello", 999L);
            assertThrows(MediaNotFoundException.class, () -> postService.updatePost(100L, req, "owner"));
        }

        @Test
        @DisplayName("should handle updating with blank content but valid media")
        void shouldAllowBlankContentWithMedia() {
            Post post = new Post();
            post.setId(100L);
            post.setUser(owner);
            when(postRepository.findById(100L)).thenReturn(Optional.of(post));
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
            when(mediaRepository.findById(10L)).thenReturn(Optional.of(media));
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

            PostUpdateRequest req = new PostUpdateRequest("   ", 10L);
            PostResponse res = postService.updatePost(100L, req, "owner");

            assertThat(res.getContent()).isNull();
            assertThat(res.getMediaId()).isEqualTo(10L);
        }
    }

    // -----------------------------------------------------------------------
    // getUserPosts
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("getUserPosts")
    class GetUserPosts {

        private Post post(Long id, User user, String content) {
            Post p = new Post();
            p.setId(id);
            p.setUser(user);
            p.setPostCategory(PostCategory.NORMAL);
            p.setContent(content);
            p.setCreatedAt(OffsetDateTime.now());
            p.setViewCount(3L);
            p.setLikeCount(1L);
            p.setDislikeCount(0L);
            return p;
        }

        @Test
        @DisplayName("should delegate to repository with same userId and pageable")
        void shouldDelegateWithSameArgs() {
            Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
            when(postRepository.findPostsByUserId(1L, pageable))
                    .thenReturn(Page.empty(pageable));

            postService.getUserPosts(1L, pageable);

            verify(postRepository).findPostsByUserId(1L, pageable);
        }

        @Test
        @DisplayName("should map posts to PostResponse preserving order")
        void shouldMapPosts() {
            Post p1 = post(100L, owner, "first");
            Post p2 = post(101L, owner, "second");
            Pageable pageable = PageRequest.of(0, 10);
            when(postRepository.findPostsByUserId(1L, pageable))
                    .thenReturn(new PageImpl<>(List.of(p1, p2), pageable, 2));

            Page<PostResponse> res = postService.getUserPosts(1L, pageable);

            assertThat(res.getContent()).hasSize(2);
            assertThat(res.getContent().get(0).getId()).isEqualTo(100L);
            assertThat(res.getContent().get(0).getUserId()).isEqualTo(1L);
            assertThat(res.getContent().get(0).getContent()).isEqualTo("first");
            assertThat(res.getContent().get(1).getId()).isEqualTo(101L);
        }

        @Test
        @DisplayName("should map media/parent/repost ids")
        void shouldMapRelations() {
            Post parent = post(200L, owner, "parent");
            Post repost = post(300L, owner, "repost");
            Post p = post(100L, owner, "hello");
            p.setMedia(media);
            p.setParent(parent);
            p.setRepostOf(repost);
            Pageable pageable = PageRequest.of(0, 10);
            when(postRepository.findPostsByUserId(1L, pageable))
                    .thenReturn(new PageImpl<>(List.of(p), pageable, 1));

            Page<PostResponse> res = postService.getUserPosts(1L, pageable);

            assertThat(res.getContent().get(0).getMediaId()).isEqualTo(10L);
            assertThat(res.getContent().get(0).getParentId()).isEqualTo(200L);
            assertThat(res.getContent().get(0).getRepostOfId()).isEqualTo(300L);
        }

        @Test
        @DisplayName("should preserve pagination metadata")
        void shouldPreservePagination() {
            Post p = post(100L, owner, "hello");
            Pageable pageable = PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "createdAt"));
            when(postRepository.findPostsByUserId(1L, pageable))
                    .thenReturn(new PageImpl<>(List.of(p), pageable, 5));

            Page<PostResponse> res = postService.getUserPosts(1L, pageable);

            assertThat(res.getTotalElements()).isEqualTo(5);
            assertThat(res.getTotalPages()).isEqualTo(3);
            assertThat(res.getNumber()).isEqualTo(1);
            assertThat(res.getSize()).isEqualTo(2);
        }

        @Test
        @DisplayName("should return empty page when user has no posts")
        void shouldReturnEmpty() {
            Pageable pageable = PageRequest.of(0, 10);
            when(postRepository.findPostsByUserId(1L, pageable))
                    .thenReturn(Page.empty(pageable));

            Page<PostResponse> res = postService.getUserPosts(1L, pageable);

            assertThat(res.getTotalElements()).isZero();
            assertThat(res.getContent()).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // getPostComments
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("getPostComments")
    class GetPostComments {

        private Post comment(Long id, Post parent, User user, String content) {
            Post p = new Post();
            p.setId(id);
            p.setUser(user);
            p.setPostCategory(PostCategory.COMMENT);
            p.setContent(content);
            p.setCreatedAt(OffsetDateTime.now());
            p.setParent(parent);
            p.setViewCount(0L);
            p.setLikeCount(0L);
            p.setDislikeCount(0L);
            return p;
        }

        @Test
        @DisplayName("should delegate to repository with same postId and pageable")
        void shouldDelegateWithSameArgs() {
            Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
            when(postRepository.existsById(100L)).thenReturn(true);
            when(postRepository.findCommentsByParentId(100L, pageable))
                    .thenReturn(Page.empty(pageable));

            postService.getPostComments(100L, pageable);

            verify(postRepository).existsById(100L);
            verify(postRepository).findCommentsByParentId(100L, pageable);
        }

        @Test
        @DisplayName("should map comments to PostResponse preserving order")
        void shouldMapComments() {
            Post parent = new Post();
            parent.setId(100L);
            parent.setUser(owner);
            parent.setPostCategory(PostCategory.NORMAL);
            parent.setContent("parent");
            parent.setCreatedAt(OffsetDateTime.now());
            Post c1 = comment(200L, parent, otherUser, "first");
            Post c2 = comment(201L, parent, owner, "second");
            Pageable pageable = PageRequest.of(0, 10);
            when(postRepository.existsById(100L)).thenReturn(true);
            when(postRepository.findCommentsByParentId(100L, pageable))
                    .thenReturn(new PageImpl<>(List.of(c1, c2), pageable, 2));

            Page<PostResponse> res = postService.getPostComments(100L, pageable);

            assertThat(res.getContent()).hasSize(2);
            assertThat(res.getContent().get(0).getId()).isEqualTo(200L);
            assertThat(res.getContent().get(0).getParentId()).isEqualTo(100L);
            assertThat(res.getContent().get(0).getContent()).isEqualTo("first");
            assertThat(res.getContent().get(1).getId()).isEqualTo(201L);
        }

        @Test
        @DisplayName("should throw PostNotFoundException when parent post does not exist")
        void shouldThrowWhenParentNotFound() {
            when(postRepository.existsById(99L)).thenReturn(false);

            PostNotFoundException ex = assertThrows(PostNotFoundException.class,
                    () -> postService.getPostComments(99L, PageRequest.of(0, 10)));

            assertThat(ex.getMessage()).contains("99");
            verify(postRepository, never()).findCommentsByParentId(any(), any());
        }

        @Test
        @DisplayName("should preserve pagination metadata")
        void shouldPreservePagination() {
            Post parent = new Post();
            parent.setId(100L);
            parent.setUser(owner);
            Post c = comment(200L, parent, owner, "hello");
            Pageable pageable = PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "createdAt"));
            when(postRepository.existsById(100L)).thenReturn(true);
            when(postRepository.findCommentsByParentId(100L, pageable))
                    .thenReturn(new PageImpl<>(List.of(c), pageable, 5));

            Page<PostResponse> res = postService.getPostComments(100L, pageable);

            assertThat(res.getTotalElements()).isEqualTo(5);
            assertThat(res.getTotalPages()).isEqualTo(3);
            assertThat(res.getNumber()).isEqualTo(1);
            assertThat(res.getSize()).isEqualTo(2);
        }

        @Test
        @DisplayName("should return empty page when post has no comments")
        void shouldReturnEmpty() {
            Pageable pageable = PageRequest.of(0, 10);
            when(postRepository.existsById(100L)).thenReturn(true);
            when(postRepository.findCommentsByParentId(100L, pageable))
                    .thenReturn(Page.empty(pageable));

            Page<PostResponse> res = postService.getPostComments(100L, pageable);

            assertThat(res.getTotalElements()).isZero();
            assertThat(res.getContent()).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // getReactedPosts
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("getReactedPosts")
    class GetReactedPosts {

        private Post post(Long id, User user, String content) {
            Post p = new Post();
            p.setId(id);
            p.setUser(user);
            p.setPostCategory(PostCategory.NORMAL);
            p.setContent(content);
            p.setCreatedAt(OffsetDateTime.now());
            p.setViewCount(3L);
            p.setLikeCount(1L);
            p.setDislikeCount(0L);
            return p;
        }

        @Test
        @DisplayName("should pass null reaction type for ALL filter")
        void shouldPassNullForAll() {
            Pageable pageable = PageRequest.of(0, 10);
            when(postRepository.findReactedPosts(1L, null, pageable))
                    .thenReturn(Page.empty(pageable));

            postService.getReactedPosts(1L, ReactionFilter.ALL, pageable);

            verify(postRepository).findReactedPosts(1L, null, pageable);
        }

        @Test
        @DisplayName("should pass like reaction type for LIKE filter")
        void shouldPassLikeType() {
            Pageable pageable = PageRequest.of(0, 10);
            when(postRepository.findReactedPosts(eq(1L), eq((short) 1), eq(pageable)))
                    .thenReturn(Page.empty(pageable));

            postService.getReactedPosts(1L, ReactionFilter.LIKE, pageable);

            verify(postRepository).findReactedPosts(1L, (short) 1, pageable);
        }

        @Test
        @DisplayName("should pass dislike reaction type for DISLIKE filter")
        void shouldPassDislikeType() {
            Pageable pageable = PageRequest.of(0, 10);
            when(postRepository.findReactedPosts(eq(1L), eq((short) -1), eq(pageable)))
                    .thenReturn(Page.empty(pageable));

            postService.getReactedPosts(1L, ReactionFilter.DISLIKE, pageable);

            verify(postRepository).findReactedPosts(1L, (short) -1, pageable);
        }

        @Test
        @DisplayName("should map posts to PostResponse preserving order")
        void shouldMapPosts() {
            Post p1 = post(100L, otherUser, "first");
            Post p2 = post(101L, owner, "second");
            Pageable pageable = PageRequest.of(0, 10);
            when(postRepository.findReactedPosts(1L, null, pageable))
                    .thenReturn(new PageImpl<>(List.of(p1, p2), pageable, 2));

            Page<PostResponse> res = postService.getReactedPosts(1L, ReactionFilter.ALL, pageable);

            assertThat(res.getContent()).hasSize(2);
            assertThat(res.getContent().get(0).getId()).isEqualTo(100L);
            assertThat(res.getContent().get(0).getContent()).isEqualTo("first");
            assertThat(res.getContent().get(1).getId()).isEqualTo(101L);
        }

        @Test
        @DisplayName("should preserve pagination metadata")
        void shouldPreservePagination() {
            Post p = post(100L, owner, "hello");
            Pageable pageable = PageRequest.of(1, 2);
            when(postRepository.findReactedPosts(1L, null, pageable))
                    .thenReturn(new PageImpl<>(List.of(p), pageable, 5));

            Page<PostResponse> res = postService.getReactedPosts(1L, ReactionFilter.ALL, pageable);

            assertThat(res.getTotalElements()).isEqualTo(5);
            assertThat(res.getTotalPages()).isEqualTo(3);
            assertThat(res.getNumber()).isEqualTo(1);
            assertThat(res.getSize()).isEqualTo(2);
        }

        @Test
        @DisplayName("should return empty page when user has no reactions")
        void shouldReturnEmpty() {
            Pageable pageable = PageRequest.of(0, 10);
            when(postRepository.findReactedPosts(1L, null, pageable))
                    .thenReturn(Page.empty(pageable));

            Page<PostResponse> res = postService.getReactedPosts(1L, ReactionFilter.ALL, pageable);

            assertThat(res.getTotalElements()).isZero();
            assertThat(res.getContent()).isEmpty();
        }
    }
}
