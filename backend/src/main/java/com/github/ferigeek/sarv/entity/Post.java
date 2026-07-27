package com.github.ferigeek.sarv.entity;

import com.github.ferigeek.sarv.entity.type.PostCategory;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "posts")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private PostCategory postCategory;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id")
    private Media media;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Post parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repost_of_id")
    private Post repostOf;

    @Column(name = "view_count", nullable = false)
    private Long viewCount = 0L;

    @Column(name = "like_count", nullable = false)
    private Long likeCount = 0L;

    @Column(name = "dislike_count", nullable = false)
    private Long dislikeCount = 0L;
}
