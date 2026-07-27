package com.github.ferigeek.sarv.repository;

import com.github.ferigeek.sarv.entity.Post;
import com.github.ferigeek.sarv.entity.Reaction;
import com.github.ferigeek.sarv.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {

    Optional<Reaction> findByPostAndUser(Post post, User user);
}
