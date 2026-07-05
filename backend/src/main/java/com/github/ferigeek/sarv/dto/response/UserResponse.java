package com.github.ferigeek.sarv.dto.response;

import com.github.ferigeek.sarv.entity.User;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserResponse {
    private Long id;
    private String username;
    private String displayName;
    private String bio;
    private String gender;
    private String location;
    private Long profilePictureId;

    public UserResponse(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.displayName = user.getDisplayName();
        this.bio = user.getBio();
        this.gender = user.getGender().name();
        this.location = user.getLocation();
        if (user.getProfilePicture() != null) {
            this.profilePictureId = user.getProfilePicture().getId();
        }
    }
}
