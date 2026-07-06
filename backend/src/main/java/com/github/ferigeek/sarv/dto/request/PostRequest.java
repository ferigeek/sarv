package com.github.ferigeek.sarv.dto.request;

import com.github.ferigeek.sarv.entity.type.PostCategory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostRequest {

    private PostCategory postCategory;
    private String content;
    private Long mediaId;
    private Long parentId;
    private Long repostOfId;
}
