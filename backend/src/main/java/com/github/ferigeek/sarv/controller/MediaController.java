package com.github.ferigeek.sarv.controller;

import com.github.ferigeek.sarv.dto.response.MediaMetadataResponse;
import com.github.ferigeek.sarv.dto.response.MediaResponse;
import com.github.ferigeek.sarv.entity.Media;
import com.github.ferigeek.sarv.service.MediaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final MediaService mediaService;

    @Autowired
    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping
    public ResponseEntity<MediaResponse> uploadMedia(
            @RequestParam MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {
        MediaResponse response = mediaService.uploadMedia(file, userDetails.getUsername());
        return ResponseEntity.created(URI.create("/api/media/" + response.getId())).body(response);
    }

    @GetMapping("/{mediaId}")
    public ResponseEntity<Resource> getMedia(@PathVariable Long mediaId) {
        Media media = mediaService.getMediaEntity(mediaId);
        Resource resource = mediaService.downloadMedia(mediaId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.getMimeType()))
                .body(resource);
    }

    @GetMapping("/{mediaId}/metadata")
    public MediaMetadataResponse getMediaMetadata(@PathVariable Long mediaId) {
        return mediaService.getMediaMetadata(mediaId);
    }
}
