package com.github.ferigeek.sarv.dto.response;

public record StoredObject(
        String objectKey,
        String mimeType,
        long size
) { }
