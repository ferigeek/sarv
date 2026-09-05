package com.github.ferigeek.sarv.repository;

import com.github.ferigeek.sarv.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    @Query("""
        SELECT post
        FROM Post post
        WHERE post.parent.id = :postId
        AND post.deletedAt IS NULL
    """)
    Page<Post> findCommentsByParentId(@Param("postId") Long postId, Pageable pageable);

    @Modifying
    @Query("""
        UPDATE Post post
        SET post.commentCount = COALESCE(post.commentCount, 0) + 1
        WHERE post.id = :postId
    """)
    void incrementCommentCount(@Param("postId") Long postId);

    @Modifying
    @Query("""
        UPDATE Post post
        SET post.viewCount = COALESCE(post.viewCount, 0) + 1
        WHERE post.id IN :postIds
    """)
    void incrementViewCounts(@Param("postIds") List<Long> postIds);

    @Query("""
        SELECT reaction.post
        FROM Reaction reaction
        WHERE reaction.user.id = :userId
        AND (:reactionType IS NULL OR reaction.reactionType = :reactionType)
        AND reaction.post.deletedAt IS NULL
        ORDER BY reaction.createdAt DESC
    """)
    Page<Post> findReactedPosts(
            @Param("userId") Long userId,
            @Param("reactionType") Short reactionType,
            Pageable pageable);
}
