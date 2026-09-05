package com.github.ferigeek.sarv.dto.request;

import com.github.ferigeek.sarv.entity.type.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserRegisterRequest {

    @NotBlank
    @Size(min = 2)
    private String username;

    @NotBlank
    @Size(min = 8, max = 50)
    private String password;

    @NotBlank
    @Size(min = 8, max = 50)
    private String confirmPassword;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 2)
    private String displayName;

    @NotNull
    private Gender gender;
}
