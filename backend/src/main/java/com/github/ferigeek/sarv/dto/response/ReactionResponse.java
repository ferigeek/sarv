package com.github.ferigeek.sarv.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ReactionResponse {

    private Long likeCount;
    private Long dislikeCount;
    private Short userReaction;

    public ReactionResponse(Long likeCount, Long dislikeCount, Short userReaction) {
        this.likeCount = likeCount;
        this.dislikeCount = dislikeCount;
        this.userReaction = userReaction;
    }
}