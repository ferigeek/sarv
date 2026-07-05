package com.github.ferigeek.sarv.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserRegisterResponse {

    private Long id;
    private String username;
    private String displayName;
    private String email;
    private String token;
}
