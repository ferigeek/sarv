package com.github.ferigeek.sarv.service;

import com.github.ferigeek.sarv.dto.response.UserSummaryResponse;
import com.github.ferigeek.sarv.entity.Follow;
import com.github.ferigeek.sarv.entity.User;
import com.github.ferigeek.sarv.exception.FollowException;
import com.github.ferigeek.sarv.exception.UserNotFoundException;
import com.github.ferigeek.sarv.repository.FollowRepository;
import com.github.ferigeek.sarv.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class FollowService {

    private final UserRepository userRepository;
    private final FollowRepository followRepository;

    @Autowired
    public FollowService(UserRepository userRepository, FollowRepository followRepository) {
        this.userRepository = userRepository;
        this.followRepository = followRepository;
    }

    public List<UserSummaryResponse> getFollowers(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        List<Follow> follows = followRepository.findByFollowed(user);

        return follows.stream()
                .map(Follow::getFollower)
                .map(UserSummaryResponse::new)
                .toList();
    }

    public List<UserSummaryResponse> getFollowings(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        List<Follow> follows = followRepository.findByFollower(user);

        return follows.stream()
                .map(Follow::getFollowed)
                .map(UserSummaryResponse::new)
                .toList();
    }

    public void followUser(String username, Long userId) {
        User follower = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(
                        "Follower user not found with username: <%s>".formatted(username))
                );
        User followed = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        "Followed user not found with ID: <%d>".formatted(userId))
                );

        Follow follow = new Follow();
        follow.setFollower(follower);
        follow.setFollowed(followed);
        follow.setCreatedAt(OffsetDateTime.now());
        followRepository.save(follow);
    }

    public void unfollowUser(String username, Long userId) {
        User follower = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(
                        "Follower user not found with username: <%s>".formatted(username))
                );
        User followed = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        "Followed user not found with ID: <%d>".formatted(userId))
                );

        Follow follow = followRepository.findByFollowerAndFollowed(follower, followed)
                .orElseThrow(() -> new FollowException(
                        "A follow from user with ID: <%d>, following user with ID: <%d>, doesn't exist"
                                .formatted(follower.getId(), followed.getId()))
                );
        followRepository.delete(follow);
    }
}
