package com.github.ferigeek.sarv.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ReactionRequest {

    @NotNull
    private Short reactionType;
}
