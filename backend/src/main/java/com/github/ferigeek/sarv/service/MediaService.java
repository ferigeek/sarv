package com.github.ferigeek.sarv.service;

import com.github.ferigeek.sarv.dto.response.MediaMetadataResponse;
import com.github.ferigeek.sarv.dto.response.MediaResponse;
import com.github.ferigeek.sarv.entity.Media;
import com.github.ferigeek.sarv.entity.User;
import com.github.ferigeek.sarv.exception.MediaNotFoundException;
import com.github.ferigeek.sarv.exception.StorageException;
import com.github.ferigeek.sarv.exception.UserNotFoundException;
import com.github.ferigeek.sarv.repository.MediaRepository;
import com.github.ferigeek.sarv.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;

@Service
public class MediaService {

    private final MediaRepository mediaRepository;
    private final ObjectStorageService objectStorageService;
    private final UserRepository userRepository;

    @Autowired
    public MediaService(MediaRepository mediaRepository, ObjectStorageService objectStorageService, UserRepository userRepository) {
        this.mediaRepository = mediaRepository;
        this.objectStorageService = objectStorageService;
        this.userRepository = userRepository;
    }

    public MediaResponse uploadMedia(MultipartFile file, String username) {
        try {
            byte[] bytes = file.getBytes();
            String mimeType = file.getContentType();
            String originalName = file.getOriginalFilename();

            var stored = objectStorageService.uploadObject(bytes, mimeType);

            // Content-addressed dedup: identical bytes share one stored file
            // (see LocalStorageService) and one Media row. Return the existing
            // row when this content was uploaded before.
            var existing = mediaRepository.findBySha256(stored.sha256());
            if (existing.isPresent()) {
                Media hit = existing.get();
                return new MediaResponse(hit.getId(), "/api/media/" + hit.getId());
            }

            User owner = userRepository.findByUsername(username)
                    .orElseThrow(() -> new UserNotFoundException(
                            "Owner not found with username: <%s>".formatted(username))
                    );

            Media media = new Media();
            media.setSize(stored.size());
            media.setName(originalName != null ? originalName : "unknown");
            media.setMimeType(mimeType != null ? mimeType : "application/octet-stream");
            media.setSha256(stored.sha256());
            media.setCreatedAt(OffsetDateTime.now());
            media.setOwner(owner);

            try {
                media = mediaRepository.save(media);
            } catch (DataIntegrityViolationException e) {
                // Lost a race with a concurrent upload of the same content:
                // the winner's row is now visible, return it instead of 500.
                return mediaRepository.findBySha256(stored.sha256())
                        .map(winner -> new MediaResponse(winner.getId(), "/api/media/" + winner.getId()))
                        .orElseThrow(() -> e);
            }
            return new MediaResponse(media.getId(), "/api/media/" + media.getId());
        } catch (IOException e) {
            throw new StorageException("Failed to read uploaded file", e);
        }
    }

    public Resource downloadMedia(Long mediaId) {
        Media media = getMediaEntity(mediaId);
        return objectStorageService.download(media.getSha256());
    }

    public Media getMediaEntity(Long mediaId) {
        return mediaRepository.findById(mediaId)
                .orElseThrow(() -> new MediaNotFoundException(mediaId));
    }

    public MediaMetadataResponse getMediaMetadata(Long mediaId) {
        Media media = getMediaEntity(mediaId);
        return new MediaMetadataResponse(media);
    }
}
