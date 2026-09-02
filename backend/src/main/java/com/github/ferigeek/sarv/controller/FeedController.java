package com.github.ferigeek.sarv.controller;

import com.github.ferigeek.sarv.aspect.LogEvent;
import com.github.ferigeek.sarv.dto.response.PostResponse;
import com.github.ferigeek.sarv.entity.type.EventType;
import com.github.ferigeek.sarv.service.FeedService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
    @LogEvent(EventType.REQUEST_FEED)
    public Page<PostResponse> getChronological(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return feedService.getChronological(pageable);
    }
}
