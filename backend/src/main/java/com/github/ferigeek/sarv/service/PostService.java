package com.github.ferigeek.sarv.service;

import com.github.ferigeek.sarv.dto.request.PostRequest;
import com.github.ferigeek.sarv.dto.response.PostResponse;
import com.github.ferigeek.sarv.entity.Media;
import com.github.ferigeek.sarv.entity.Post;
import com.github.ferigeek.sarv.entity.User;
import com.github.ferigeek.sarv.repository.MediaRepository;
import com.github.ferigeek.sarv.repository.PostRepository;
import com.github.ferigeek.sarv.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final MediaRepository mediaRepository;

    public PostService(PostRepository postRepository, UserRepository userRepository, MediaRepository mediaRepository) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.mediaRepository = mediaRepository;
    }

    public PostResponse getPost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        return new PostResponse(post);
    }

    public PostResponse createPost(PostRequest postRequest, String username) {
        User user = userRepository.findByUsername(username);
        Post post = new Post();

        post.setUser(user);
        post.setPostCategory(postRequest.getPostCategory());
        post.setCreatedAt(OffsetDateTime.now());
        if (postRequest.getMediaId() != null) {
            Media media = mediaRepository.findById(postRequest.getMediaId())
                    .orElseThrow(() -> new RuntimeException("Media not found"));
            post.setMedia(media);
        }
        if (postRequest.getParentId() != null) {
            Post parentPost = postRepository.findById(postRequest.getParentId())
                    .orElseThrow(() -> new RuntimeException("Parent post not found"));
            post.setParent(parentPost);
        }
        if (postRequest.getRepostOfId() != null) {
            Post repostOfPost = postRepository.findById(postRequest.getRepostOfId())
                    .orElseThrow(() -> new RuntimeException("Repost of post not found"));
            post.setRepostOf(repostOfPost);
        }
        return new PostResponse(postRepository.save(post));
    }

    public void deletePost(Long postId, String username) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        User user = userRepository.findByUsername(username);
        if (post.getUser() != user) {
            throw new RuntimeException("You are not the owner of this post");
        }
        post.setDeletedAt(OffsetDateTime.now());
        post.setUser(null);
        postRepository.save(post);
    }

    public PostResponse updatePost(Long postId, PostRequest postRequest, String username) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        User user = userRepository.findByUsername(username);
        if (post.getUser() != user) {
            throw new RuntimeException("You are not the owner of this post");
        }
        post.setContent(postRequest.getContent());
        if (post.getMedia() != null && !post.getMedia().getId().equals(postRequest.getMediaId())) {
            Media media = mediaRepository.findById(postRequest.getMediaId())
                    .orElseThrow(() -> new RuntimeException("Media not found"));
            post.setMedia(media);
        }
        return new PostResponse(postRepository.save(post));
    }
}
