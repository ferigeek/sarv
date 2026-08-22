package com.github.ferigeek.sarv.service;

import com.github.ferigeek.sarv.dto.response.MediaMetadataResponse;
import com.github.ferigeek.sarv.dto.response.MediaResponse;
import com.github.ferigeek.sarv.dto.response.StoredObject;
import com.github.ferigeek.sarv.entity.Media;
import com.github.ferigeek.sarv.entity.User;
import com.github.ferigeek.sarv.exception.MediaNotFoundException;
import com.github.ferigeek.sarv.exception.StorageException;
import com.github.ferigeek.sarv.exception.UserNotFoundException;
import com.github.ferigeek.sarv.repository.MediaRepository;
import com.github.ferigeek.sarv.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    @Mock
    private MediaRepository mediaRepository;
    @Mock
    private ObjectStorageService objectStorageService;
    @Mock
    private UserRepository userRepository;

    @Mock
    private MultipartFile multipartFile;

    @InjectMocks
    private MediaService mediaService;

    private User owner;
    private Media media;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);
        owner.setUsername("alice");
        owner.setDisplayName("Alice");
        owner.setEmail("alice@example.com");
        owner.setPasswordHash("hash");
        owner.setCreatedAt(OffsetDateTime.now());

        media = new Media();
        media.setId(10L);
        media.setSize(1234L);
        media.setName("photo.png");
        media.setMimeType("image/png");
        media.setSha256("abc123sha256hashabc123sha256hashabc123sha256hashabc123sha256hash12");
        media.setCreatedAt(OffsetDateTime.now());
        media.setOwner(owner);
    }

    // -----------------------------------------------------------------------
    // uploadMedia
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("uploadMedia")
    class UploadMedia {

        @Test
        @DisplayName("happy path: uploads and returns MediaResponse")
        void happyPath() throws Exception {
            byte[] bytes = "filecontent".getBytes();
            when(multipartFile.getBytes()).thenReturn(bytes);
            when(multipartFile.getContentType()).thenReturn("image/png");
            when(multipartFile.getOriginalFilename()).thenReturn("photo.png");
            StoredObject stored = new StoredObject("abc123", "image/png", bytes.length, "abc123");
            when(objectStorageService.uploadObject(eq(bytes), eq("image/png"))).thenReturn(stored);
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(owner));
            when(mediaRepository.save(any(Media.class))).thenAnswer(inv -> {
                Media m = inv.getArgument(0);
                m.setId(10L);
                return m;
            });

            MediaResponse res = mediaService.uploadMedia(multipartFile, "alice");

            assertThat(res.getId()).isEqualTo(10L);
            assertThat(res.getUrl()).isEqualTo("/api/media/10");

            ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
            verify(mediaRepository).save(captor.capture());
            Media saved = captor.getValue();
            assertThat(saved.getSize()).isEqualTo(bytes.length);
            assertThat(saved.getName()).isEqualTo("photo.png");
            assertThat(saved.getMimeType()).isEqualTo("image/png");
            assertThat(saved.getSha256()).isEqualTo("abc123");
            assertThat(saved.getOwner()).isEqualTo(owner);
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getCreatedAt()).isAfter(OffsetDateTime.now().minusSeconds(5));
            verify(objectStorageService).uploadObject(bytes, "image/png");
        }

        @Test
        @DisplayName("should default name to unknown when originalFilename null")
        void shouldDefaultNameWhenNull() throws Exception {
            byte[] bytes = "abc".getBytes();
            when(multipartFile.getBytes()).thenReturn(bytes);
            when(multipartFile.getContentType()).thenReturn("image/jpeg");
            when(multipartFile.getOriginalFilename()).thenReturn(null);
            StoredObject stored = new StoredObject("sha", "image/jpeg", 3, "sha");
            when(objectStorageService.uploadObject(any(), any())).thenReturn(stored);
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(owner));
            when(mediaRepository.save(any(Media.class))).thenAnswer(inv -> inv.getArgument(0));

            mediaService.uploadMedia(multipartFile, "alice");

            ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
            verify(mediaRepository).save(captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo("unknown");
        }

        @Test
        @DisplayName("should default mimeType to application/octet-stream when null")
        void shouldDefaultMimeWhenNull() throws Exception {
            byte[] bytes = "data".getBytes();
            when(multipartFile.getBytes()).thenReturn(bytes);
            when(multipartFile.getContentType()).thenReturn(null);
            when(multipartFile.getOriginalFilename()).thenReturn("file.bin");
            StoredObject stored = new StoredObject("sha", null, 4, "sha");
            when(objectStorageService.uploadObject(eq(bytes), isNull())).thenReturn(stored);
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(owner));
            when(mediaRepository.save(any(Media.class))).thenAnswer(inv -> inv.getArgument(0));

            mediaService.uploadMedia(multipartFile, "alice");

            ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
            verify(mediaRepository).save(captor.capture());
            assertThat(captor.getValue().getMimeType()).isEqualTo("application/octet-stream");
        }

        @Test
        @DisplayName("should use stored.size and sha256, not file size directly")
        void shouldUseStoredValues() throws Exception {
            byte[] bytes = "hello".getBytes();
            when(multipartFile.getBytes()).thenReturn(bytes);
            when(multipartFile.getContentType()).thenReturn("text/plain");
            when(multipartFile.getOriginalFilename()).thenReturn("a.txt");
            StoredObject stored = new StoredObject("key123", "text/plain", 999L, "sha999");
            when(objectStorageService.uploadObject(any(), any())).thenReturn(stored);
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(owner));
            when(mediaRepository.save(any(Media.class))).thenAnswer(inv -> inv.getArgument(0));

            mediaService.uploadMedia(multipartFile, "alice");

            ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
            verify(mediaRepository).save(captor.capture());
            assertThat(captor.getValue().getSize()).isEqualTo(999L);
            assertThat(captor.getValue().getSha256()).isEqualTo("sha999");
        }

        @Test
        @DisplayName("should throw StorageException when file.getBytes throws IOException")
        void shouldThrowWhenReadFails() throws Exception {
            when(multipartFile.getBytes()).thenThrow(new IOException("read fail"));

            StorageException ex = assertThrows(StorageException.class, () -> mediaService.uploadMedia(multipartFile, "alice"));

            assertThat(ex.getMessage()).contains("Failed to read uploaded file");
            assertThat(ex.getCause()).isInstanceOf(IOException.class);
            verify(objectStorageService, never()).uploadObject(any(), any());
            verify(mediaRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw UserNotFoundException when owner not found (after upload)")
        void shouldThrowWhenOwnerNotFound() throws Exception {
            byte[] bytes = "data".getBytes();
            when(multipartFile.getBytes()).thenReturn(bytes);
            when(multipartFile.getContentType()).thenReturn("image/png");
            when(multipartFile.getOriginalFilename()).thenReturn("x.png");
            StoredObject stored = new StoredObject("sha", "image/png", 4, "sha");
            when(objectStorageService.uploadObject(any(), any())).thenReturn(stored);
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            UserNotFoundException ex = assertThrows(UserNotFoundException.class,
                    () -> mediaService.uploadMedia(multipartFile, "ghost"));

            assertThat(ex.getMessage()).contains("ghost");
            verify(mediaRepository, never()).save(any());
            // upload already happened before user check (current behavior)
            verify(objectStorageService).uploadObject(bytes, "image/png");
        }

        @Test
        @DisplayName("should propagate StorageException from objectStorageService upload")
        void shouldPropagateStorageException() throws Exception {
            byte[] bytes = "data".getBytes();
            when(multipartFile.getBytes()).thenReturn(bytes);
            when(multipartFile.getContentType()).thenReturn("image/png");
            when(multipartFile.getOriginalFilename()).thenReturn("x.png");
            when(objectStorageService.uploadObject(any(), any())).thenThrow(new StorageException("store fail"));

            assertThrows(StorageException.class, () -> mediaService.uploadMedia(multipartFile, "alice"));
            verify(mediaRepository, never()).save(any());
        }

        @Test
        @DisplayName("should handle empty file bytes")
        void shouldHandleEmptyBytes() throws Exception {
            byte[] bytes = new byte[0];
            when(multipartFile.getBytes()).thenReturn(bytes);
            when(multipartFile.getContentType()).thenReturn("application/octet-stream");
            when(multipartFile.getOriginalFilename()).thenReturn("empty.bin");
            StoredObject stored = new StoredObject("emptySha", "application/octet-stream", 0, "emptySha");
            when(objectStorageService.uploadObject(eq(bytes), eq("application/octet-stream"))).thenReturn(stored);
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(owner));
            when(mediaRepository.save(any(Media.class))).thenAnswer(inv -> {
                Media m = inv.getArgument(0);
                m.setId(11L);
                return m;
            });

            MediaResponse res = mediaService.uploadMedia(multipartFile, "alice");

            assertThat(res.getId()).isEqualTo(11L);
            ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
            verify(mediaRepository).save(captor.capture());
            assertThat(captor.getValue().getSize()).isZero();
        }
    }

    // -----------------------------------------------------------------------
    // getMediaEntity
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("getMediaEntity")
    class GetMediaEntity {

        @Test
        @DisplayName("should return media when found")
        void shouldReturnWhenFound() {
            when(mediaRepository.findById(10L)).thenReturn(Optional.of(media));

            Media result = mediaService.getMediaEntity(10L);

            assertThat(result).isEqualTo(media);
        }

        @Test
        @DisplayName("should throw MediaNotFoundException when not found")
        void shouldThrowWhenNotFound() {
            when(mediaRepository.findById(99L)).thenReturn(Optional.empty());

            MediaNotFoundException ex = assertThrows(MediaNotFoundException.class,
                    () -> mediaService.getMediaEntity(99L));

            assertThat(ex.getMessage()).contains("99");
        }

        @Test
        @DisplayName("should delegate with correct id")
        void shouldDelegateWithCorrectId() {
            when(mediaRepository.findById(10L)).thenReturn(Optional.of(media));

            mediaService.getMediaEntity(10L);

            verify(mediaRepository).findById(10L);
        }
    }

    // -----------------------------------------------------------------------
    // downloadMedia
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("downloadMedia")
    class DownloadMedia {

        @Test
        @DisplayName("should return resource via sha256")
        void shouldReturnResource() {
            Resource resource = mock(Resource.class);
            when(mediaRepository.findById(10L)).thenReturn(Optional.of(media));
            when(objectStorageService.download("abc123sha256hashabc123sha256hashabc123sha256hashabc123sha256hash12"))
                    .thenReturn(resource);

            Resource result = mediaService.downloadMedia(10L);

            assertThat(result).isEqualTo(resource);
            verify(objectStorageService).download(media.getSha256());
        }

        @Test
        @DisplayName("should throw MediaNotFoundException when media not found")
        void shouldThrowWhenNotFound() {
            when(mediaRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(MediaNotFoundException.class, () -> mediaService.downloadMedia(99L));
            verify(objectStorageService, never()).download(anyString());
        }

        @Test
        @DisplayName("should use media's sha256 for download")
        void shouldUseSha256() {
            media.setSha256("customSha");
            Resource resource = mock(Resource.class);
            when(mediaRepository.findById(10L)).thenReturn(Optional.of(media));
            when(objectStorageService.download("customSha")).thenReturn(resource);

            mediaService.downloadMedia(10L);

            verify(objectStorageService).download("customSha");
        }
    }

    // -----------------------------------------------------------------------
    // getMediaMetadata
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("getMediaMetadata")
    class GetMediaMetadata {

        @Test
        @DisplayName("should return mapped metadata")
        void shouldReturnMappedMetadata() {
            when(mediaRepository.findById(10L)).thenReturn(Optional.of(media));

            MediaMetadataResponse res = mediaService.getMediaMetadata(10L);

            assertThat(res.getId()).isEqualTo(10L);
            assertThat(res.getSize()).isEqualTo(1234L);
            assertThat(res.getName()).isEqualTo("photo.png");
            assertThat(res.getMimeType()).isEqualTo("image/png");
            assertThat(res.getCreatedAt()).isEqualTo(media.getCreatedAt());
        }

        @Test
        @DisplayName("should throw MediaNotFoundException when not found")
        void shouldThrowWhenNotFound() {
            when(mediaRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(MediaNotFoundException.class, () -> mediaService.getMediaMetadata(99L));
        }

        @Test
        @DisplayName("should map size and name correctly for different media")
        void shouldMapDifferentMedia() {
            Media m2 = new Media();
            m2.setId(20L);
            m2.setSize(999L);
            m2.setName("doc.pdf");
            m2.setMimeType("application/pdf");
            m2.setCreatedAt(OffsetDateTime.now());
            when(mediaRepository.findById(20L)).thenReturn(Optional.of(m2));

            MediaMetadataResponse res = mediaService.getMediaMetadata(20L);

            assertThat(res.getSize()).isEqualTo(999L);
            assertThat(res.getName()).isEqualTo("doc.pdf");
            assertThat(res.getMimeType()).isEqualTo("application/pdf");
        }
    }
}
