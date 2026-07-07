package com.github.ferigeek.sarv.controller;

import com.github.ferigeek.sarv.dto.request.UserUpdateRequest;
import com.github.ferigeek.sarv.dto.response.UserResponse;
import com.github.ferigeek.sarv.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{userId}")
    public UserResponse getUser(@PathVariable Long userId) {
        return userService.getUser(userId);
    }

    @GetMapping("/me")
    public UserResponse getCurrentUserProfile(@AuthenticationPrincipal UserDetails userDetails) {
        return userService.getUserByUsername(userDetails.getUsername());
    }

    @PatchMapping("/me")
    public UserResponse updateCurrentUserProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody UserUpdateRequest userUpdateRequest) {
        return userService.updateUser(userDetails.getUsername(), userUpdateRequest);
    }
}
