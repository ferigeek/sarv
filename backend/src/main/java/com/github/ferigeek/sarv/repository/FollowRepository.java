package com.github.ferigeek.sarv.repository;

import com.github.ferigeek.sarv.entity.Follow;
import com.github.ferigeek.sarv.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    Page<Follow> findByFollowed(User user, Pageable pageable);
    Page<Follow> findByFollower(User user, Pageable pageable);
    Optional<Follow> findByFollowerAndFollowed(User follower, User followed);

    long countByFollowed(User followed);
    long countByFollower(User follower);
}
