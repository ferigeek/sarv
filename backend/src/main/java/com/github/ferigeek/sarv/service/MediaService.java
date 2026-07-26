package com.github.ferigeek.sarv.service;

import com.github.ferigeek.sarv.dto.response.MediaMetadataResponse;
import com.github.ferigeek.sarv.dto.response.MediaResponse;
import com.github.ferigeek.sarv.entity.Media;
import com.github.ferigeek.sarv.entity.User;
import com.github.ferigeek.sarv.exception.MediaNotFoundException;
import com.github.ferigeek.sarv.exception.StorageException;
import com.github.ferigeek.sarv.repository.MediaRepository;
import com.github.ferigeek.sarv.repository.UserRepository;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;

@Service
public class MediaService {

    private final MediaRepository mediaRepository;
    private final ObjectStorageService objectStorageService;
    private final UserRepository userRepository;

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
            User owner = userRepository.findByUsername(username);

            Media media = new Media();
            media.setSize(stored.size());
            media.setName(originalName != null ? originalName : "unknown");
            media.setMimeType(mimeType != null ? mimeType : "application/octet-stream");
            media.setSha256(stored.sha256());
            media.setCreatedAt(OffsetDateTime.now());
            media.setOwner(owner);

            media = mediaRepository.save(media);
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
