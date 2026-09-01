package com.github.ferigeek.sarv.controller;

import com.github.ferigeek.sarv.dto.response.PostResponse;
import com.github.ferigeek.sarv.service.FeedService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    public Page<PostResponse> getChronological(Pageable pageable) {
        return feedService.getChronological(pageable);
    }
}
