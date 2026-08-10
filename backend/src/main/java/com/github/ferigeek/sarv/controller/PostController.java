package com.github.ferigeek.sarv.controller;

import com.github.ferigeek.sarv.aspect.LogEvent;
import com.github.ferigeek.sarv.dto.request.PostRequest;
import com.github.ferigeek.sarv.dto.request.PostUpdateRequest;
import com.github.ferigeek.sarv.dto.response.PostResponse;
import com.github.ferigeek.sarv.entity.type.EventType;
import com.github.ferigeek.sarv.service.PostService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    @Autowired
    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping("/{postId}")
    @LogEvent(EventType.VIEW_POST)
    public PostResponse getPost(@Positive @PathVariable Long postId) {
        return postService.getPost(postId);
    }

    @PostMapping
    @LogEvent(EventType.CREATE_POST)
    public ResponseEntity<?> createPost(
            @Valid @RequestBody PostRequest postRequest,
            @AuthenticationPrincipal UserDetails userDetails) {
        PostResponse postResponse = postService.createPost(postRequest, userDetails.getUsername());
        return ResponseEntity.created(URI.create("/api/posts/" + postResponse.getId())).body(postResponse);
    }

    @DeleteMapping("/{postId}")
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
    @PutMapping("/{postId}")
    public PostResponse updatePost(
            @Positive @PathVariable Long postId,
            @Valid @RequestBody PostUpdateRequest postUpdateRequest,
            @AuthenticationPrincipal UserDetails userDetails) {
        return postService.updatePost(postId, postUpdateRequest, userDetails.getUsername());
    }
}
