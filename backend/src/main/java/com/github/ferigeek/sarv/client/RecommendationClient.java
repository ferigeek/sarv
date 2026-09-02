package com.github.ferigeek.sarv.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.List;

@Component
public class RecommendationClient {

    private static final Logger log = LoggerFactory.getLogger(RecommendationClient.class);

    private final RestClient restClient;

    public RecommendationClient(RestClient recommendationRestClient) {
        this.restClient = recommendationRestClient;
    }

    public RecommendationResponse getRecommendations(Long userId, int page, int size) {
        try {
            RecommendationResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/feed")
                            .queryParam("user_id", userId)
                            .queryParam("page", page)
                            .queryParam("size", size)
                            .build())
                    .retrieve()
                    .body(RecommendationResponse.class);

            if (response == null) {
                throw new RecommendationException("Empty response from recommendation service");
            }
            return response;
        } catch (RestClientException ex) {
            log.warn("Recommendation service call failed for userId={} page={} size={}: {}", userId, page, size, ex.getMessage());
            throw new RecommendationException("Recommendation service unavailable", ex);
        } catch (Exception ex) {
            log.warn("Unexpected error calling recommendation service", ex);
            throw new RecommendationException("Unexpected recommendation error", ex);
        }
    }

    public List<Long> getRankedPostIds(Long userId, int page, int size) {
        RecommendationResponse response = getRecommendations(userId, page, size);
        if (response.posts() == null || response.posts().isEmpty()) {
            return Collections.emptyList();
        }
        return response.posts().stream()
                .map(RankedPost::postId)
                .map(this::parsePostId)
                .filter(id -> id != null)
                .toList();
    }

    private Long parsePostId(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            log.warn("Skipping invalid post_id from recommendation service: {}", raw);
            return null;
        }
    }
}
