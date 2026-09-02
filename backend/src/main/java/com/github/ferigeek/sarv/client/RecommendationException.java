package com.github.ferigeek.sarv.client;

public class RecommendationException extends RuntimeException {

    public RecommendationException(String message, Throwable cause) {
        super(message, cause);
    }

    public RecommendationException(String message) {
        super(message);
    }
}
