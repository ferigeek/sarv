package com.github.ferigeek.sarv.controller;

import com.github.ferigeek.sarv.dto.request.PostRequest;
import com.github.ferigeek.sarv.dto.response.PostResponse;
import com.github.ferigeek.sarv.service.PostService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping("/{postId}")
    public PostResponse getPost(@PathVariable Long postId) {
        return postService.getPost(postId);
    }

    @PostMapping
    public ResponseEntity<?> createPost(
            @RequestBody PostRequest postRequest,
            @AuthenticationPrincipal UserDetails userDetails) {
        PostResponse postResponse = postService.createPost(postRequest, userDetails.getUsername());
        return ResponseEntity.created(URI.create("/api/posts/" + postResponse.getId())).build();
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<?> deletePost(
            @PathVariable Long postId,
            @AuthenticationPrincipal UserDetails userDetails) {
        postService.deletePost(postId, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{postId}")
    public PostResponse updatePost(
            @PathVariable Long postId,
            @RequestBody PostRequest postRequest,
            @AuthenticationPrincipal UserDetails userDetails) {
        return postService.updatePost(postId, postRequest, userDetails.getUsername());
    }
}
