package com.github.ferigeek.sarv.service;

import com.github.ferigeek.sarv.dto.response.UserSummaryResponse;
import com.github.ferigeek.sarv.entity.Follow;
import com.github.ferigeek.sarv.entity.Media;
import com.github.ferigeek.sarv.entity.User;
import com.github.ferigeek.sarv.exception.FollowException;
import com.github.ferigeek.sarv.exception.UserNotFoundException;
import com.github.ferigeek.sarv.repository.FollowRepository;
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
class FollowServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private FollowRepository followRepository;

    @InjectMocks
    private FollowService followService;

    private User alice;
    private User bob;
    private User charlie;
    private Media media;

    @BeforeEach
    void setUp() {
        media = new Media();
        media.setId(10L);
        media.setSize(100L);
        media.setMimeType("image/png");
        media.setSha256("abc");
        media.setCreatedAt(OffsetDateTime.now());

        alice = new User();
        alice.setId(1L);
        alice.setUsername("alice");
        alice.setDisplayName("Alice");
        alice.setEmail("alice@example.com");
        alice.setPasswordHash("hash");
        alice.setCreatedAt(OffsetDateTime.now());
        alice.setProfilePicture(media);

        bob = new User();
        bob.setId(2L);
        bob.setUsername("bob");
        bob.setDisplayName("Bob");
        bob.setEmail("bob@example.com");
        bob.setPasswordHash("hash");
        bob.setCreatedAt(OffsetDateTime.now());
        // bob no picture

        charlie = new User();
        charlie.setId(3L);
        charlie.setUsername("charlie");
        charlie.setDisplayName("Charlie");
        charlie.setEmail("charlie@example.com");
        charlie.setPasswordHash("hash");
        charlie.setCreatedAt(OffsetDateTime.now());
        charlie.setProfilePicture(media);
    }

    private Follow follow(User follower, User followed) {
        Follow f = new Follow();
        f.setId(100L);
        f.setFollower(follower);
        f.setFollowed(followed);
        f.setCreatedAt(OffsetDateTime.now());
        return f;
    }

    // -----------------------------------------------------------------------
    // getFollowers
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("getFollowers")
    class GetFollowers {

        @Test
        @DisplayName("should return mapped followers page")
        void shouldReturnMappedFollowers() {
            User target = bob; // bob is being followed
            Follow f1 = follow(alice, target);
            Follow f2 = follow(charlie, target);
            Pageable pageable = PageRequest.of(0, 20);
            when(userRepository.findById(2L)).thenReturn(Optional.of(target));
            when(followRepository.findByFollowed(target, pageable))
                    .thenReturn(new PageImpl<>(List.of(f1, f2), pageable, 2));

            Page<UserSummaryResponse> res = followService.getFollowers(2L, pageable);

            assertThat(res.getContent()).hasSize(2);
            assertThat(res.getTotalElements()).isEqualTo(2);
            assertThat(res.getContent().get(0).getId()).isEqualTo(1L);
            assertThat(res.getContent().get(0).getUsername()).isEqualTo("alice");
            assertThat(res.getContent().get(0).getDisplayName()).isEqualTo("Alice");
            assertThat(res.getContent().get(0).getProfilePictureId()).isEqualTo(10L);
            assertThat(res.getContent().get(1).getId()).isEqualTo(3L);
            assertThat(res.getContent().get(1).getUsername()).isEqualTo("charlie");
            assertThat(res.getContent().get(1).getProfilePictureId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("should map follower without profilePicture to null")
        void shouldMapNullPicture() {
            User target = bob;
            Follow f = follow(bob, target); // bob follower has no picture
            Pageable pageable = PageRequest.of(0, 20);
            when(userRepository.findById(1L)).thenReturn(Optional.of(target));
            when(followRepository.findByFollowed(target, pageable)).thenReturn(new PageImpl<>(List.of(f)));

            Page<UserSummaryResponse> res = followService.getFollowers(1L, pageable);

            assertThat(res.getContent()).hasSize(1);
            assertThat(res.getContent().get(0).getProfilePictureId()).isNull();
        }

        @Test
        @DisplayName("should return empty page when no followers")
        void shouldReturnEmptyWhenNoFollowers() {
            User target = alice;
            when(userRepository.findById(1L)).thenReturn(Optional.of(target));
            when(followRepository.findByFollowed(target, PageRequest.of(0, 20))).thenReturn(Page.empty());

            Page<UserSummaryResponse> res = followService.getFollowers(1L, PageRequest.of(0, 20));

            assertThat(res).isEmpty();
            assertThat(res.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("should throw UserNotFoundException when user not found")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            UserNotFoundException ex = assertThrows(UserNotFoundException.class,
                    () -> followService.getFollowers(99L, PageRequest.of(0, 20)));

            assertThat(ex.getMessage()).contains("99");
            assertThat(ex.getMessage()).isEqualTo("User not found with ID: <99>");
            verify(followRepository, never()).findByFollowed(any(), any());
        }

        @Test
        @DisplayName("should delegate to repositories with correct user and pageable")
        void shouldDelegateWithCorrectUser() {
            User target = alice;
            Pageable pageable = PageRequest.of(1, 5);
            when(userRepository.findById(1L)).thenReturn(Optional.of(target));
            when(followRepository.findByFollowed(target, pageable)).thenReturn(Page.empty());

            followService.getFollowers(1L, pageable);

            verify(userRepository).findById(1L);
            verify(followRepository).findByFollowed(target, pageable);
            verify(followRepository, never()).findByFollower(any(), any());
        }

        @Test
        @DisplayName("should preserve repository order")
        void shouldPreserveOrder() {
            User target = alice;
            Follow f1 = follow(charlie, target);
            Follow f2 = follow(alice, target);
            Follow f3 = follow(bob, target);
            Pageable pageable = PageRequest.of(0, 20);
            when(userRepository.findById(1L)).thenReturn(Optional.of(target));
            when(followRepository.findByFollowed(target, pageable))
                    .thenReturn(new PageImpl<>(List.of(f1, f2, f3)));

            Page<UserSummaryResponse> res = followService.getFollowers(1L, pageable);

            assertThat(res.getContent()).extracting(UserSummaryResponse::getId).containsExactly(3L, 1L, 2L);
        }

        @Test
        @DisplayName("should keep pagination metadata of the repository page")
        void shouldKeepPaginationMetadata() {
            User target = alice;
            Follow f = follow(bob, target);
            Pageable pageable = PageRequest.of(1, 10);
            when(userRepository.findById(1L)).thenReturn(Optional.of(target));
            when(followRepository.findByFollowed(target, pageable))
                    .thenReturn(new PageImpl<>(List.of(f), PageRequest.of(1, 10), 25));

            Page<UserSummaryResponse> res = followService.getFollowers(1L, pageable);

            assertThat(res.getNumber()).isEqualTo(1);
            assertThat(res.getSize()).isEqualTo(10);
            assertThat(res.getTotalElements()).isEqualTo(25);
            assertThat(res.getTotalPages()).isEqualTo(3);
        }
    }

    // -----------------------------------------------------------------------
    // getFollowing
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("getFollowing")
    class GetFollowing {

        @Test
        @DisplayName("should return mapped following page")
        void shouldReturnMappedFollowing() {
            User source = alice;
            Follow f1 = follow(source, bob);
            Follow f2 = follow(source, charlie);
            Pageable pageable = PageRequest.of(0, 20);
            when(userRepository.findById(1L)).thenReturn(Optional.of(source));
            when(followRepository.findByFollower(source, pageable))
                    .thenReturn(new PageImpl<>(List.of(f1, f2), pageable, 2));

            Page<UserSummaryResponse> res = followService.getFollowing(1L, pageable);

            assertThat(res.getContent()).hasSize(2);
            assertThat(res.getTotalElements()).isEqualTo(2);
            assertThat(res.getContent().get(0).getId()).isEqualTo(2L);
            assertThat(res.getContent().get(0).getUsername()).isEqualTo("bob");
            assertThat(res.getContent().get(0).getProfilePictureId()).isNull();
            assertThat(res.getContent().get(1).getId()).isEqualTo(3L);
            assertThat(res.getContent().get(1).getProfilePictureId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("should return empty page when not following anyone")
        void shouldReturnEmptyWhenNone() {
            User source = alice;
            when(userRepository.findById(1L)).thenReturn(Optional.of(source));
            when(followRepository.findByFollower(source, PageRequest.of(0, 20))).thenReturn(Page.empty());

            Page<UserSummaryResponse> res = followService.getFollowing(1L, PageRequest.of(0, 20));

            assertThat(res).isEmpty();
            assertThat(res.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("should throw UserNotFoundException when user not found")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(UserNotFoundException.class, () -> followService.getFollowing(99L, PageRequest.of(0, 20)));
            verify(followRepository, never()).findByFollower(any(), any());
        }

        @Test
        @DisplayName("should delegate with correct user and pageable")
        void shouldDelegate() {
            User source = bob;
            Pageable pageable = PageRequest.of(1, 5);
            when(userRepository.findById(2L)).thenReturn(Optional.of(source));
            when(followRepository.findByFollower(source, pageable)).thenReturn(Page.empty());

            followService.getFollowing(2L, pageable);

            verify(followRepository).findByFollower(source, pageable);
        }

        @Test
        @DisplayName("should keep pagination metadata of the repository page")
        void shouldKeepPaginationMetadata() {
            User source = alice;
            Follow f = follow(source, bob);
            Pageable pageable = PageRequest.of(2, 5);
            when(userRepository.findById(1L)).thenReturn(Optional.of(source));
            when(followRepository.findByFollower(source, pageable))
                    .thenReturn(new PageImpl<>(List.of(f), PageRequest.of(2, 5), 17));

            Page<UserSummaryResponse> res = followService.getFollowing(1L, pageable);

            assertThat(res.getNumber()).isEqualTo(2);
            assertThat(res.getSize()).isEqualTo(5);
            assertThat(res.getTotalElements()).isEqualTo(17);
            assertThat(res.getTotalPages()).isEqualTo(4);
        }
    }

    // -----------------------------------------------------------------------
    // followUser
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("followUser")
    class FollowUser {

        @Test
        @DisplayName("happy path: creates and saves follow")
        void happyPath() {
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
            when(followRepository.save(any(Follow.class))).thenAnswer(inv -> inv.getArgument(0));

            followService.followUser("alice", 2L);

            ArgumentCaptor<Follow> captor = ArgumentCaptor.forClass(Follow.class);
            verify(followRepository).save(captor.capture());
            Follow saved = captor.getValue();
            assertThat(saved.getFollower()).isEqualTo(alice);
            assertThat(saved.getFollowed()).isEqualTo(bob);
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getCreatedAt()).isAfter(OffsetDateTime.now().minusSeconds(5));
            assertThat(saved.getCreatedAt()).isBefore(OffsetDateTime.now().plusSeconds(1));
        }

        @Test
        @DisplayName("should throw UserNotFoundException when follower not found")
        void shouldThrowWhenFollowerNotFound() {
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            UserNotFoundException ex = assertThrows(UserNotFoundException.class, () -> followService.followUser("ghost", 2L));

            assertThat(ex.getMessage()).contains("ghost");
            assertThat(ex.getMessage()).isEqualTo("Follower user not found with username: <ghost>");
            verify(followRepository, never()).save(any());
            // should not even lookup followed
            verify(userRepository, never()).findById(anyLong());
        }

        @Test
        @DisplayName("should throw UserNotFoundException when followed not found")
        void shouldThrowWhenFollowedNotFound() {
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            UserNotFoundException ex = assertThrows(UserNotFoundException.class, () -> followService.followUser("alice", 99L));

            assertThat(ex.getMessage()).contains("99");
            assertThat(ex.getMessage()).isEqualTo("Followed user not found with ID: <99>");
            verify(followRepository, never()).save(any());
        }

        @Test
        @DisplayName("should allow self-follow (current behavior saves)")
        void shouldAllowSelfFollow() {
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
            when(followRepository.save(any(Follow.class))).thenAnswer(inv -> inv.getArgument(0));

            // current service does not prevent self-follow, so it should save
            followService.followUser("alice", 1L);

            ArgumentCaptor<Follow> captor = ArgumentCaptor.forClass(Follow.class);
            verify(followRepository).save(captor.capture());
            assertThat(captor.getValue().getFollower()).isEqualTo(alice);
            assertThat(captor.getValue().getFollowed()).isEqualTo(alice);
        }

        @Test
        @DisplayName("should lookup follower by username and followed by id")
        void shouldLookupCorrectly() {
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(userRepository.findById(3L)).thenReturn(Optional.of(charlie));
            when(followRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            followService.followUser("alice", 3L);

            verify(userRepository).findByUsername("alice");
            verify(userRepository).findById(3L);
        }
    }

    // -----------------------------------------------------------------------
    // unfollowUser
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("unfollowUser")
    class UnfollowUser {

        @Test
        @DisplayName("happy path: deletes existing follow")
        void happyPath() {
            Follow existing = follow(alice, bob);
            existing.setId(50L);
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
            when(followRepository.findByFollowerAndFollowed(alice, bob)).thenReturn(Optional.of(existing));

            followService.unfollowUser("alice", 2L);

            verify(followRepository).delete(existing);
        }

        @Test
        @DisplayName("should throw UserNotFoundException when follower not found")
        void shouldThrowWhenFollowerNotFound() {
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            UserNotFoundException ex = assertThrows(UserNotFoundException.class, () -> followService.unfollowUser("ghost", 2L));

            assertThat(ex.getMessage()).isEqualTo("Follower user not found with username: <ghost>");
            verify(followRepository, never()).findByFollowerAndFollowed(any(), any());
            verify(followRepository, never()).delete(any());
        }

        @Test
        @DisplayName("should throw UserNotFoundException when followed not found")
        void shouldThrowWhenFollowedNotFound() {
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            UserNotFoundException ex = assertThrows(UserNotFoundException.class, () -> followService.unfollowUser("alice", 99L));

            assertThat(ex.getMessage()).isEqualTo("Followed user not found with ID: <99>");
            verify(followRepository, never()).delete(any());
        }

        @Test
        @DisplayName("should throw FollowException when follow does not exist")
        void shouldThrowWhenFollowNotExists() {
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
            when(followRepository.findByFollowerAndFollowed(alice, bob)).thenReturn(Optional.empty());

            FollowException ex = assertThrows(FollowException.class, () -> followService.unfollowUser("alice", 2L));

            assertThat(ex.getMessage()).contains("1");
            assertThat(ex.getMessage()).contains("2");
            assertThat(ex.getMessage()).isEqualTo("A follow from user with ID: <1>, following user with ID: <2>, doesn't exist");
            verify(followRepository, never()).delete(any());
        }

        @Test
        @DisplayName("should lookup follow with correct follower and followed instances")
        void shouldLookupWithCorrectInstances() {
            Follow existing = follow(alice, charlie);
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(userRepository.findById(3L)).thenReturn(Optional.of(charlie));
            when(followRepository.findByFollowerAndFollowed(alice, charlie)).thenReturn(Optional.of(existing));

            followService.unfollowUser("alice", 3L);

            verify(followRepository).findByFollowerAndFollowed(alice, charlie);
            verify(followRepository).delete(existing);
        }

        @Test
        @DisplayName("should not delete when follow missing")
        void shouldNotDeleteWhenMissing() {
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
            when(followRepository.findByFollowerAndFollowed(any(), any())).thenReturn(Optional.empty());

            assertThrows(FollowException.class, () -> followService.unfollowUser("alice", 2L));

            verify(followRepository, never()).delete(any());
        }
    }
}
