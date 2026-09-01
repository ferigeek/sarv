package com.github.ferigeek.sarv.repository;

import com.github.ferigeek.sarv.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PostRepository extends JpaRepository<Post, Long> {

    @Query("""
        SELECT post
        FROM Post post
        ORDER BY post.createdAt DESC
    """)
    Page<Post> findChronologicalFeed(Pageable pageable);
}
