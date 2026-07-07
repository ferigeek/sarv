package com.github.ferigeek.sarv.service;

import com.github.ferigeek.sarv.dto.response.MediaMetadataResponse;
import com.github.ferigeek.sarv.dto.response.MediaResponse;
import com.github.ferigeek.sarv.dto.response.StoredObject;
import com.github.ferigeek.sarv.entity.Media;
import com.github.ferigeek.sarv.entity.User;
import com.github.ferigeek.sarv.repository.MediaRepository;
import com.github.ferigeek.sarv.repository.UserRepository;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
        StoredObject storedObject = objectStorageService.uploadObject(file);
        Media media = new Media();

        media.setCreatedAt(OffsetDateTime.now());
        media.setName(file.getOriginalFilename());
        media.setMimeType(storedObject.mimeType());

        User user = userRepository.findByUsername(username);
        media.setOwner(user);

        return new MediaResponse(mediaRepository.save(media).getId(), storedObject.objectKey());
    }

    public Resource getMedia(String mediaId) {
        Media media = mediaRepository.findById(Long.parseLong(mediaId)).orElseThrow();
        return objectStorageService.download(media.getName());
    }

    public MediaMetadataResponse getMediaMetadata(String mediaId) {
        Media media = mediaRepository.findById(Long.parseLong(mediaId)).orElseThrow();
        return new MediaMetadataResponse(media);
    }
}
