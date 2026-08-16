package com.github.ferigeek.sarv.controller;

import com.github.ferigeek.sarv.aspect.LogEvent;
import com.github.ferigeek.sarv.dto.request.ReactionRequest;
import com.github.ferigeek.sarv.dto.response.ReactionResponse;
import com.github.ferigeek.sarv.entity.type.EventType;
import com.github.ferigeek.sarv.service.ReactionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts/{postId}/reactions")
public class ReactionController {

    private final ReactionService reactionService;

    @Autowired
    public ReactionController(ReactionService reactionService) {
        this.reactionService = reactionService;
    }

    @PostMapping
    @LogEvent(EventType.LIKE_POST)
    public ReactionResponse addReaction(
            @Positive @PathVariable Long postId,
            @Valid @RequestBody ReactionRequest reactionRequest,
            @AuthenticationPrincipal UserDetails userDetails) {
        return reactionService.addReaction(postId, reactionRequest, userDetails.getUsername());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeReaction(
            @Positive @PathVariable Long postId,
            @AuthenticationPrincipal UserDetails userDetails) {
        reactionService.removeReaction(postId, userDetails.getUsername());
    }

    @GetMapping
    public ReactionResponse getReactionCounts(
            @Positive @PathVariable Long postId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return reactionService.getReactionCounts(postId, userDetails.getUsername());
    }
}
