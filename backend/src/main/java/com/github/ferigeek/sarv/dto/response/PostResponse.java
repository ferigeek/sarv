package com.github.ferigeek.sarv.dto.response;

import com.github.ferigeek.sarv.entity.Post;
import com.github.ferigeek.sarv.entity.type.PostCategory;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
public class PostResponse {

    private Long id;
    private Long userId;
    private PostCategory postCategory;
    private String content;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long mediaId;
    private Long repostOfId;
    private Long parentId;
    private Long viewCount;
    private Long likeCount;
    private Long dislikeCount;
    private Long commentCount;

    public PostResponse(Post post) {
        this.id = post.getId();
        this.userId = post.getUser().getId();
        this.postCategory = post.getPostCategory();
        this.content = post.getContent();
        this.createdAt = post.getCreatedAt();
        this.updatedAt = post.getUpdatedAt();
        this.mediaId = (post.getMedia() != null) ? post.getMedia().getId() : null;
        this.repostOfId = (post.getRepostOf() != null) ? post.getRepostOf().getId() : null;
        this.parentId = (post.getParent() != null) ? post.getParent().getId() : null;
        this.viewCount = post.getViewCount();
        this.likeCount = post.getLikeCount();
        this.dislikeCount = post.getDislikeCount();
        this.commentCount = post.getCommentCount();
    }
}
