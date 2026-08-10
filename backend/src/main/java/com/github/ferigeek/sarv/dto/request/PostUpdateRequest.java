package com.github.ferigeek.sarv.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostUpdateRequest {

    @Size(min = 2, max = 280)
    private String content;

    @Positive
    private Long mediaId;
}
