package com.github.ferigeek.sarv.dto.response;

import com.github.ferigeek.sarv.entity.Media;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
public class MediaMetadataResponse {

    private Long id;
    private Long size;
    private String name;
    private String mimeType;
    private OffsetDateTime createdAt;

    public MediaMetadataResponse(Media media) {
        this.id = media.getId();
        this.size = media.getSize();
        this.name = media.getName();
        this.mimeType = media.getMimeType();
        this.createdAt = media.getCreatedAt();
    }
}
