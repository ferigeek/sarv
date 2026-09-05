package com.github.ferigeek.sarv.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserStatsResponse {

    private Long userId;
    private long followerCount;
    private long followingCount;

    public UserStatsResponse(Long userId, long followerCount, long followingCount) {
        this.userId = userId;
        this.followerCount = followerCount;
        this.followingCount = followingCount;
    }
}
