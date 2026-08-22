package com.github.ferigeek.sarv.service;

import com.github.ferigeek.sarv.dto.request.UserLoginRequest;
import com.github.ferigeek.sarv.dto.request.UserRegisterRequest;
import com.github.ferigeek.sarv.dto.response.UserRegisterResponse;
import com.github.ferigeek.sarv.entity.User;
import com.github.ferigeek.sarv.entity.type.Gender;
import com.github.ferigeek.sarv.exception.UsernameAlreadyExistsException;
import com.github.ferigeek.sarv.repository.UserRepository;
import com.github.ferigeek.sarv.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    private UserRegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new UserRegisterRequest(
                "ferigeek",
                "strongPass123",
                "feri@example.com",
                "Feri Geek",
                Gender.MALE
        );
    }

    // -----------------------------------------------------------------------
    // login
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("should authenticate and return JWT token on happy path")
        void shouldReturnTokenOnHappyPath() {
            UserLoginRequest req = new UserLoginRequest("ferigeek", "strongPass123");
            Authentication authentication = mock(Authentication.class);
            UserDetails userDetails = mock(UserDetails.class);

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(userDetails);
            when(userDetails.getUsername()).thenReturn("ferigeek");
            when(jwtUtil.generateToken("ferigeek")).thenReturn("jwt-token-123");

            String token = authService.login(req);

            assertThat(token).isEqualTo("jwt-token-123");

            ArgumentCaptor<UsernamePasswordAuthenticationToken> captor =
                    ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
            verify(authenticationManager).authenticate(captor.capture());
            UsernamePasswordAuthenticationToken captured = captor.getValue();
            assertThat(captured.getPrincipal()).isEqualTo("ferigeek");
            assertThat(captured.getCredentials()).isEqualTo("strongPass123");
            verify(jwtUtil).generateToken("ferigeek");
        }

        @Test
        @DisplayName("should propagate BadCredentialsException from authenticationManager")
        void shouldPropagateBadCredentials() {
            UserLoginRequest req = new UserLoginRequest("ferigeek", "wrongPass");
            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            assertThrows(BadCredentialsException.class, () -> authService.login(req));

            verify(jwtUtil, never()).generateToken(anyString());
        }

        @Test
        @DisplayName("should propagate exception when jwt generation fails")
        void shouldPropagateJwtFailure() {
            UserLoginRequest req = new UserLoginRequest("ferigeek", "strongPass123");
            Authentication authentication = mock(Authentication.class);
            UserDetails userDetails = mock(UserDetails.class);
            when(authenticationManager.authenticate(any())).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(userDetails);
            when(userDetails.getUsername()).thenReturn("ferigeek");
            when(jwtUtil.generateToken("ferigeek")).thenThrow(new RuntimeException("jwt error"));

            assertThrows(RuntimeException.class, () -> authService.login(req));
        }

        @Test
        @DisplayName("should use username from UserDetails not request for token generation")
        void shouldUsePrincipalUsername() {
            // request username and principal username could differ in case sensitivity
            UserLoginRequest req = new UserLoginRequest("FeriGeek", "pass");
            Authentication authentication = mock(Authentication.class);
            UserDetails userDetails = mock(UserDetails.class);
            when(authenticationManager.authenticate(any())).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(userDetails);
            when(userDetails.getUsername()).thenReturn("ferigeek");
            when(jwtUtil.generateToken("ferigeek")).thenReturn("token");

            String result = authService.login(req);

            assertThat(result).isEqualTo("token");
            verify(jwtUtil).generateToken("ferigeek");
            verify(jwtUtil, never()).generateToken("FeriGeek");
        }

        @Test
        @DisplayName("should authenticate with exact credentials from request")
        void shouldAuthenticateWithExactCredentials() {
            UserLoginRequest req = new UserLoginRequest("alice", "mySecret99");
            Authentication authentication = mock(Authentication.class);
            UserDetails userDetails = mock(UserDetails.class);
            when(authenticationManager.authenticate(any())).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(userDetails);
            when(userDetails.getUsername()).thenReturn("alice");
            when(jwtUtil.generateToken(anyString())).thenReturn("t");

            authService.login(req);

            verify(authenticationManager).authenticate(argThat(token -> {
                UsernamePasswordAuthenticationToken up = (UsernamePasswordAuthenticationToken) token;
                return "alice".equals(up.getPrincipal()) && "mySecret99".equals(up.getCredentials());
            }));
        }
    }

    // -----------------------------------------------------------------------
    // register
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("happy path: creates user, encodes password, saves and returns token")
        void happyPath() {
            when(userRepository.existsByUsername("ferigeek")).thenReturn(false);
            when(passwordEncoder.encode("strongPass123")).thenReturn("hashedPass");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(1L);
                return u;
            });

            Authentication authentication = mock(Authentication.class);
            UserDetails userDetails = mock(UserDetails.class);
            when(authenticationManager.authenticate(any())).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(userDetails);
            when(userDetails.getUsername()).thenReturn("ferigeek");
            when(jwtUtil.generateToken("ferigeek")).thenReturn("jwt-token");

            UserRegisterResponse response = authService.register(registerRequest);

            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getUsername()).isEqualTo("ferigeek");
            assertThat(response.getEmail()).isEqualTo("feri@example.com");
            assertThat(response.getDisplayName()).isEqualTo("Feri Geek");
            assertThat(response.getToken()).isEqualTo("jwt-token");

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            User saved = captor.getValue();
            assertThat(saved.getUsername()).isEqualTo("ferigeek");
            assertThat(saved.getEmail()).isEqualTo("feri@example.com");
            assertThat(saved.getDisplayName()).isEqualTo("Feri Geek");
            assertThat(saved.getGender()).isEqualTo(Gender.MALE);
            assertThat(saved.getPasswordHash()).isEqualTo("hashedPass");
            assertThat(saved.getCreatedAt()).isNotNull();
            verify(passwordEncoder).encode("strongPass123");
        }

        @Test
        @DisplayName("should set createdAt close to now")
        void shouldSetCreatedAtCloseToNow() {
            when(userRepository.existsByUsername(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("hash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            Authentication authentication = mock(Authentication.class);
            UserDetails userDetails = mock(UserDetails.class);
            when(authenticationManager.authenticate(any())).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(userDetails);
            when(userDetails.getUsername()).thenReturn("ferigeek");
            when(jwtUtil.generateToken(anyString())).thenReturn("tok");

            authService.register(registerRequest);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            User saved = captor.getValue();
            assertThat(saved.getCreatedAt()).isNotNull();
            // within last 5 seconds
            assertThat(saved.getCreatedAt()).isAfter(java.time.OffsetDateTime.now().minusSeconds(5));
            assertThat(saved.getCreatedAt()).isBefore(java.time.OffsetDateTime.now().plusSeconds(1));
        }

        @Test
        @DisplayName("should throw UsernameAlreadyExistsException when username exists")
        void shouldThrowWhenUsernameExists() {
            when(userRepository.existsByUsername("ferigeek")).thenReturn(true);

            assertThrows(UsernameAlreadyExistsException.class, () -> authService.register(registerRequest));

            verify(userRepository, never()).save(any());
            verify(passwordEncoder, never()).encode(anyString());
            verify(authenticationManager, never()).authenticate(any());
            verify(jwtUtil, never()).generateToken(anyString());
        }

        @Test
        @DisplayName("should throw RuntimeException with message when save fails")
        void shouldThrowWhenSaveFails() {
            when(userRepository.existsByUsername("ferigeek")).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("hash");
            when(userRepository.save(any(User.class))).thenThrow(new RuntimeException("db error"));

            RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.register(registerRequest));

            assertThat(ex.getMessage()).isEqualTo("Error while registering user");
            verify(authenticationManager, never()).authenticate(any());
            verify(jwtUtil, never()).generateToken(anyString());
        }

        @Test
        @DisplayName("should throw RuntimeException when passwordEncoder fails")
        void shouldThrowWhenEncodeFails() {
            when(userRepository.existsByUsername("ferigeek")).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenThrow(new RuntimeException("encoder fail"));

            RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.register(registerRequest));

            assertThat(ex.getMessage()).isEqualTo("Error while registering user");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw RuntimeException when login/authentication fails during register")
        void shouldThrowWhenLoginFails() {
            when(userRepository.existsByUsername("ferigeek")).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("hash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(10L);
                return u;
            });
            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("bad creds"));

            RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.register(registerRequest));

            assertThat(ex.getMessage()).isEqualTo("Error while generating token");
            // user was still saved before login attempt
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("should throw RuntimeException when jwt generation fails during register")
        void shouldThrowWhenJwtFails() {
            when(userRepository.existsByUsername("ferigeek")).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("hash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(10L);
                return u;
            });
            Authentication authentication = mock(Authentication.class);
            UserDetails userDetails = mock(UserDetails.class);
            when(authenticationManager.authenticate(any())).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(userDetails);
            when(userDetails.getUsername()).thenReturn("ferigeek");
            when(jwtUtil.generateToken(anyString())).thenThrow(new RuntimeException("jwt fail"));

            RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.register(registerRequest));

            assertThat(ex.getMessage()).isEqualTo("Error while generating token");
        }

        @Test
        @DisplayName("should call login with username and password from register request")
        void shouldCallLoginWithCorrectCredentials() {
            when(userRepository.existsByUsername("ferigeek")).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("hash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(1L);
                return u;
            });
            Authentication authentication = mock(Authentication.class);
            UserDetails userDetails = mock(UserDetails.class);
            when(authenticationManager.authenticate(any())).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(userDetails);
            when(userDetails.getUsername()).thenReturn("ferigeek");
            when(jwtUtil.generateToken(anyString())).thenReturn("tok");

            authService.register(registerRequest);

            ArgumentCaptor<UsernamePasswordAuthenticationToken> captor =
                    ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
            verify(authenticationManager).authenticate(captor.capture());
            UsernamePasswordAuthenticationToken token = captor.getValue();
            assertThat(token.getPrincipal()).isEqualTo("ferigeek");
            assertThat(token.getCredentials()).isEqualTo("strongPass123");
        }

        @Test
        @DisplayName("should preserve gender from request")
        void shouldPreserveGender() {
            for (Gender g : Gender.values()) {
                UserRegisterRequest req = new UserRegisterRequest("user" + g, "pass12345", g.name().toLowerCase() + "@ex.com", "Display", g);
                when(userRepository.existsByUsername(anyString())).thenReturn(false);
                when(passwordEncoder.encode(anyString())).thenReturn("hash");
                when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
                Authentication authentication = mock(Authentication.class);
                UserDetails userDetails = mock(UserDetails.class);
                when(authenticationManager.authenticate(any())).thenReturn(authentication);
                when(authentication.getPrincipal()).thenReturn(userDetails);
                when(userDetails.getUsername()).thenReturn(req.getUsername());
                when(jwtUtil.generateToken(anyString())).thenReturn("tok");

                authService.register(req);

                ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
                verify(userRepository).save(captor.capture());
                assertThat(captor.getValue().getGender()).isEqualTo(g);
                // reset mocks for next iteration
                reset(userRepository, passwordEncoder, authenticationManager, jwtUtil);
            }
        }

        @Test
        @DisplayName("should not call jwtUtil when save fails")
        void shouldNotCallJwtWhenSaveFails() {
            when(userRepository.existsByUsername(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("hash");
            when(userRepository.save(any())).thenThrow(new RuntimeException("fail"));

            assertThrows(RuntimeException.class, () -> authService.register(registerRequest));

            verify(jwtUtil, never()).generateToken(anyString());
        }

        @Test
        @DisplayName("should encode raw password not already hashed")
        void shouldEncodeRawPassword() {
            when(userRepository.existsByUsername(anyString())).thenReturn(false);
            when(passwordEncoder.encode("strongPass123")).thenReturn("encoded123");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            Authentication authentication = mock(Authentication.class);
            UserDetails userDetails = mock(UserDetails.class);
            when(authenticationManager.authenticate(any())).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(userDetails);
            when(userDetails.getUsername()).thenReturn("ferigeek");
            when(jwtUtil.generateToken(anyString())).thenReturn("tok");

            authService.register(registerRequest);

            verify(passwordEncoder).encode("strongPass123");
            verify(passwordEncoder, never()).encode("encoded123");
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getPasswordHash()).isEqualTo("encoded123");
        }

        @Test
        @DisplayName("should return UserRegisterResponse with correct mapping and token")
        void shouldReturnCorrectResponseMapping() {
            when(userRepository.existsByUsername("ferigeek")).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("hash");
            User savedUser = new User();
            savedUser.setId(99L);
            savedUser.setUsername("ferigeek");
            savedUser.setDisplayName("Feri Geek");
            savedUser.setEmail("feri@example.com");
            savedUser.setGender(Gender.FEMALE);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            Authentication authentication = mock(Authentication.class);
            UserDetails userDetails = mock(UserDetails.class);
            when(authenticationManager.authenticate(any())).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(userDetails);
            when(userDetails.getUsername()).thenReturn("ferigeek");
            when(jwtUtil.generateToken("ferigeek")).thenReturn("my-jwt");

            // need to use request with same data but service will override saved values?
            // Actually service creates user from request then saves, so savedUser returned from save is used.
            // To test mapping, we mock save to return our savedUser with custom gender to see if response reflects saved user.
            // However current mock returns savedUser ignoring request's gender MALE vs saved FEMALE.
            // This tests that response is built from returned saved user, not request.
            UserRegisterResponse resp = authService.register(registerRequest);

            assertThat(resp.getId()).isEqualTo(99L);
            assertThat(resp.getUsername()).isEqualTo("ferigeek");
            assertThat(resp.getDisplayName()).isEqualTo("Feri Geek");
            assertThat(resp.getEmail()).isEqualTo("feri@example.com");
            assertThat(resp.getToken()).isEqualTo("my-jwt");
        }

        @Test
        @DisplayName("should check existence with exact username (case-sensitive)")
        void shouldCheckExactUsername() {
            when(userRepository.existsByUsername("Ferigeek")).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("hash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            Authentication authentication = mock(Authentication.class);
            UserDetails userDetails = mock(UserDetails.class);
            when(authenticationManager.authenticate(any())).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(userDetails);
            when(userDetails.getUsername()).thenReturn("Ferigeek");
            when(jwtUtil.generateToken(anyString())).thenReturn("tok");

            UserRegisterRequest req = new UserRegisterRequest("Ferigeek", "pass12345", "a@b.com", "Disp", Gender.MALE);
            authService.register(req);

            verify(userRepository).existsByUsername("Ferigeek");
            verify(userRepository, never()).existsByUsername("ferigeek");
        }
    }
}
