package com.github.ferigeek.sarv.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record RecommendationResponse(
        @JsonProperty("user_id") String userId,
        @JsonProperty("posts") List<RankedPost> posts,
        @JsonProperty("page") int page,
        @JsonProperty("size") int size,
        @JsonProperty("total") int total
) {
}
