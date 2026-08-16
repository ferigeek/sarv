package com.github.ferigeek.sarv.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserLoginRequest {

    @NotBlank
    @Size(min = 2)
    private String username;

    @NotBlank
    @Size(min = 8, max = 50)
    private String password;
}
