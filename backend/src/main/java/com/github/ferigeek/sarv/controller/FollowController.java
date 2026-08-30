package com.github.ferigeek.sarv.controller;

import com.github.ferigeek.sarv.aspect.LogEvent;
import com.github.ferigeek.sarv.dto.response.UserSummaryResponse;
import com.github.ferigeek.sarv.entity.type.EventType;
import com.github.ferigeek.sarv.service.FollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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
    public Page<UserSummaryResponse> getFollowers(
            @PathVariable Long userId,
            @PageableDefault(size = 20, sort = "follower.username") Pageable pageable) {
        return followService.getFollowers(userId, pageable);
    }

    @GetMapping("/{userId}/following")
    public List<UserSummaryResponse> getFollowings(@PathVariable Long userId) {
        return followService.getFollowing(userId);
    }

    @PostMapping("/{userId}/followers")
    @ResponseStatus(HttpStatus.CREATED)
    @LogEvent(EventType.FOLLOW_USER)
    public void followUser(@PathVariable Long userId, @AuthenticationPrincipal UserDetails userDetails) {
        followService.followUser(userDetails.getUsername(), userId);
    }

    @DeleteMapping("/{userId}/followers")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @LogEvent(EventType.UNFOLLOW_USER)
    public void unfollowUser(@PathVariable Long userId, @AuthenticationPrincipal UserDetails userDetails) {
        followService.unfollowUser(userDetails.getUsername(), userId);
    }
}
