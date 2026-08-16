package com.github.ferigeek.sarv.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ReactionRequest {

    @NotNull
    @Size(min = -1, max = 1)
    private short reactionType;
}
