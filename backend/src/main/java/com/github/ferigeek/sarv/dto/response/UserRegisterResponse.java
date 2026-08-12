package com.github.ferigeek.sarv.dto.response;

import com.github.ferigeek.sarv.entity.User;
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

    public UserRegisterResponse(User user, String token) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.displayName = user.getDisplayName();
        this.email = user.getEmail();
        this.token = token;
    }
}
