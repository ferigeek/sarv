package com.github.ferigeek.sarv.controller;

import com.github.ferigeek.sarv.dto.request.ReactionRequest;
import com.github.ferigeek.sarv.dto.response.ReactionResponse;
import com.github.ferigeek.sarv.service.ReactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts/{postId}/reactions")
public class ReactionController {

    private final ReactionService reactionService;

    public ReactionController(ReactionService reactionService) {
        this.reactionService = reactionService;
    }

    @PostMapping
    public ReactionResponse addReaction(
            @PathVariable Long postId,
            @RequestBody ReactionRequest reactionRequest,
            @AuthenticationPrincipal UserDetails userDetails) {
        return reactionService.addReaction(postId, reactionRequest, userDetails.getUsername());
    }

    @DeleteMapping
    public ResponseEntity<Void> removeReaction(
            @PathVariable Long postId,
            @AuthenticationPrincipal UserDetails userDetails) {
        reactionService.removeReaction(postId, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ReactionResponse getReactionCounts(
            @PathVariable Long postId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return reactionService.getReactionCounts(postId, userDetails.getUsername());
    }
}
