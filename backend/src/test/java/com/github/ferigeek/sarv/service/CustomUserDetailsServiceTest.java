package com.github.ferigeek.sarv.service;

import com.github.ferigeek.sarv.entity.User;
import com.github.ferigeek.sarv.entity.type.UserStatus;
import com.github.ferigeek.sarv.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService service;

    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = new User();
        activeUser.setId(1L);
        activeUser.setUsername("alice");
        activeUser.setDisplayName("Alice");
        activeUser.setEmail("alice@example.com");
        activeUser.setPasswordHash("hashed-pw");
        activeUser.setCreatedAt(OffsetDateTime.now());
        activeUser.setStatus(UserStatus.ACTIVE);
    }

    private User userWithStatus(UserStatus status) {
        User u = new User();
        u.setId(2L);
        u.setUsername("bob");
        u.setDisplayName("Bob");
        u.setEmail("bob@example.com");
        u.setPasswordHash("hash2");
        u.setCreatedAt(OffsetDateTime.now());
        u.setStatus(status);
        return u;
    }

    @Test
    @DisplayName("should return UserDetails with username and password for ACTIVE user")
    void shouldReturnActiveUserDetails() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(activeUser));

        UserDetails details = service.loadUserByUsername("alice");

        assertThat(details.getUsername()).isEqualTo("alice");
        assertThat(details.getPassword()).isEqualTo("hashed-pw");
        assertThat(details.getAuthorities()).isEmpty();
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
        assertThat(details.isAccountNonExpired()).isTrue();
        assertThat(details.isCredentialsNonExpired()).isTrue();
        verify(userRepository).findByUsername("alice");
    }

    @Test
    @DisplayName("should throw UsernameNotFoundException when user not found")
    void shouldThrowWhenNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        UsernameNotFoundException ex = assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("ghost"));

        assertThat(ex.getMessage()).contains("ghost");
        assertThat(ex.getMessage()).isEqualTo("User not found with username: <ghost>");
        verify(userRepository).findByUsername("ghost");
    }

    @Test
    @DisplayName("should use exact username for lookup")
    void shouldUseExactUsername() {
        when(userRepository.findByUsername("Alice")).thenReturn(Optional.of(activeUser));

        service.loadUserByUsername("Alice");

        verify(userRepository).findByUsername("Alice");
        verify(userRepository, never()).findByUsername("alice");
    }

    @Test
    @DisplayName("should throw with formatted message containing requested username")
    void shouldFormatMessage() {
        when(userRepository.findByUsername("ferigeek")).thenReturn(Optional.empty());

        UsernameNotFoundException ex = assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("ferigeek"));

        assertThat(ex.getMessage()).isEqualTo("User not found with username: <ferigeek>");
    }

    @Test
    @DisplayName("should map SUSPENDED status to disabled and locked")
    void shouldMapSuspended() {
        User suspended = userWithStatus(UserStatus.SUSPENDED);
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(suspended));

        UserDetails details = service.loadUserByUsername("bob");

        assertThat(details.isEnabled()).isFalse(); // only ACTIVE is enabled
        assertThat(details.isAccountNonLocked()).isFalse(); // SUSPENDED is locked
        assertThat(details.isAccountNonExpired()).isTrue();
        assertThat(details.isCredentialsNonExpired()).isTrue();
    }

    @Test
    @DisplayName("should map BANNED status to disabled but not locked")
    void shouldMapBanned() {
        User banned = userWithStatus(UserStatus.BANNED);
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(banned));

        UserDetails details = service.loadUserByUsername("bob");

        assertThat(details.isEnabled()).isFalse();
        assertThat(details.isAccountNonLocked()).isTrue();
    }

    @Test
    @DisplayName("should map DELETED status to disabled but not locked")
    void shouldMapDeleted() {
        User deleted = userWithStatus(UserStatus.DELETED);
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(deleted));

        UserDetails details = service.loadUserByUsername("bob");

        assertThat(details.isEnabled()).isFalse();
        assertThat(details.isAccountNonLocked()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    @DisplayName("should map all UserStatus to correct enabled/locked flags")
    void shouldMapAllStatuses(UserStatus status) {
        User u = userWithStatus(status);
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(u));

        UserDetails details = service.loadUserByUsername("bob");

        boolean expectedEnabled = status == UserStatus.ACTIVE;
        boolean expectedNonLocked = status != UserStatus.SUSPENDED;

        assertThat(details.isEnabled()).isEqualTo(expectedEnabled);
        assertThat(details.isAccountNonLocked()).isEqualTo(expectedNonLocked);
        assertThat(details.isAccountNonExpired()).isTrue();
        assertThat(details.isCredentialsNonExpired()).isTrue();
    }

    @Test
    @DisplayName("should return authorities as empty list")
    void shouldReturnEmptyAuthorities() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(activeUser));

        UserDetails details = service.loadUserByUsername("alice");

        assertThat(details.getAuthorities()).isNotNull();
        assertThat(details.getAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("should return passwordHash as password")
    void shouldReturnPasswordHash() {
        activeUser.setPasswordHash("$2a$10$hashedvalue");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(activeUser));

        UserDetails details = service.loadUserByUsername("alice");

        assertThat(details.getPassword()).isEqualTo("$2a$10$hashedvalue");
    }

    @Test
    @DisplayName("should return username from persisted user, not requested case")
    void shouldReturnPersistedUsername() {
        User persisted = new User();
        persisted.setId(1L);
        persisted.setUsername("alice"); // lower case stored
        persisted.setPasswordHash("hash");
        persisted.setStatus(UserStatus.ACTIVE);
        persisted.setCreatedAt(OffsetDateTime.now());
        when(userRepository.findByUsername("Alice")).thenReturn(Optional.of(persisted));

        UserDetails details = service.loadUserByUsername("Alice");

        // service uses user.getUsername(), not requested param
        assertThat(details.getUsername()).isEqualTo("alice");
    }
}
