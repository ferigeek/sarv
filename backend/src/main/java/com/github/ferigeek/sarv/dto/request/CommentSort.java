package com.github.ferigeek.sarv.dto.request;

import org.springframework.data.domain.Sort;

public enum CommentSort {
    NEWEST,
    MOST_LIKED;

    public Sort toSort() {
        return switch (this) {
            case NEWEST -> Sort.by(Sort.Direction.DESC, "createdAt");
            case MOST_LIKED -> Sort.by(Sort.Direction.DESC, "likeCount");
        };
    }
}
