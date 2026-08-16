package com.github.ferigeek.sarv.repository;

import com.github.ferigeek.sarv.entity.Follow;
import com.github.ferigeek.sarv.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    List<Follow> findByFollowed(User user);
    List<Follow> findByFollower(User user);
    Optional<Follow> findByFollowerAndFollowed(User follower, User followed);
}
