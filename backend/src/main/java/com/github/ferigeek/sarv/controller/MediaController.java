package com.github.ferigeek.sarv.controller;

import com.github.ferigeek.sarv.dto.response.MediaMetadataResponse;
import com.github.ferigeek.sarv.dto.response.MediaResponse;
import com.github.ferigeek.sarv.service.MediaService;
import org.springframework.core.io.Resource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping
    public MediaResponse uploadMedia(
            @RequestParam MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {
        return mediaService.uploadMedia(file, userDetails.getUsername());
    }

    @GetMapping("/{mediaId}")
    public Resource getMedia(@PathVariable String mediaId) {
        return mediaService.getMedia(mediaId);
    }

    @GetMapping("/{mediaId}/metadata")
    public MediaMetadataResponse getMediaMetadata(@PathVariable String mediaId) {
        return mediaService.getMediaMetadata(mediaId);
    }
}
