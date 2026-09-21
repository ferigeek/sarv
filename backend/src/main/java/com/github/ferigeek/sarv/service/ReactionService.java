package com.github.ferigeek.sarv.service;

import com.github.ferigeek.sarv.dto.request.ReactionRequest;
import com.github.ferigeek.sarv.dto.response.ReactionResponse;
import com.github.ferigeek.sarv.entity.Post;
import com.github.ferigeek.sarv.entity.Reaction;
import com.github.ferigeek.sarv.entity.User;
import com.github.ferigeek.sarv.exception.PostNotFoundException;
import com.github.ferigeek.sarv.exception.UserNotFoundException;
import com.github.ferigeek.sarv.repository.PostRepository;
import com.github.ferigeek.sarv.repository.ReactionRepository;
import com.github.ferigeek.sarv.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@Slf4j
public class ReactionService {

    private final ReactionRepository reactionRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final EventLogService eventLogService;

    @Autowired
    public ReactionService(
            ReactionRepository reactionRepository,
            PostRepository postRepository,
            UserRepository userRepository,
            EventLogService eventLogService) {
        this.reactionRepository = reactionRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.eventLogService = eventLogService;
    }

    @Transactional
    public ReactionResponse addReaction(Long postId, ReactionRequest reactionRequest, String username) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(
                        "User not found with username: %s".formatted(username))
                );

        Reaction existing = reactionRepository.findByPostAndUser(post, user).orElse(null);

        if (existing != null) {
            if (existing.getReactionType().equals(reactionRequest.getReactionType())) {
                log.debug("User ID={} re-reacted same type={} to post ID={}", user.getId(), existing.getReactionType(), postId);
                return new ReactionResponse(post.getLikeCount(), post.getDislikeCount(), existing.getReactionType());
            } else {
                short oldType = existing.getReactionType();
                existing.setReactionType(reactionRequest.getReactionType());
                reactionRepository.save(existing);
                adjustCount(post, oldType, reactionRequest.getReactionType());
                log.info("User ID={} changed reaction from {} to {} on post ID={}", user.getId(), oldType, reactionRequest.getReactionType(), postId);
                logReactionSafely(username, post, reactionRequest.getReactionType());
                return new ReactionResponse(
                        post.getLikeCount(),
                        post.getDislikeCount(),
                        reactionRequest.getReactionType()
                );
            }
        }

        Reaction reaction = new Reaction();
        reaction.setPost(post);
        reaction.setUser(user);
        reaction.setReactionType(reactionRequest.getReactionType());
        reaction.setCreatedAt(OffsetDateTime.now());
        reactionRepository.save(reaction);
        incrementCount(post, reactionRequest.getReactionType());

        log.info("User ID={} reacted type={} to post ID={}", user.getId(), reaction.getReactionType(), postId);
        logReactionSafely(username, post, reactionRequest.getReactionType());

        return new ReactionResponse(post.getLikeCount(), post.getDislikeCount(), reactionRequest.getReactionType());
    }

    @Transactional
    public void removeReaction(Long postId, String username) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(
                        "User not found with username: %s".formatted(username))
                );

        Reaction existing = reactionRepository.findByPostAndUser(post, user).orElse(null);
        if (existing != null) {
            reactionRepository.delete(existing);
            decrementCount(post, existing.getReactionType());
            log.info("User ID={} removed reaction from post ID={}", user.getId(), postId);
        } else {
            log.debug("User ID={} had no reaction to remove from post ID={}", user.getId(), postId);
        }
    }

    public ReactionResponse getReactionCounts(Long postId, String username) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(
                        "User not found with username: %s".formatted(username))
                );

        Short userReaction = 0;
        Reaction r = reactionRepository.findByPostAndUser(post, user).orElse(null);
        if (r != null) {
            userReaction = r.getReactionType();
        }

        return new ReactionResponse(post.getLikeCount(), post.getDislikeCount(), userReaction);
    }

    private void incrementCount(Post post, short reactionType) {
        if (reactionType == Reaction.LIKE) {
            post.setLikeCount((post.getLikeCount() == null ? 0L : post.getLikeCount()) + 1);
        } else if (reactionType == Reaction.DISLIKE) {
            post.setDislikeCount((post.getDislikeCount() == null ? 0L : post.getDislikeCount()) + 1);
        }
        postRepository.save(post);
    }

    private void decrementCount(Post post, short reactionType) {
        if (reactionType == Reaction.LIKE) {
            post.setLikeCount((post.getLikeCount() == null ? 0L : post.getLikeCount()) - 1);
        } else if (reactionType == Reaction.DISLIKE) {
            post.setDislikeCount((post.getDislikeCount() == null ? 0L : post.getDislikeCount()) - 1);
        }
        postRepository.save(post);
    }

    private void adjustCount(Post post, short oldType, short newType) {
        if (oldType == Reaction.LIKE) {
            post.setLikeCount((post.getLikeCount() == null ? 0L : post.getLikeCount()) - 1);
        } else if (oldType == Reaction.DISLIKE) {
            post.setDislikeCount((post.getDislikeCount() == null ? 0L : post.getDislikeCount()) - 1);
        }
        if (newType == Reaction.LIKE) {
            post.setLikeCount((post.getLikeCount() == null ? 0L : post.getLikeCount()) + 1);
        } else if (newType == Reaction.DISLIKE) {
            post.setDislikeCount((post.getDislikeCount() == null ? 0L : post.getDislikeCount()) + 1);
        }
        postRepository.save(post);
    }

    private void logReactionSafely(String username, Post post, short reactionType) {
        if (username == null) {
            return;
        }
        try {
            eventLogService.logReaction(username, post, reactionType);
        } catch (Exception e) {
            log.warn("Failed to log reaction event postId={} username={}", post.getId(), username, e);
        }
    }
}
