package com.github.ferigeek.sarv.dto.request;

import com.github.ferigeek.sarv.entity.type.Gender;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserRegisterRequest {

    private String username;
    private String password;
    private String email;
    private String displayName;
    private Gender gender;
}
