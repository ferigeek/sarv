package com.github.ferigeek.sarv.service;

import com.github.ferigeek.sarv.dto.request.PostRequest;
import com.github.ferigeek.sarv.dto.request.PostUpdateRequest;
import com.github.ferigeek.sarv.dto.response.PostResponse;
import com.github.ferigeek.sarv.entity.Media;
import com.github.ferigeek.sarv.entity.Post;
import com.github.ferigeek.sarv.entity.User;
import com.github.ferigeek.sarv.entity.type.PostCategory;
import com.github.ferigeek.sarv.exception.*;
import com.github.ferigeek.sarv.repository.MediaRepository;
import com.github.ferigeek.sarv.repository.PostRepository;
import com.github.ferigeek.sarv.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final MediaRepository mediaRepository;

    @Autowired
    public PostService(PostRepository postRepository, UserRepository userRepository, MediaRepository mediaRepository) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.mediaRepository = mediaRepository;
    }

    public PostResponse getPost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));
        post.setViewCount(post.getViewCount() + 1);
        postRepository.save(post);
        return new PostResponse(post);
    }

    @Transactional
    public PostResponse createPost(PostRequest postRequest, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(
                        "User not found with username: <%s>".formatted(username))
                );

        Post post = new Post();

        post.setUser(user);
        post.setCreatedAt(OffsetDateTime.now());
        PostCategory postCategory = postRequest.getPostCategory();
        post.setPostCategory(postCategory);
        post.setContent(postRequest.getContent());

        switch (postCategory) {
            case NORMAL:
                if ((postRequest.getContent() == null || postRequest.getContent().isBlank())
                        && postRequest.getMediaId() == null) {
                    throw new PostNotValidException(
                            "Post with category NORMAL should have at least text or media attached to it"
                    );
                }

                if (postRequest.getParentId() != null) {
                    throw new PostNotValidException(
                            "Post with category NORMAL cannot have a parent"
                    );
                }

                if (postRequest.getRepostOfId() != null) {
                    throw new PostNotValidException(
                            "Post with category NORMAL cannot be a repost"
                    );
                }

                break;

            case COMMENT:
                if ((postRequest.getContent() == null || postRequest.getContent().isBlank())
                        && postRequest.getMediaId() == null) {
                    throw new PostNotValidException(
                            "Post with category COMMENT should have at least text or media attached to it"
                    );
                }

                if (postRequest.getParentId() == null) {
                    throw new PostNotValidException(
                            "Post with category COMMENT should have a parent post"
                    );
                }

                /*
                If the `repostOfId` is not null, then the comment is a quote
                sent as a comment. But a repost as a comment is not permitted.
                 */

                break;

            case QUOTE:
                if ((postRequest.getContent() == null || postRequest.getContent().isBlank())
                        && postRequest.getMediaId() == null) {
                    throw new PostNotValidException(
                            "Post with category QUOTE should have at least text or media attached to it"
                    );
                }

                if (postRequest.getRepostOfId() == null) {
                    throw new PostNotValidException(
                            "Post with category QUOTE should reference a post"
                    );
                }

                if (postRequest.getParentId() != null) {
                    throw new  PostNotValidException(
                            "Post with category QUOTE cannot have a parent"
                    );
                }

                break;

            case REPOST:
                if ((postRequest.getContent() != null && !postRequest.getContent().isBlank())
                        || postRequest.getMediaId() != null) {
                    throw new PostNotValidException(
                            "Post with category REPOST cannot have text or media attached to it"
                    );
                }

                if (postRequest.getRepostOfId() == null) {
                    throw new PostNotValidException(
                            "Post with category REPOST should reference a post"
                    );
                }

                if (postRequest.getParentId() != null) {
                    throw new  PostNotValidException(
                            "Post with category REPOST cannot have a parent"
                    );
                }

                break;
        }

        if (postRequest.getMediaId() != null) {
            Media media = mediaRepository.findById(postRequest.getMediaId())
                    .orElseThrow(() -> new MediaNotFoundException(postRequest.getMediaId()));
            post.setMedia(media);
        }

        if (postRequest.getParentId() != null) {
            Post parentPost = postRepository.findById(postRequest.getParentId())
                    .orElseThrow(() -> new PostNotFoundException(postRequest.getParentId()));
            post.setParent(parentPost);
        }

        if (postRequest.getRepostOfId() != null) {
            Post repostOfPost = postRepository.findById(postRequest.getRepostOfId())
                    .orElseThrow(() -> new PostNotFoundException(postRequest.getRepostOfId()));
            post.setRepostOf(repostOfPost);
        }

        if (post.getParent() != null) {
            postRepository.incrementCommentCount(post.getParent().getId());
        }

        return new PostResponse(postRepository.save(post));
    }

    public void deletePost(Long postId, String username) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(
                        "User not found with username: <%s>".formatted(username))
                );

        if (!post.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("You are not the owner of this post");
        }
        post.setDeletedAt(OffsetDateTime.now());
        post.setUser(null);
        postRepository.save(post);
    }

    public PostResponse updatePost(Long postId, PostUpdateRequest postUpdateRequest, String username) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(
                        "User not found with username: <%s>".formatted(username))
                );

        if (!post.getUser().getId().equals(user.getId())) {
            throw new UnAuthorizedUpdateException(
                    "User with ID: <%d> is not the owner of post with ID: <%d>".formatted(user.getId(), post.getId())
            );
        }

        if ((postUpdateRequest.getContent() == null || postUpdateRequest.getContent().isBlank())
                && postUpdateRequest.getMediaId() == null) {
            throw new PostNotValidException(
                    "Updating post should have at least text or media attached to it"
            );
        }

        if (postUpdateRequest.getContent() == null || postUpdateRequest.getContent().isBlank()) {
            post.setContent(null);
        } else {
            post.setContent(postUpdateRequest.getContent());
        }

        if (postUpdateRequest.getMediaId() == null) {
            post.setMedia(null);
        } else {
            Media media = mediaRepository.findById(postUpdateRequest.getMediaId())
                    .orElseThrow(() -> new MediaNotFoundException(postUpdateRequest.getMediaId()));
            post.setMedia(media);
        }

        return new PostResponse(postRepository.save(post));
    }

    public Page<PostResponse> getUserPosts(Long userId, Pageable pageable) {
        return postRepository.findPostsByUserId(userId, pageable)
                .map(PostResponse::new);
    }
}
