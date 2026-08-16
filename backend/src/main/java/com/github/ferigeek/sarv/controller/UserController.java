package com.github.ferigeek.sarv.controller;

import com.github.ferigeek.sarv.aspect.LogEvent;
import com.github.ferigeek.sarv.dto.request.UserUpdateRequest;
import com.github.ferigeek.sarv.dto.response.UserResponse;
import com.github.ferigeek.sarv.dto.response.UserSummaryResponse;
import com.github.ferigeek.sarv.entity.type.EventType;
import com.github.ferigeek.sarv.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{userId}")
    @LogEvent(EventType.VIEW_PROFILE)
    public UserResponse getUser(@Positive @PathVariable Long userId) {
        return userService.getUser(userId);
    }

    @GetMapping("/me")
    @LogEvent(EventType.VIEW_PROFILE)
    public UserResponse getCurrentUserProfile(@AuthenticationPrincipal UserDetails userDetails) {
        return userService.getUserByUsername(userDetails.getUsername());
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
    @PutMapping("/me")
    public UserResponse updateCurrentUserProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UserUpdateRequest userUpdateRequest) {
        return userService.updateUser(userDetails.getUsername(), userUpdateRequest);
    }

    @GetMapping
    public List<UserSummaryResponse> searchUser(@RequestParam String query) {
        return userService.searchUsers(query);
    }
}
