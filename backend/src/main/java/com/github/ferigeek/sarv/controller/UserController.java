package com.github.ferigeek.sarv.controller;

import com.github.ferigeek.sarv.dto.response.UserSummaryResponse;
import com.github.ferigeek.sarv.dto.response.UserResponse;
import com.github.ferigeek.sarv.service.FollowService;
import com.github.ferigeek.sarv.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final FollowService followService;

    @Autowired
    public UserController(UserService userService,  FollowService followService) {
        this.userService = userService;
        this.followService = followService;
    }

    @GetMapping("/{userId}")
    public UserResponse getUser(@PathVariable Long userId) {
        return userService.getUser(userId);
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

    @GetMapping("/me")
    public UserResponse getCurrentUserProfile(@AuthenticationPrincipal UserDetails userDetails) {
        return userService.getUserByUsername(userDetails.getUsername());
    }
}
