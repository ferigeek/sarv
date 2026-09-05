package com.github.ferigeek.sarv.service;

import com.github.ferigeek.sarv.client.RecommendationClient;
import com.github.ferigeek.sarv.client.RecommendationResponse;
import com.github.ferigeek.sarv.dto.response.PostResponse;
import com.github.ferigeek.sarv.entity.Post;
import com.github.ferigeek.sarv.exception.UserNotFoundException;
import com.github.ferigeek.sarv.repository.PostRepository;
import com.github.ferigeek.sarv.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FeedService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final RecommendationClient recommendationClient;

    public FeedService(PostRepository postRepository, UserRepository userRepository, RecommendationClient recommendationClient) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.recommendationClient = recommendationClient;
    }

    @Transactional
    public Page<PostResponse> getChronological(Pageable pageable) {
        Page<Post> page = postRepository.findChronologicalFeed(pageable);
        recordViews(page.getContent().stream().map(Post::getId).toList());
        return page.map(this::withRecordedView);
    }

    @Transactional
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

            List<Post> visible = rankedIds.stream()
                    .map(map::get)
                    .filter(p -> p != null && p.getDeletedAt() == null)
                    .toList();
            recordViews(visible.stream().map(Post::getId).toList());

            List<PostResponse> content = visible.stream()
                    .map(this::withRecordedView)
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

    private void recordViews(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return;
        }
        postRepository.incrementViewCounts(postIds);
    }

    private PostResponse withRecordedView(Post post) {
        PostResponse response = new PostResponse(post);
        response.setViewCount((response.getViewCount() == null ? 0L : response.getViewCount()) + 1);
        return response;
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
                .filter(Objects::nonNull)
                .toList();
    }
}
