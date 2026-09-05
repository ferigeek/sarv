package com.github.ferigeek.sarv.repository;

import com.github.ferigeek.sarv.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    @Query("""
        SELECT post
        FROM Post post
        WHERE post.deletedAt IS NULL
        ORDER BY post.createdAt DESC
    """)
    Page<Post> findChronologicalFeed(Pageable pageable);

    @Query("""
        SELECT post
        FROM Post post
        WHERE post.id IN :ids
          AND post.deletedAt IS NULL
    """)
    List<Post> findAllByIdsFiltered(@Param("ids") List<Long> ids);

    @Query("""
        SELECT post
        FROM Post post
        WHERE post.user.id = :userId
        AND post.deletedAt IS NULL
    """)
    Page<Post> findPostsByUserId(@Param("userId") Long userId, Pageable pageable);
}
