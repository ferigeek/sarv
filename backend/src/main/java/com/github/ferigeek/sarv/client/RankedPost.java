package com.github.ferigeek.sarv.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RankedPost(
        @JsonProperty("post_id") String postId,
        @JsonProperty("score") double score
) {
}
