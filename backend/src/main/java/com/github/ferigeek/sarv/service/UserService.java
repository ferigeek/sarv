package com.github.ferigeek.sarv.service;

import com.github.ferigeek.sarv.dto.request.UserUpdateRequest;
import com.github.ferigeek.sarv.dto.response.UserResponse;
import com.github.ferigeek.sarv.entity.Media;
import com.github.ferigeek.sarv.entity.User;
import com.github.ferigeek.sarv.repository.MediaRepository;
import com.github.ferigeek.sarv.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final MediaRepository mediaRepository;

    @Autowired
    public UserService(UserRepository userRepository, MediaRepository mediaRepository) {
        this.userRepository = userRepository;
        this.mediaRepository = mediaRepository;
    }

    public UserResponse getUser(Long id) {
        User user = userRepository.findById(id).orElse(null);
        return new UserResponse(user);
    }

    public UserResponse getUserByUsername(String username) {
        User user = userRepository.findByUsername(username);
        return new UserResponse(user);
    }

    public UserResponse updateUser(String username, UserUpdateRequest userUpdateRequest) {
        User user = userRepository.findByUsername(username);

        user.setDisplayName(userUpdateRequest.getDisplayName());
        user.setBio(userUpdateRequest.getBio());
        user.setGender(userUpdateRequest.getGender());
        user.setLocation(userUpdateRequest.getLocation());
        if (userUpdateRequest.getProfilePictureId() != null) {
            if (user.getProfilePicture().getId().equals(userUpdateRequest.getProfilePictureId())) {
                Media newProfilePicture = mediaRepository.findById(userUpdateRequest.getProfilePictureId()).orElse(null);
                user.setProfilePicture(newProfilePicture);
            }
        }
        userRepository.save(user);
        return new UserResponse(user);
    }
}
