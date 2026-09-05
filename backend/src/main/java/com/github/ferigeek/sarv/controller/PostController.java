package com.github.ferigeek.sarv.controller;

import com.github.ferigeek.sarv.aspect.LogEvent;
import com.github.ferigeek.sarv.dto.request.CommentSort;
import com.github.ferigeek.sarv.dto.request.PostRequest;
import com.github.ferigeek.sarv.dto.request.PostUpdateRequest;
import com.github.ferigeek.sarv.dto.request.ReactionFilter;
import com.github.ferigeek.sarv.dto.response.PostResponse;
import com.github.ferigeek.sarv.entity.type.EventType;
import com.github.ferigeek.sarv.service.PostService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@Validated
@RestController
@RequestMapping("/api")
public class PostController {

    private final PostService postService;

    @Autowired
    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping("/posts/{postId}")
    @LogEvent(EventType.VIEW_POST)
    public PostResponse getPost(@Positive @PathVariable Long postId) {
        return postService.getPost(postId);
    }

    @PostMapping("/posts")
    @LogEvent(EventType.CREATE_POST)
    public ResponseEntity<?> createPost(
            @Valid @RequestBody PostRequest postRequest,
            @AuthenticationPrincipal UserDetails userDetails) {
        PostResponse postResponse = postService.createPost(postRequest, userDetails.getUsername());
        return ResponseEntity.created(URI.create("/api/posts/" + postResponse.getId())).body(postResponse);
    }

    @DeleteMapping("/posts/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(
            @Positive @PathVariable Long postId,
            @AuthenticationPrincipal UserDetails userDetails) {
        postService.deletePost(postId, userDetails.getUsername());
    }

    /*
    `PUT` method is used instead of `PATCH` method for editing a post; Because
    otherwise if you check the request object and see that an attribute is null,
    you can't distinguish between if the attribute is set to null
    in the JSON request to delete, or the attribute is not mentioned
    in the request to keep it without change.
    Thus, `PUT` is used so that if the user wants the data the bo untouched,
    data is set to its previous value, and if it's needed to be deleted,
    it is set to null.
     */
    @PutMapping("/posts/{postId}")
    public PostResponse updatePost(
            @Positive @PathVariable Long postId,
            @Valid @RequestBody PostUpdateRequest postUpdateRequest,
            @AuthenticationPrincipal UserDetails userDetails) {
        return postService.updatePost(postId, postUpdateRequest, userDetails.getUsername());
    }

    @GetMapping("/users/{userId}/posts")
    public Page<PostResponse> getUserPosts(
            @Positive @PathVariable Long userId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return postService.getUserPosts(userId, pageable);
    }

    @GetMapping("/users/{userId}/reacted-posts")
    public Page<PostResponse> getReactedPosts(
            @Positive @PathVariable Long userId,
            @RequestParam(name = "filter", defaultValue = "ALL") ReactionFilter filter,
            @PageableDefault(size = 10) Pageable pageable) {
        // Ordering is newest reactions first; ignore any client sort to keep ordering well-defined
        Pageable sanitized = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return postService.getReactedPosts(userId, filter, sanitized);
    }

    @GetMapping("/posts/{postId}/comments")
    public Page<PostResponse> getPostComments(
            @Positive @PathVariable Long postId,
            @RequestParam(name = "sortBy", defaultValue = "NEWEST") CommentSort sortBy,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        // Sorting is driven by sortBy; ignore any client sort to keep ordering well-defined
        Pageable sanitized = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sortBy.toSort());
        return postService.getPostComments(postId, sanitized);
    }
}
