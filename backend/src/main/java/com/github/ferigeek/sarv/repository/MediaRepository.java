package com.github.ferigeek.sarv.repository;

import com.github.ferigeek.sarv.entity.Media;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MediaRepository extends JpaRepository<Media, Long> {

    Optional<Media> findBySha256(String sha256);
}
