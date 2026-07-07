package com.github.ferigeek.sarv.controller;

import com.github.ferigeek.sarv.dto.response.UserSummaryResponse;
import com.github.ferigeek.sarv.service.FollowService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    @GetMapping("/{userId}/followers")
    public List<UserSummaryResponse> getFollowers(@PathVariable Long userId) {
        return followService.getFollowers(userId);
    }

    @GetMapping("/{userId}/following")
    public List<UserSummaryResponse> getFollowings(@PathVariable Long userId) {
        return followService.getFollowings(userId);
    }

    @PostMapping("/{userId}/followers")
    public ResponseEntity<?> followUser(@PathVariable Long userId, @AuthenticationPrincipal UserDetails userDetails) {
        followService.followUser(userDetails.getUsername(), userId);
        return ResponseEntity.created(null).build();
    }

    @DeleteMapping("/{userId}/followers")
    public ResponseEntity<?> unfollowUser(@PathVariable Long userId, @AuthenticationPrincipal UserDetails userDetails) {
        followService.unfollowUser(userDetails.getUsername(), userId);
        return ResponseEntity.ok().build();
    }
}
