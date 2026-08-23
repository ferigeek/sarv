package com.github.ferigeek.sarv.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.ferigeek.sarv.dto.request.UserLoginRequest;
import com.github.ferigeek.sarv.dto.request.UserRegisterRequest;
import com.github.ferigeek.sarv.dto.response.UserRegisterResponse;
import com.github.ferigeek.sarv.entity.type.Gender;
import com.github.ferigeek.sarv.exception.UsernameAlreadyExistsException;
import com.github.ferigeek.sarv.repository.EventLogRepository;
import com.github.ferigeek.sarv.repository.PostRepository;
import com.github.ferigeek.sarv.repository.UserRepository;
import com.github.ferigeek.sarv.security.JwtUtil;
import com.github.ferigeek.sarv.service.AuthService;
import com.github.ferigeek.sarv.service.CustomUserDetailsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.github.ferigeek.sarv.security.JwtAuthFilter;
import com.github.ferigeek.sarv.security.SecurityConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, AuthControllerTest.TestJwtFilterConfig.class})
@DisplayName("AuthController")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AuthService authService;

    // Security filter dependencies
    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    // JwtAuthFilter will be provided as a real bean via TestConfig below using mocked collaborators

    // Aspect dependencies — register mocks so EventLoggingAspect can be instantiated
    @MockitoBean
    private EventLogRepository eventLogRepository;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private PostRepository postRepository;

    @TestConfiguration
    static class TestJwtFilterConfig {
        @Bean
        JwtAuthFilter jwtAuthFilter(JwtUtil jwtUtil, CustomUserDetailsService customUserDetailsService) {
            return new JwtAuthFilter(jwtUtil, customUserDetailsService);
        }
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }

    private UserLoginRequest validLogin() {
        return new UserLoginRequest("ferigeek", "strongPass123");
    }

    private UserRegisterRequest validRegister() {
        return new UserRegisterRequest("ferigeek", "strongPass123", "feri@example.com", "Feri Geek", Gender.MALE);
    }

    // =======================================================================
    // POST /api/auth/login
    // =======================================================================
    @Nested
    @DisplayName("POST /api/auth/login")
    class Login {

        @Test
        @DisplayName("should return 200 and token body on success")
        void shouldReturnTokenOnSuccess() throws Exception {
            when(authService.login(any(UserLoginRequest.class))).thenReturn("jwt-token-abc");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validLogin())))
                    .andExpect(status().isOk())
                    .andExpect(content().string("jwt-token-abc"));
        }

        @Test
        @DisplayName("should return 200 even without Authorization header (permitAll)")
        void shouldBePermitAll() throws Exception {
            when(authService.login(any())).thenReturn("tok");

            // no Authorization header at all
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validLogin())))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should return 400 when username is blank")
        void shouldReturn400WhenUsernameBlank() throws Exception {
            UserLoginRequest req = new UserLoginRequest("", "strongPass123");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when username is missing")
        void shouldReturn400WhenUsernameMissing() throws Exception {
            String json = """
                    {"password":"strongPass123"}
                    """;

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when username is too short (size <2)")
        void shouldReturn400WhenUsernameTooShort() throws Exception {
            UserLoginRequest req = new UserLoginRequest("a", "strongPass123");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when username is whitespace")
        void shouldReturn400WhenUsernameWhitespace() throws Exception {
            UserLoginRequest req = new UserLoginRequest("   ", "strongPass123");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when password is blank")
        void shouldReturn400WhenPasswordBlank() throws Exception {
            UserLoginRequest req = new UserLoginRequest("ferigeek", "");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when password is missing")
        void shouldReturn400WhenPasswordMissing() throws Exception {
            String json = """
                    {"username":"ferigeek"}
                    """;

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when password too short (<8)")
        void shouldReturn400WhenPasswordTooShort() throws Exception {
            UserLoginRequest req = new UserLoginRequest("ferigeek", "short");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when password too long (>50)")
        void shouldReturn400WhenPasswordTooLong() throws Exception {
            String longPass = "a".repeat(51);
            UserLoginRequest req = new UserLoginRequest("ferigeek", longPass);

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when password is whitespace")
        void shouldReturn400WhenPasswordWhitespace() throws Exception {
            UserLoginRequest req = new UserLoginRequest("ferigeek", "        ");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when both fields invalid")
        void shouldReturn400WhenBothInvalid() throws Exception {
            UserLoginRequest req = new UserLoginRequest("", "");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when request body is empty")
        void shouldReturn400WhenEmptyBody() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when JSON is malformed")
        void shouldReturn400WhenMalformedJson() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{invalid json"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when body is missing (no content)")
        void shouldReturn400WhenNoBody() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should propagate 401 when authentication fails (BadCredentialsException)")
        void shouldReturn401WhenBadCredentials() throws Exception {
            when(authService.login(any())).thenThrow(new org.springframework.security.authentication.BadCredentialsException("Bad credentials"));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validLogin())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should return 500 when service throws unexpected RuntimeException")
        void shouldReturn500WhenServiceThrows() throws Exception {
            when(authService.login(any())).thenThrow(new RuntimeException("unexpected"));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validLogin())))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                    .andExpect(jsonPath("$.status").value(500));
        }

        @Test
        @DisplayName("should return 500 when JwtUtil fails indirectly via service RuntimeException")
        void shouldReturn500WhenJwtFails() throws Exception {
            when(authService.login(any())).thenThrow(new RuntimeException("Error while generating token"));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validLogin())))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("should return 405 for GET on login endpoint")
        void shouldReturn405ForGet() throws Exception {
            mockMvc.perform(get("/api/auth/login"))
                    .andExpect(status().isMethodNotAllowed());
        }

        @Test
        @DisplayName("should accept password length boundaries 8 and 50")
        void shouldAcceptPasswordBoundaries() throws Exception {
            // min =8
            when(authService.login(any())).thenReturn("tok");
            UserLoginRequest min = new UserLoginRequest("ferigeek", "12345678");
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(min)))
                    .andExpect(status().isOk());

            // max=50
            UserLoginRequest max = new UserLoginRequest("ferigeek", "a".repeat(50));
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(max)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should accept username length boundary 2")
        void shouldAcceptUsernameBoundary() throws Exception {
            when(authService.login(any())).thenReturn("tok");
            UserLoginRequest req = new UserLoginRequest("ab", "12345678");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should return content type text for successful login")
        void shouldReturnStringContent() throws Exception {
            when(authService.login(any())).thenReturn("my-jwt");
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validLogin())))
                    .andExpect(status().isOk())
                    .andExpect(content().string("my-jwt"));
        }
    }

    // =======================================================================
    // POST /api/auth/register
    // =======================================================================
    @Nested
    @DisplayName("POST /api/auth/register")
    class Register {

        private UserRegisterResponse successResponse() {
            return new UserRegisterResponse(1L, "ferigeek", "Feri Geek", "feri@example.com", "jwt-token-xyz");
        }

        @Test
        @DisplayName("should return 200 with JSON body on success")
        void shouldReturn200OnSuccess() throws Exception {
            when(authService.register(any(UserRegisterRequest.class))).thenReturn(successResponse());

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validRegister())))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.username").value("ferigeek"))
                    .andExpect(jsonPath("$.displayName").value("Feri Geek"))
                    .andExpect(jsonPath("$.email").value("feri@example.com"))
                    .andExpect(jsonPath("$.token").value("jwt-token-xyz"));
        }

        @Test
        @DisplayName("should be permitAll without Authorization header")
        void shouldBePermitAll() throws Exception {
            when(authService.register(any())).thenReturn(successResponse());

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validRegister())))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should return 400 when username is blank")
        void shouldReturn400WhenUsernameBlank() throws Exception {
            UserRegisterRequest req = new UserRegisterRequest("", "strongPass123", "feri@example.com", "Feri Geek", Gender.MALE);

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when username missing")
        void shouldReturn400WhenUsernameMissing() throws Exception {
            String json = """
                    {"password":"strongPass123","email":"feri@example.com","displayName":"Feri Geek","gender":"MALE"}
                    """;

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when username too short")
        void shouldReturn400WhenUsernameTooShort() throws Exception {
            UserRegisterRequest req = new UserRegisterRequest("a", "strongPass123", "feri@example.com", "Feri Geek", Gender.MALE);

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when username whitespace")
        void shouldReturn400WhenUsernameWhitespace() throws Exception {
            UserRegisterRequest req = new UserRegisterRequest("   ", "strongPass123", "feri@example.com", "Feri Geek", Gender.MALE);

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when password blank")
        void shouldReturn400WhenPasswordBlank() throws Exception {
            UserRegisterRequest req = new UserRegisterRequest("ferigeek", "", "feri@example.com", "Feri Geek", Gender.MALE);

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when password missing")
        void shouldReturn400WhenPasswordMissing() throws Exception {
            String json = """
                    {"username":"ferigeek","email":"feri@example.com","displayName":"Feri Geek","gender":"MALE"}
                    """;

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when password too short")
        void shouldReturn400WhenPasswordTooShort() throws Exception {
            UserRegisterRequest req = new UserRegisterRequest("ferigeek", "short", "feri@example.com", "Feri Geek", Gender.MALE);

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when password too long")
        void shouldReturn400WhenPasswordTooLong() throws Exception {
            UserRegisterRequest req = new UserRegisterRequest("ferigeek", "a".repeat(51), "feri@example.com", "Feri Geek", Gender.MALE);

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when email blank")
        void shouldReturn400WhenEmailBlank() throws Exception {
            UserRegisterRequest req = new UserRegisterRequest("ferigeek", "strongPass123", "", "Feri Geek", Gender.MALE);

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when email missing")
        void shouldReturn400WhenEmailMissing() throws Exception {
            String json = """
                    {"username":"ferigeek","password":"strongPass123","displayName":"Feri Geek","gender":"MALE"}
                    """;

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when email invalid format")
        void shouldReturn400WhenEmailInvalid() throws Exception {
            UserRegisterRequest req = new UserRegisterRequest("ferigeek", "strongPass123", "not-an-email", "Feri Geek", Gender.MALE);

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when email invalid with missing @")
        void shouldReturn400WhenEmailInvalidMissingAt() throws Exception {
            UserRegisterRequest req = new UserRegisterRequest("ferigeek", "strongPass123", "feriexample.com", "Feri Geek", Gender.MALE);

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when displayName blank")
        void shouldReturn400WhenDisplayNameBlank() throws Exception {
            UserRegisterRequest req = new UserRegisterRequest("ferigeek", "strongPass123", "feri@example.com", "", Gender.MALE);

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when displayName missing")
        void shouldReturn400WhenDisplayNameMissing() throws Exception {
            String json = """
                    {"username":"ferigeek","password":"strongPass123","email":"feri@example.com","gender":"MALE"}
                    """;

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when displayName too short")
        void shouldReturn400WhenDisplayNameTooShort() throws Exception {
            UserRegisterRequest req = new UserRegisterRequest("ferigeek", "strongPass123", "feri@example.com", "a", Gender.MALE);

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when displayName whitespace")
        void shouldReturn400WhenDisplayNameWhitespace() throws Exception {
            UserRegisterRequest req = new UserRegisterRequest("ferigeek", "strongPass123", "feri@example.com", "   ", Gender.MALE);

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when gender is null")
        void shouldReturn400WhenGenderNull() throws Exception {
            UserRegisterRequest req = new UserRegisterRequest("ferigeek", "strongPass123", "feri@example.com", "Feri Geek", null);

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when gender missing")
        void shouldReturn400WhenGenderMissing() throws Exception {
            String json = """
                    {"username":"ferigeek","password":"strongPass123","email":"feri@example.com","displayName":"Feri Geek"}
                    """;

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when gender invalid value")
        void shouldReturn400WhenGenderInvalid() throws Exception {
            String json = """
                    {"username":"ferigeek","password":"strongPass123","email":"feri@example.com","displayName":"Feri Geek","gender":"UNKNOWN"}
                    """;

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when body is empty")
        void shouldReturn400WhenEmptyBody() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when JSON malformed")
        void shouldReturn400WhenMalformedJson() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{invalid"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when no body")
        void shouldReturn400WhenNoBody() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when multiple fields invalid")
        void shouldReturn400WhenMultipleFieldsInvalid() throws Exception {
            UserRegisterRequest req = new UserRegisterRequest("", "short", "bad", "", null);

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should accept all valid Gender values")
        void shouldAcceptAllGenders() throws Exception {
            for (Gender g : Gender.values()) {
                when(authService.register(any())).thenReturn(
                        new UserRegisterResponse(1L, "user", "Display", "a@b.com", "tok"));
                UserRegisterRequest req = new UserRegisterRequest("validUser", "strongPass123", "a@b.com", "Display", g);
                mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(req)))
                        .andExpect(status().isOk());
            }
        }

        @Test
        @DisplayName("should accept boundary values: username 2, password 8/50, displayName 2")
        void shouldAcceptBoundaries() throws Exception {
            when(authService.register(any())).thenReturn(successResponse());
            UserRegisterRequest min = new UserRegisterRequest("ab", "12345678", "a@b.com", "ab", Gender.FEMALE);
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(min)))
                    .andExpect(status().isOk());

            UserRegisterRequest maxPass = new UserRegisterRequest("ab", "a".repeat(50), "a@b.com", "ab", Gender.FEMALE);
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(maxPass)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should return 409 when username already exists")
        void shouldReturn409WhenUsernameExists() throws Exception {
            when(authService.register(any())).thenThrow(new UsernameAlreadyExistsException());

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validRegister())))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value("Username is already taken"))
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.title").value("Conflict"))
                    .andExpect(jsonPath("$.instance").value("/api/auth/register"));
        }

        @Test
        @DisplayName("should return 409 with custom message when UsernameAlreadyExistsException has custom message")
        void shouldReturn409WithCustomMessage() throws Exception {
            when(authService.register(any())).thenThrow(new UsernameAlreadyExistsException("custom taken"));

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validRegister())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("custom taken"));
        }

        @Test
        @DisplayName("should return 500 when service throws RuntimeException (Error while registering user)")
        void shouldReturn500WhenRegisterFails() throws Exception {
            when(authService.register(any())).thenThrow(new RuntimeException("Error while registering user"));

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validRegister())))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.title").value("Internal Server Error"));
        }

        @Test
        @DisplayName("should return 500 when token generation fails during register")
        void shouldReturn500WhenTokenGenerationFails() throws Exception {
            when(authService.register(any())).thenThrow(new RuntimeException("Error while generating token"));

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validRegister())))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
        }

        @Test
        @DisplayName("should map IllegalArgumentException to 400")
        void shouldReturn400WhenIllegalArgument() throws Exception {
            when(authService.register(any())).thenThrow(new IllegalArgumentException("illegal arg"));

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validRegister())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("illegal arg"))
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("should return 405 for GET on register endpoint")
        void shouldReturn405ForGet() throws Exception {
            mockMvc.perform(get("/api/auth/register"))
                    .andExpect(status().isMethodNotAllowed());
        }

        @Test
        @DisplayName("should return 400 for wrong Content-Type (no JSON)")
        void shouldReturn400ForWrongContentType() throws Exception {
            // send form data without JSON
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("not json"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should include instance URI in ProblemDetail for errors")
        void shouldIncludeInstanceUri() throws Exception {
            when(authService.register(any())).thenThrow(new UsernameAlreadyExistsException());

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validRegister())))
                    .andExpect(jsonPath("$.instance").value("/api/auth/register"));
        }

        @Test
        @DisplayName("should return valid JSON structure for register success with correct field types")
        void shouldVerifyResponseFieldTypes() throws Exception {
            UserRegisterResponse resp = new UserRegisterResponse(42L, "john", "John Doe", "john@example.com", "token123");
            when(authService.register(any())).thenReturn(resp);

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validRegister())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.username").isString())
                    .andExpect(jsonPath("$.displayName").isString())
                    .andExpect(jsonPath("$.email").isString())
                    .andExpect(jsonPath("$.token").isString());
        }
    }

    // =======================================================================
    // Cross-cutting: HTTP method & content negotiation
    // =======================================================================
    @Nested
    @DisplayName("HTTP contract")
    class HttpContract {

        @Test
        @DisplayName("should return 405 for unsupported methods on both endpoints")
        void shouldReturn405ForUnsupportedMethods() throws Exception {
            mockMvc.perform(get("/api/auth/login")).andExpect(status().isMethodNotAllowed());
            mockMvc.perform(get("/api/auth/register")).andExpect(status().isMethodNotAllowed());
        }

        @Test
        @DisplayName("should return 403 for unknown auth sub-path (requires authentication)")
        void shouldReturn404ForUnknownPath() throws Exception {
            mockMvc.perform(post("/api/auth/unknown")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 401 for login BadCredentials translates to unauthorized")
        void shouldMapBadCredentialsTo401() throws Exception {
            when(authService.login(any())).thenThrow(new org.springframework.security.authentication.BadCredentialsException("Bad credentials"));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validLogin())))
                    .andExpect(status().isUnauthorized());
        }
    }
}
