package com.github.ferigeek.sarv.exception;

public class PostNotFoundException extends RuntimeException {

    public PostNotFoundException(Long postId) {
        super("Post not found with ID: <%d>".formatted(postId));
    }
}
