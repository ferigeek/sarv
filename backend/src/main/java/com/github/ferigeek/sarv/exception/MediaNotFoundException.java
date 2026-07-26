package com.github.ferigeek.sarv.exception;

public class MediaNotFoundException extends RuntimeException {

    public MediaNotFoundException(Long mediaId) {
        super("Media not found with id: " + mediaId);
    }
}
