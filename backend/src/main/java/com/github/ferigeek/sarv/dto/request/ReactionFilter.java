package com.github.ferigeek.sarv.dto.request;

import com.github.ferigeek.sarv.entity.Reaction;

public enum ReactionFilter {
    ALL,
    LIKE,
    DISLIKE;

    public Short toReactionType() {
        return switch (this) {
            case ALL -> null;
            case LIKE -> Reaction.LIKE;
            case DISLIKE -> Reaction.DISLIKE;
        };
    }
}
