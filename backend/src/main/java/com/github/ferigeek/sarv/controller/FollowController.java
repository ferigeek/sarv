package com.github.ferigeek.sarv.controller;

import com.github.ferigeek.sarv.aspect.LogEvent;
import com.github.ferigeek.sarv.dto.response.UserSummaryResponse;
import com.github.ferigeek.sarv.entity.type.EventType;
import com.github.ferigeek.sarv.service.FollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class FollowController {

    private final FollowService followService;

    @Autowired
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
    @LogEvent(EventType.FOLLOW_USER)
    public ResponseEntity<?> followUser(@PathVariable Long userId, @AuthenticationPrincipal UserDetails userDetails) {
        followService.followUser(userDetails.getUsername(), userId);
        return ResponseEntity.created(null).build();
    }

    @DeleteMapping("/{userId}/followers")
    @LogEvent(EventType.UNFOLLOW_USER)
    public ResponseEntity<?> unfollowUser(@PathVariable Long userId, @AuthenticationPrincipal UserDetails userDetails) {
        followService.unfollowUser(userDetails.getUsername(), userId);
        return ResponseEntity.ok().build();
    }
}
