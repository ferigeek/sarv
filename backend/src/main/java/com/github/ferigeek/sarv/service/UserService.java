package com.github.ferigeek.sarv.service;

import com.github.ferigeek.sarv.dto.request.UserUpdateRequest;
import com.github.ferigeek.sarv.dto.response.UserResponse;
import com.github.ferigeek.sarv.dto.response.UserSummaryResponse;
import com.github.ferigeek.sarv.entity.Media;
import com.github.ferigeek.sarv.entity.User;
import com.github.ferigeek.sarv.exception.MediaNotFoundException;
import com.github.ferigeek.sarv.exception.UserNotFoundException;
import com.github.ferigeek.sarv.repository.MediaRepository;
import com.github.ferigeek.sarv.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

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
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: <%d>".formatted(id)));
        return new UserResponse(user);
    }

    public UserResponse getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found with username: <%s>".formatted(username)));
        return new UserResponse(user);
    }

    public UserResponse updateUser(String username, UserUpdateRequest userUpdateRequest) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found with Username: <%s>".formatted(username)));

        if (userUpdateRequest.getDisplayName() == null || userUpdateRequest.getDisplayName().isBlank()) {
            throw new IllegalArgumentException("Display name can not be empty");
        }
        user.setDisplayName(userUpdateRequest.getDisplayName());

        // If bio and location are given as null or empty string, they will be deleted.

        if (userUpdateRequest.getBio() == null || userUpdateRequest.getBio().isBlank()) {
            user.setBio(null);
        } else {
            user.setBio(userUpdateRequest.getBio().trim());
        }

        if (userUpdateRequest.getLocation() == null || userUpdateRequest.getLocation().isBlank()) {
            user.setLocation(null);
        } else {
            user.setLocation(userUpdateRequest.getLocation().trim());
        }

        // Gender can not be null, instead it can be `RATHER_NOT_TO_SAY`.
        if (userUpdateRequest.getGender() == null) {
            throw new IllegalArgumentException("Gender can not be empty");
        }
        user.setGender(userUpdateRequest.getGender());


        if (userUpdateRequest.getProfilePictureId() == null) {
            user.setProfilePicture(null);
        } else {
            Media picture = mediaRepository.findById(userUpdateRequest.getProfilePictureId())
                    .orElseThrow(() -> new MediaNotFoundException(userUpdateRequest.getProfilePictureId()));
            user.setProfilePicture(picture);
        }

        return new UserResponse(userRepository.save(user));
    }

    public List<UserSummaryResponse> searchUsers(String query) {
        return userRepository
                .searchUsers(query)
                .stream()
                .map(UserSummaryResponse::new)
                .toList();
    }
}
