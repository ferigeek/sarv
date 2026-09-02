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
    java.util.List<Post> findAllByIdsFiltered(@org.springframework.data.repository.query.Param("ids") java.util.List<Long> ids);
}
