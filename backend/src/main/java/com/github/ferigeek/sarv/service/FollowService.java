package com.github.ferigeek.sarv.service;

import com.github.ferigeek.sarv.dto.response.UserSummaryResponse;
import com.github.ferigeek.sarv.entity.Follow;
import com.github.ferigeek.sarv.entity.User;
import com.github.ferigeek.sarv.exception.FollowException;
import com.github.ferigeek.sarv.exception.UserNotFoundException;
import com.github.ferigeek.sarv.repository.FollowRepository;
import com.github.ferigeek.sarv.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
@Slf4j
public class FollowService {

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final EventLogService eventLogService;

    @Autowired
    public FollowService(UserRepository userRepository, FollowRepository followRepository, EventLogService eventLogService) {
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.eventLogService = eventLogService;
    }

    public Page<UserSummaryResponse> getFollowers(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: %d".formatted(userId)));
        return followRepository.findByFollowed(user, pageable)
                .map(Follow::getFollower)
                .map(UserSummaryResponse::new);
    }

    public Page<UserSummaryResponse> getFollowing(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: %d".formatted(userId)));
        return followRepository.findByFollower(user, pageable)
                .map(Follow::getFollowed)
                .map(UserSummaryResponse::new);
    }

    public void followUser(String username, Long userId) {
        User follower = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(
                        "Follower user not found with username: %s".formatted(username))
                );
        User followed = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        "Followed user not found with ID: %d".formatted(userId))
                );

        if (follower.getId().equals(followed.getId())) {
            log.warn("Rejected self-follow for user ID={}", userId);
            throw new IllegalArgumentException("User cannot follow themselves ID: %d".formatted(userId));
        }

        Follow follow = new Follow();
        follow.setFollower(follower);
        follow.setFollowed(followed);
        follow.setCreatedAt(OffsetDateTime.now());
        followRepository.save(follow);

        log.info("User with ID={} followed user with ID={}", follower.getId(), followed.getId());
        logFollowSafely(follower, followed, false);
    }

    public void unfollowUser(String username, Long userId) {
        User follower = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(
                        "Follower user not found with username: %s".formatted(username))
                );
        User followed = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        "Followed user not found with ID: %d".formatted(userId))
                );

        if (follower.getId().equals(followed.getId())) {
            log.warn("Rejected self-unfollow for user ID={}", userId);
            throw new IllegalArgumentException("User cannot unfollow themselves ID: %d".formatted(userId));
        }

        Follow follow = followRepository.findByFollowerAndFollowed(follower, followed)
                .orElseThrow(() -> new FollowException(
                        "A follow from user with ID: %d, following user with ID: %d, doesn't exist"
                                .formatted(follower.getId(), followed.getId()))
                );
        followRepository.delete(follow);

        log.info("User with ID={} unfollowed user with ID={}", follower.getId(), followed.getId());
        logFollowSafely(follower, followed, true);
    }

    private void logFollowSafely(User follower, User followed, boolean isUnfollow) {
        try {
            eventLogService.logFollow(follower, followed, isUnfollow);
        } catch (Exception e) {
            log.warn("Failed to log follow event followerId={} followedId={}", follower.getId(), followed.getId(), e);
        }
    }
}
