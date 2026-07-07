package com.github.ferigeek.sarv.dto.request;

import com.github.ferigeek.sarv.entity.type.Gender;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequest {

    private String displayName;
    private String bio;
    private String location;
    private Long profilePictureId;
    private Gender gender;
}
