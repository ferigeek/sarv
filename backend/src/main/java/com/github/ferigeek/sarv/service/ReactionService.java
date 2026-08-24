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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class ReactionService {

    private final ReactionRepository reactionRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @Autowired
    public ReactionService(
            ReactionRepository reactionRepository,
            PostRepository postRepository,
            UserRepository userRepository) {
        this.reactionRepository = reactionRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
    }

    public ReactionResponse addReaction(Long postId, ReactionRequest reactionRequest, String username) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(
                        "User not found with username: <%s>".formatted(username))
                );

        Reaction existing = reactionRepository.findByPostAndUser(post, user).orElse(null);

        if (existing != null) {
            if (existing.getReactionType().equals(reactionRequest.getReactionType())) {
                return new ReactionResponse(post.getLikeCount(), post.getDislikeCount(), existing.getReactionType());
            } else {
                short oldType = existing.getReactionType();
                existing.setReactionType(reactionRequest.getReactionType());
                reactionRepository.save(existing);
                adjustCount(post, oldType, reactionRequest.getReactionType());
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
        return new ReactionResponse(post.getLikeCount(), post.getDislikeCount(), reactionRequest.getReactionType());
    }

    public void removeReaction(Long postId, String username) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(
                        "User not found with username: <%s>".formatted(username))
                );

        Reaction existing = reactionRepository.findByPostAndUser(post, user).orElse(null);
        if (existing != null) {
            reactionRepository.delete(existing);
            decrementCount(post, existing.getReactionType());
        }
    }

    public ReactionResponse getReactionCounts(Long postId, String username) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(
                        "User not found with username: <%s>".formatted(username))
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
            post.setLikeCount(post.getLikeCount() + 1);
        } else if (reactionType == Reaction.DISLIKE) {
            post.setDislikeCount(post.getDislikeCount() + 1);
        }
        postRepository.save(post);
    }

    private void decrementCount(Post post, short reactionType) {
        if (reactionType == Reaction.LIKE) {
            post.setLikeCount(post.getLikeCount() - 1);
        } else if (reactionType == Reaction.DISLIKE) {
            post.setDislikeCount(post.getDislikeCount() - 1);
        }
        postRepository.save(post);
    }

    private void adjustCount(Post post, short oldType, short newType) {
        if (oldType == Reaction.LIKE) {
            post.setLikeCount(post.getLikeCount() - 1);
        } else if (oldType == Reaction.DISLIKE) {
            post.setDislikeCount(post.getDislikeCount() - 1);
        }
        if (newType == Reaction.LIKE) {
            post.setLikeCount(post.getLikeCount() + 1);
        } else if (newType == Reaction.DISLIKE) {
            post.setDislikeCount(post.getDislikeCount() + 1);
        }
        postRepository.save(post);
    }
}
