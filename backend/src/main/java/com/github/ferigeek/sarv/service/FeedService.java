package com.github.ferigeek.sarv.service;

import com.github.ferigeek.sarv.client.RecommendationClient;
import com.github.ferigeek.sarv.client.RecommendationResponse;
import com.github.ferigeek.sarv.dto.response.PostResponse;
import com.github.ferigeek.sarv.entity.Post;
import com.github.ferigeek.sarv.exception.UserNotFoundException;
import com.github.ferigeek.sarv.repository.PostRepository;
import com.github.ferigeek.sarv.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FeedService {

    private static final Logger log = LoggerFactory.getLogger(FeedService.class);

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final RecommendationClient recommendationClient;

    public FeedService(PostRepository postRepository, UserRepository userRepository, RecommendationClient recommendationClient) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.recommendationClient = recommendationClient;
    }

    public Page<PostResponse> getChronological(Pageable pageable) {
        return postRepository.findChronologicalFeed(pageable)
                .map(PostResponse::new);
    }

    public Page<PostResponse> getRecommended(String username, Pageable pageable) {
        Long userId = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found with username: <%s>".formatted(username)))
                .getId();

        try {
            RecommendationResponse response = recommendationClient.getRecommendations(userId, pageable.getPageNumber(), pageable.getPageSize());
            List<Long> rankedIds = extractIds(response);

            if (rankedIds.isEmpty()) {
                log.info("Recommendation returned empty for userId={}, falling back to chronological", userId);
                return getChronological(pageable);
            }

            List<Post> hyd = postRepository.findAllByIdsFiltered(rankedIds);
            Map<Long, Post> map = hyd.stream().collect(Collectors.toMap(Post::getId, Function.identity(), (a, b) -> a));

            List<PostResponse> content = rankedIds.stream()
                    .map(map::get)
                    .filter(p -> p != null && p.getDeletedAt() == null)
                    .map(PostResponse::new)
                    .toList();

            // Use total from recommendation service for Page metadata; fallback to content size if missing
            long total = response.total() > 0 ? response.total() : content.size();
            // If recommendation service did not provide total (legacy), estimate from page
            if (response.total() == 0 && !content.isEmpty()) {
                total = content.size();
            }

            return new PageImpl<>(content, pageable, total);
        } catch (UserNotFoundException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Failed to fetch recommended feed for user {} (page {} size {}), falling back to chronological: {}", username, pageable.getPageNumber(), pageable.getPageSize(), ex.toString());
            return getChronological(pageable);
        }
    }

    private List<Long> extractIds(RecommendationResponse response) {
        if (response == null || response.posts() == null || response.posts().isEmpty()) {
            return List.of();
        }
        return response.posts().stream()
                .map(p -> {
                    try {
                        return Long.parseLong(p.postId());
                    } catch (NumberFormatException e) {
                        log.warn("Skipping invalid post_id {}", p.postId());
                        return null;
                    }
                })
                .filter(id -> id != null)
                .toList();
    }
}
