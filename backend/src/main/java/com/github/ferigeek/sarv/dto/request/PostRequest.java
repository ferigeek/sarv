package com.github.ferigeek.sarv.dto.request;

import com.github.ferigeek.sarv.entity.type.PostCategory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostRequest {

    @NotNull
    private PostCategory postCategory;

    @Size(max=280)
    private String content;

    @Positive
    private Long mediaId;

    @Positive
    private Long parentId;

    @Positive
    private Long repostOfId;
}
