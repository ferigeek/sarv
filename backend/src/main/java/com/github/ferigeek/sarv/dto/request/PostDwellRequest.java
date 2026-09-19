package com.github.ferigeek.sarv.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostDwellRequest {

    @NotNull
    @Positive
    @Max(1_800_000)
    private Long durationMs;

    private UUID sessionId;

    private DwellSource source;
}
