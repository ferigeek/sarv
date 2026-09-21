package com.github.ferigeek.sarv.controller;

import com.github.ferigeek.sarv.dto.response.PostResponse;
import com.github.ferigeek.sarv.service.FeedService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feed")
public class FeedController {

    private final FeedService feedService;

    @Autowired
    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    @GetMapping("/chronological")
    public Page<PostResponse> getChronological(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        String username = userDetails != null ? userDetails.getUsername() : null;
        return feedService.getChronological(pageable, username);
    }

    @GetMapping("/recommended")
    public Page<PostResponse> getRecommended(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable) {
        // Sorting is done by the recommendation server (score desc); ignore any client sort
        Pageable sanitized = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return feedService.getRecommended(userDetails.getUsername(), sanitized);
    }
}
