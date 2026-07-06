package com.github.ferigeek.sarv.dto.response;

import com.github.ferigeek.sarv.entity.Post;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
public class PostResponse {

    private Long id;
    private Long userId;
    private String postCategory;
    private String content;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long mediaId;
    private Long repostOfId;
    private Long parentId;

    public PostResponse(Post post) {
        this.id = post.getId();
        this.userId = post.getId();
        this.postCategory = post.getPostCategory().name();
        this.content = (post.getContent() != null) ? post.getContent() : "";
        this.createdAt = post.getCreatedAt();
        this.updatedAt = (post.getUpdatedAt() != null) ? post.getUpdatedAt() : null;
        this.mediaId = (post.getMedia() != null) ? post.getMedia().getId() : null;
        this.repostOfId = (post.getRepostOf() != null) ? post.getRepostOf().getId() : null;
        this.parentId = (post.getParent() != null) ? post.getParent().getId() : null;
    }
}
