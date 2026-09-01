package com.github.ferigeek.sarv.service;

import com.github.ferigeek.sarv.dto.response.PostResponse;
import com.github.ferigeek.sarv.repository.PostRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class FeedService {

    private final PostRepository postRepository;

    public FeedService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    public Page<PostResponse> getChronological(Pageable pageable) {
        return postRepository.findChronologicalFeed(pageable)
                .map(PostResponse::new);
    }
}
