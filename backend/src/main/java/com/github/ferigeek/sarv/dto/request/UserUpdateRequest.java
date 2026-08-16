package com.github.ferigeek.sarv.dto.request;

import com.github.ferigeek.sarv.entity.type.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequest {

    @NotBlank
    @Size(min = 2)
    private String displayName;

    @Size(min = 2, max = 255)
    private String bio;

    @Size(min = 2, max = 30)
    private String location;

    @Positive
    private Long profilePictureId;

    @NotNull
    private Gender gender;
}
