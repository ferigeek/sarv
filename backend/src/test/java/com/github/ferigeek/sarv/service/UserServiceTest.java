package com.github.ferigeek.sarv.service;

import com.github.ferigeek.sarv.dto.request.UserUpdateRequest;
import com.github.ferigeek.sarv.dto.response.UserResponse;
import com.github.ferigeek.sarv.dto.response.UserStatsResponse;
import com.github.ferigeek.sarv.dto.response.UserSummaryResponse;
import com.github.ferigeek.sarv.entity.Media;
import com.github.ferigeek.sarv.entity.User;
import com.github.ferigeek.sarv.entity.type.Gender;
import com.github.ferigeek.sarv.entity.type.UserStatus;
import com.github.ferigeek.sarv.exception.MediaNotFoundException;
import com.github.ferigeek.sarv.exception.UserNotFoundException;
import com.github.ferigeek.sarv.repository.FollowRepository;
import com.github.ferigeek.sarv.repository.MediaRepository;
import com.github.ferigeek.sarv.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private FollowRepository followRepository;

    @InjectMocks
    private UserService userService;

    private User baseUser;
    private Media baseMedia;

    @BeforeEach
    void setUp() {
        baseMedia = new Media();
        baseMedia.setId(100L);
        baseMedia.setSize(1234L);
        baseMedia.setMimeType("image/png");
        baseMedia.setSha256("abc123sha256hashvalueabc123sha256hashvalueabc123sha256hashvalue12");
        baseMedia.setCreatedAt(OffsetDateTime.now());

        baseUser = new User();
        baseUser.setId(1L);
        baseUser.setUsername("ferigeek");
        baseUser.setDisplayName("Feri Geek");
        baseUser.setEmail("feri@example.com");
        baseUser.setPasswordHash("hashed");
        baseUser.setCreatedAt(OffsetDateTime.now());
        baseUser.setBio("original bio");
        baseUser.setGender(Gender.MALE);
        baseUser.setLocation("Tehran");
        baseUser.setStatus(UserStatus.ACTIVE);
        baseUser.setProfilePicture(baseMedia);
    }

    private User createUser(Long id, String username, String displayName, Gender gender, Media picture) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setDisplayName(displayName);
        u.setEmail(username + "@example.com");
        u.setPasswordHash("hash");
        u.setCreatedAt(OffsetDateTime.now());
        u.setGender(gender);
        u.setStatus(UserStatus.ACTIVE);
        u.setProfilePicture(picture);
        return u;
    }

    // -----------------------------------------------------------------------
    // getUser
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("getUser")
    class GetUser {

        @Test
        @DisplayName("should return UserResponse when user exists by id")
        void shouldReturnUserWhenExists() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(baseUser));

            UserResponse response = userService.getUser(1L);

            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getUsername()).isEqualTo("ferigeek");
            assertThat(response.getDisplayName()).isEqualTo("Feri Geek");
            assertThat(response.getBio()).isEqualTo("original bio");
            assertThat(response.getGender()).isEqualTo(Gender.MALE.name());
            assertThat(response.getLocation()).isEqualTo("Tehran");
            assertThat(response.getProfilePictureId()).isEqualTo(100L);
            assertThat(response.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("should map null profilePicture to null profilePictureId")
        void shouldMapNullProfilePicture() {
            baseUser.setProfilePicture(null);
            when(userRepository.findById(1L)).thenReturn(Optional.of(baseUser));

            UserResponse response = userService.getUser(1L);

            assertThat(response.getProfilePictureId()).isNull();
        }

        @Test
        @DisplayName("should map null bio and location correctly")
        void shouldMapNullBioAndLocation() {
            baseUser.setBio(null);
            baseUser.setLocation(null);
            when(userRepository.findById(1L)).thenReturn(Optional.of(baseUser));

            UserResponse response = userService.getUser(1L);

            assertThat(response.getBio()).isNull();
            assertThat(response.getLocation()).isNull();
        }

        @Test
        @DisplayName("should throw UserNotFoundException when user does not exist by id")
        void shouldThrowWhenNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            UserNotFoundException ex = assertThrows(UserNotFoundException.class,
                    () -> userService.getUser(99L));

            assertThat(ex.getMessage()).contains("99");
        }

        @Test
        @DisplayName("should include id in exception message")
        void shouldIncludeIdInMessage() {
            when(userRepository.findById(42L)).thenReturn(Optional.empty());

            UserNotFoundException ex = assertThrows(UserNotFoundException.class,
                    () -> userService.getUser(42L));

            assertThat(ex.getMessage()).isEqualTo("User not found with ID: <42>");
        }

        @Test
        @DisplayName("should delegate to repository with correct id")
        void shouldDelegateWithCorrectId() {
            when(userRepository.findById(5L)).thenReturn(Optional.of(baseUser));

            userService.getUser(5L);

            verify(userRepository).findById(5L);
            verifyNoInteractions(mediaRepository);
        }
    }

    // -----------------------------------------------------------------------
    // getUserByUsername
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("getUserByUsername")
    class GetUserByUsername {

        @Test
        @DisplayName("should return UserResponse when user exists by username")
        void shouldReturnWhenExists() {
            when(userRepository.findByUsername("ferigeek")).thenReturn(Optional.of(baseUser));

            UserResponse response = userService.getUserByUsername("ferigeek");

            assertThat(response.getUsername()).isEqualTo("ferigeek");
            assertThat(response.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should throw UserNotFoundException when username not found")
        void shouldThrowWhenNotFound() {
            when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

            UserNotFoundException ex = assertThrows(UserNotFoundException.class,
                    () -> userService.getUserByUsername("unknown"));

            assertThat(ex.getMessage()).contains("unknown");
        }

        @Test
        @DisplayName("should include username in exception message")
        void shouldIncludeUsernameInMessage() {
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            UserNotFoundException ex = assertThrows(UserNotFoundException.class,
                    () -> userService.getUserByUsername("ghost"));

            assertThat(ex.getMessage()).isEqualTo("User not found with username: <ghost>");
        }

        @Test
        @DisplayName("should map all fields correctly")
        void shouldMapAllFields() {
            baseUser.setBio("bio text");
            baseUser.setLocation("Berlin");
            baseUser.setGender(Gender.FEMALE);
            baseUser.setStatus(UserStatus.SUSPENDED);
            when(userRepository.findByUsername("ferigeek")).thenReturn(Optional.of(baseUser));

            UserResponse r = userService.getUserByUsername("ferigeek");

            assertThat(r.getBio()).isEqualTo("bio text");
            assertThat(r.getLocation()).isEqualTo("Berlin");
            assertThat(r.getGender()).isEqualTo("FEMALE");
            assertThat(r.getStatus()).isEqualTo(UserStatus.SUSPENDED);
            assertThat(r.getProfilePictureId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("should not interact with mediaRepository")
        void shouldNotInteractWithMediaRepository() {
            when(userRepository.findByUsername("ferigeek")).thenReturn(Optional.of(baseUser));

            userService.getUserByUsername("ferigeek");

            verifyNoInteractions(mediaRepository);
        }
    }

    // -----------------------------------------------------------------------
    // updateUser
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("updateUser")
    class UpdateUser {

        @Test
        @DisplayName("happy path: update all mutable fields with trimming and new picture")
        void happyPathFullUpdate() {
            UserUpdateRequest req = new UserUpdateRequest("New Name", "  new bio  ", "  Paris  ", 200L, Gender.FEMALE);
            Media newMedia = new Media();
            newMedia.setId(200L);

            when(userRepository.findByUsername("ferigeek")).thenReturn(Optional.of(baseUser));
            when(mediaRepository.findById(200L)).thenReturn(Optional.of(newMedia));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UserResponse response = userService.updateUser("ferigeek", req);

            assertThat(response.getDisplayName()).isEqualTo("New Name");
            assertThat(response.getBio()).isEqualTo("new bio");
            assertThat(response.getLocation()).isEqualTo("Paris");
            assertThat(response.getGender()).isEqualTo("FEMALE");
            assertThat(response.getProfilePictureId()).isEqualTo(200L);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            User saved = captor.getValue();
            assertThat(saved.getDisplayName()).isEqualTo("New Name");
            assertThat(saved.getBio()).isEqualTo("new bio");
            assertThat(saved.getLocation()).isEqualTo("Paris");
            assertThat(saved.getGender()).isEqualTo(Gender.FEMALE);
            assertThat(saved.getProfilePicture()).isEqualTo(newMedia);
        }

        @Test
        @DisplayName("should clear bio when null")
        void shouldClearBioWhenNull() {
            UserUpdateRequest req = new UserUpdateRequest("New Name", null, "Tehran", null, Gender.MALE);
            when(userRepository.findByUsername("ferigeek")).thenReturn(Optional.of(baseUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UserResponse res = userService.updateUser("ferigeek", req);

            assertThat(res.getBio()).isNull();
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getBio()).isNull();
        }

        @Test
        @DisplayName("should clear bio when blank")
        void shouldClearBioWhenBlank() {
            UserUpdateRequest req = new UserUpdateRequest("New Name", "   ", "Tehran", null, Gender.MALE);
            when(userRepository.findByUsername("ferigeek")).thenReturn(Optional.of(baseUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UserResponse res = userService.updateUser("ferigeek", req);

            assertThat(res.getBio()).isNull();
        }

        @Test
        @DisplayName("should clear bio when empty string")
        void shouldClearBioWhenEmpty() {
            UserUpdateRequest req = new UserUpdateRequest("New Name", "", "Tehran", null, Gender.MALE);
            when(userRepository.findByUsername("ferigeek")).thenReturn(Optional.of(baseUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            userService.updateUser("ferigeek", req);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getBio()).isNull();
        }

        @Test
        @DisplayName("should trim bio when non-blank with spaces")
        void shouldTrimBio() {
            UserUpdateRequest req = new UserUpdateRequest("New Name", "  hello world  ", "Tehran", null, Gender.MALE);
            when(userRepository.findByUsername("ferigeek")).thenReturn(Optional.of(baseUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            userService.updateUser("ferigeek", req);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getBio()).isEqualTo("hello world");
        }

        @Test
        @DisplayName("should clear location when null")
        void shouldClearLocationWhenNull() {
            UserUpdateRequest req = new UserUpdateRequest("New Name", "bio", null, null, Gender.MALE);
            when(userRepository.findByUsername("ferigeek")).thenReturn(Optional.of(baseUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UserResponse res = userService.updateUser("ferigeek", req);

            assertThat(res.getLocation()).isNull();
        }

        @Test
        @DisplayName("should clear location when blank")
        void shouldClearLocationWhenBlank() {
            UserUpdateRequest req = new UserUpdateRequest("New Name", "bio", "   ", null, Gender.MALE);
            when(userRepository.findByUsername("ferigeek")).thenReturn(Optional.of(baseUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            userService.updateUser("ferigeek", req);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getLocation()).isNull();
        }

        @Test
        @DisplayName("should trim location when non-blank")
        void shouldTrimLocation() {
            UserUpdateRequest req = new UserUpdateRequest("New Name", "bio", "  New York  ", null, Gender.MALE);
            when(userRepository.findByUsername("ferigeek")).thenReturn(Optional.of(baseUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            userService.updateUser("ferigeek", req);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getLocation()).isEqualTo("New York");
        }

        @Test
        @DisplayName("should clear profilePicture when null")
        void shouldClearProfilePictureWhenNull() {
            // baseUser initially has a picture
            assertThat(baseUser.getProfilePicture()).isNotNull();
            UserUpdateRequest req = new UserUpdateRequest("New Name", "bio", "loc", null, Gender.MALE);
            when(userRepository.findByUsername("ferigeek")).thenReturn(Optional.of(baseUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UserResponse res = userService.updateUser("ferigeek", req);

            assertThat(res.getProfilePictureId()).isNull();
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getProfilePicture()).isNull();
            verify(mediaRepository, never()).findById(any());
        }

        @Test
        @DisplayName("should throw UserNotFoundException when user not found")
        void shouldThrowWhenUserNotFound() {
            UserUpdateRequest req = new UserUpdateRequest("Name", "bio", "loc", null, Gender.MALE);
            when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());

            assertThrows(UserNotFoundException.class, () -> userService.updateUser("missing", req));

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when displayName is null")
        void shouldThrowWhenDisplayNameNull() {
            UserUpdateRequest req = new UserUpdateRequest(null, "bio", "loc", null, Gender.MALE);
            when(userRepository.findByUsername("ferigeek")).thenReturn(Optional.of(baseUser));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> userService.updateUser("ferigeek", req));

            assertThat(ex.getMessage()).contains("Display name");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when displayName is blank")
        void shouldThrowWhenDisplayNameBlank() {
            UserUpdateRequest req = new UserUpdateRequest("   ", "bio", "loc", null, Gender.MALE);
            when(userRepository.findByUsername("ferigeek")).thenReturn(Optional.of(baseUser));

            assertThrows(IllegalArgumentException.class, () -> userService.updateUser("ferigeek", req));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when displayName is empty")
        void shouldThrowWhenDisplayNameEmpty() {
            UserUpdateRequest req = new UserUpdateRequest("", "bio", "loc", null, Gender.MALE);
            when(userRepository.findByUsername("ferigeek")).thenReturn(Optional.of(baseUser));

            assertThrows(IllegalArgumentException.class, () -> userService.updateUser("ferigeek", req));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when gender is null")
        void shouldThrowWhenGenderNull() {
            UserUpdateRequest req = new UserUpdateRequest("Name", "bio", "loc", null, null);
            when(userRepository.findByUsername("ferigeek")).thenReturn(Optional.of(baseUser));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> userService.updateUser("ferigeek", req));

            assertThat(ex.getMessage()).contains("Gender");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw MediaNotFoundException when profilePictureId not found")
        void shouldThrowWhenMediaNotFound() {
            UserUpdateRequest req = new UserUpdateRequest("Name", "bio", "loc", 999L, Gender.MALE);
            when(userRepository.findByUsername("ferigeek")).thenReturn(Optional.of(baseUser));
            when(mediaRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(MediaNotFoundException.class, () -> userService.updateUser("ferigeek", req));

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should preserve immutable fields: username, email, id, status")
        void shouldPreserveImmutableFields() {
            UserUpdateRequest req = new UserUpdateRequest("New Name", "new bio", "new loc", null, Gender.FEMALE);
            when(userRepository.findByUsername("ferigeek")).thenReturn(Optional.of(baseUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            userService.updateUser("ferigeek", req);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            User saved = captor.getValue();
            assertThat(saved.getId()).isEqualTo(1L);
            assertThat(saved.getUsername()).isEqualTo("ferigeek");
            assertThat(saved.getEmail()).isEqualTo("feri@example.com");
            assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(saved.getPasswordHash()).isEqualTo("hashed");
        }

        @Test
        @DisplayName("should not call mediaRepository when picture id is null even if user has existing picture")
        void shouldNotCallMediaWhenClearingPicture() {
            UserUpdateRequest req = new UserUpdateRequest("Name", "bio", "loc", null, Gender.MALE);
            when(userRepository.findByUsername("ferigeek")).thenReturn(Optional.of(baseUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            userService.updateUser("ferigeek", req);

            verify(mediaRepository, never()).findById(any());
        }

        @Test
        @DisplayName("should handle RATHER_NOT_TO_SAY gender")
        void shouldHandleRatherNotToSay() {
            UserUpdateRequest req = new UserUpdateRequest("Name", "bio", "loc", null, Gender.RATHER_NOT_TO_SAY);
            when(userRepository.findByUsername("ferigeek")).thenReturn(Optional.of(baseUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UserResponse res = userService.updateUser("ferigeek", req);

            assertThat(res.getGender()).isEqualTo("RATHER_NOT_TO_SAY");
        }

        @Test
        @DisplayName("should handle bio with only spaces as null - edge trimming")
        void shouldHandleBioWithWhitespaceOnly() {
            UserUpdateRequest req = new UserUpdateRequest("Name", "\t\n ", "loc", null, Gender.MALE);
            when(userRepository.findByUsername("ferigeek")).thenReturn(Optional.of(baseUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            userService.updateUser("ferigeek", req);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getBio()).isNull();
        }

        @Test
        @DisplayName("should update displayName without trimming (behavior as implemented)")
        void shouldNotTrimDisplayName() {
            // displayName trimming is NOT done in service; verify current behavior
            UserUpdateRequest req = new UserUpdateRequest("  John  ", "bio", "loc", null, Gender.MALE);
            when(userRepository.findByUsername("ferigeek")).thenReturn(Optional.of(baseUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            userService.updateUser("ferigeek", req);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getDisplayName()).isEqualTo("  John  ");
        }

        @Test
        @DisplayName("should set profile picture correctly when found")
        void shouldSetProfilePictureWhenFound() {
            Media m = new Media();
            m.setId(55L);
            UserUpdateRequest req = new UserUpdateRequest("Name", "bio", "loc", 55L, Gender.MALE);
            when(userRepository.findByUsername("ferigeek")).thenReturn(Optional.of(baseUser));
            when(mediaRepository.findById(55L)).thenReturn(Optional.of(m));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UserResponse res = userService.updateUser("ferigeek", req);

            assertThat(res.getProfilePictureId()).isEqualTo(55L);
        }

        @Test
        @DisplayName("should throw MediaNotFoundException with correct id in message")
        void shouldThrowMediaNotFoundWithMessage() {
            UserUpdateRequest req = new UserUpdateRequest("Name", "bio", "loc", 123L, Gender.MALE);
            when(userRepository.findByUsername("ferigeek")).thenReturn(Optional.of(baseUser));
            when(mediaRepository.findById(123L)).thenReturn(Optional.empty());

            MediaNotFoundException ex = assertThrows(MediaNotFoundException.class,
                    () -> userService.updateUser("ferigeek", req));

            assertThat(ex.getMessage()).contains("123");
        }
    }

    // -----------------------------------------------------------------------
    // searchUsers
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("searchUsers")
    class SearchUsers {

        @Test
        @DisplayName("should return mapped UserSummaryResponse page")
        void shouldReturnMappedPage() {
            User u1 = createUser(1L, "alice", "Alice Wonderland", Gender.FEMALE, null);
            User u2 = createUser(2L, "bob", "Bob Builder", Gender.MALE, baseMedia);
            when(userRepository.searchUsers("ali", PageRequest.of(0, 20)))
                    .thenReturn(new PageImpl<>(List.of(u1, u2), PageRequest.of(0, 20), 2));

            Page<UserSummaryResponse> result = userService.searchUsers("ali", PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent().getFirst().getId()).isEqualTo(1L);
            assertThat(result.getContent().getFirst().getUsername()).isEqualTo("alice");
            assertThat(result.getContent().get(0).getDisplayName()).isEqualTo("Alice Wonderland");
            assertThat(result.getContent().get(0).getProfilePictureId()).isNull();
            assertThat(result.getContent().get(1).getProfilePictureId()).isEqualTo(100L);
            assertThat(result.getContent().get(1).getUsername()).isEqualTo("bob");
        }

        @Test
        @DisplayName("should return empty page when no users found")
        void shouldReturnEmptyWhenNone() {
            when(userRepository.searchUsers("nonexistent", PageRequest.of(0, 20)))
                    .thenReturn(Page.empty());

            Page<UserSummaryResponse> result = userService.searchUsers("nonexistent", PageRequest.of(0, 20));

            assertThat(result).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("should delegate query and pageable to repository")
        void shouldDelegateQueryAndPageable() {
            Pageable pageable = PageRequest.of(2, 5);
            when(userRepository.searchUsers("testQuery", pageable)).thenReturn(Page.empty());

            userService.searchUsers("testQuery", pageable);

            verify(userRepository).searchUsers("testQuery", pageable);
        }

        @Test
        @DisplayName("should map profilePicture correctly for each user")
        void shouldMapProfilePicturePerUser() {
            Media m1 = new Media();
            m1.setId(10L);
            Media m2 = new Media();
            m2.setId(20L);
            User u1 = createUser(1L, "u1", "U One", Gender.MALE, m1);
            User u2 = createUser(2L, "u2", "U Two", Gender.MALE, null);
            User u3 = createUser(3L, "u3", "U Three", Gender.MALE, m2);
            when(userRepository.searchUsers("u", Pageable.unpaged()))
                    .thenReturn(new PageImpl<>(List.of(u1, u2, u3)));

            Page<UserSummaryResponse> result = userService.searchUsers("u", Pageable.unpaged());

            assertThat(result.getContent()).extracting(UserSummaryResponse::getProfilePictureId)
                    .containsExactly(10L, null, 20L);
        }

        @Test
        @DisplayName("should handle single result")
        void shouldHandleSingleResult() {
            User u = createUser(5L, "single", "Single User", Gender.FEMALE, null);
            when(userRepository.searchUsers("single", PageRequest.of(0, 20)))
                    .thenReturn(new PageImpl<>(List.of(u)));

            Page<UserSummaryResponse> result = userService.searchUsers("single", PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().getFirst().getUsername()).isEqualTo("single");
        }

        @Test
        @DisplayName("should propagate query exactly as given (no trimming at service layer)")
        void shouldPropagateQueryExactly() {
            when(userRepository.searchUsers("  spaced  ", PageRequest.of(0, 20))).thenReturn(Page.empty());

            userService.searchUsers("  spaced  ", PageRequest.of(0, 20));

            verify(userRepository).searchUsers("  spaced  ", PageRequest.of(0, 20));
        }

        @Test
        @DisplayName("should return page that preserves repository order")
        void shouldPreserveOrder() {
            User u1 = createUser(3L, "charlie", "Charlie", Gender.MALE, null);
            User u2 = createUser(1L, "alice", "Alice", Gender.FEMALE, null);
            User u3 = createUser(2L, "bob", "Bob", Gender.MALE, null);
            when(userRepository.searchUsers("a", PageRequest.of(0, 20)))
                    .thenReturn(new PageImpl<>(List.of(u1, u2, u3)));

            Page<UserSummaryResponse> result = userService.searchUsers("a", PageRequest.of(0, 20));

            assertThat(result.getContent()).extracting(UserSummaryResponse::getId)
                    .containsExactly(3L, 1L, 2L);
        }

        @Test
        @DisplayName("should keep pagination metadata of the repository page")
        void shouldKeepPaginationMetadata() {
            User u = createUser(1L, "alice", "Alice", Gender.FEMALE, null);
            Page<User> page = new PageImpl<>(List.of(u), PageRequest.of(1, 10), 25);
            when(userRepository.searchUsers("a", PageRequest.of(1, 10))).thenReturn(page);

            Page<UserSummaryResponse> result = userService.searchUsers("a", PageRequest.of(1, 10));

            assertThat(result.getNumber()).isEqualTo(1);
            assertThat(result.getSize()).isEqualTo(10);
            assertThat(result.getTotalPages()).isEqualTo(3);
        }

        @Test
        @DisplayName("should not interact with mediaRepository")
        void shouldNotInteractWithMediaRepository() {
            when(userRepository.searchUsers("any", PageRequest.of(0, 20))).thenReturn(Page.empty());

            userService.searchUsers("any", PageRequest.of(0, 20));

            verifyNoInteractions(mediaRepository);
        }
    }

    // -----------------------------------------------------------------------
    // getUserStats
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("getUserStats")
    class GetUserStats {

        @Test
        @DisplayName("should return follower and following counts for existing user")
        void shouldReturnCounts() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(baseUser));
            when(followRepository.countByFollowed(baseUser)).thenReturn(7L);
            when(followRepository.countByFollower(baseUser)).thenReturn(3L);

            UserStatsResponse response = userService.getUserStats(1L);

            assertThat(response.getUserId()).isEqualTo(1L);
            assertThat(response.getFollowerCount()).isEqualTo(7L);
            assertThat(response.getFollowingCount()).isEqualTo(3L);
            verify(followRepository).countByFollowed(baseUser);
            verify(followRepository).countByFollower(baseUser);
        }

        @Test
        @DisplayName("should return zero counts when user has no follows")
        void shouldReturnZeroCounts() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(baseUser));
            when(followRepository.countByFollowed(baseUser)).thenReturn(0L);
            when(followRepository.countByFollower(baseUser)).thenReturn(0L);

            UserStatsResponse response = userService.getUserStats(1L);

            assertThat(response.getFollowerCount()).isZero();
            assertThat(response.getFollowingCount()).isZero();
        }

        @Test
        @DisplayName("should throw UserNotFoundException when user does not exist")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            UserNotFoundException ex = assertThrows(UserNotFoundException.class,
                    () -> userService.getUserStats(99L));

            assertThat(ex.getMessage()).contains("99");
            verifyNoInteractions(followRepository);
        }
    }
}
