package com.github.ferigeek.sarv.service;

import com.github.ferigeek.sarv.dto.request.UserLoginRequest;
import com.github.ferigeek.sarv.dto.request.UserRegisterRequest;
import com.github.ferigeek.sarv.dto.response.UserRegisterResponse;
import com.github.ferigeek.sarv.entity.User;
import com.github.ferigeek.sarv.exception.UsernameAlreadyExistsException;
import com.github.ferigeek.sarv.repository.UserRepository;
import com.github.ferigeek.sarv.security.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Slf4j
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Autowired
    public AuthService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public String login(UserLoginRequest userLoginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            userLoginRequest.getUsername(),
                            userLoginRequest.getPassword()
                    )
            );
            final UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            log.info("User logged in with username={}", userLoginRequest.getUsername());
            return jwtUtil.generateToken(userDetails.getUsername());
        } catch (AuthenticationException e) {
            log.warn("Failed login attempt username={}", userLoginRequest.getUsername());
            throw e;
        } catch (RuntimeException e) {
            log.error("Failed to generate token for username={}", userLoginRequest.getUsername(), e);
            throw e;
        }
    }

    public UserRegisterResponse register(UserRegisterRequest userRegisterRequest) {
        if (!userRegisterRequest.getPassword().equals(userRegisterRequest.getConfirmPassword())) {
            log.warn("Rejected registration with mismatched passwords username={}", userRegisterRequest.getUsername());
            throw new IllegalArgumentException("Passwords do not match");
        }

        if (userRepository.existsByUsername(userRegisterRequest.getUsername())) {
            log.warn("Rejected registration with existing username={}", userRegisterRequest.getUsername());
            throw new UsernameAlreadyExistsException();
        }

        User user = new User();
        user.setUsername(userRegisterRequest.getUsername());
        user.setEmail(userRegisterRequest.getEmail());
        user.setPasswordHash(passwordEncoder.encode(userRegisterRequest.getPassword()));
        user.setGender(userRegisterRequest.getGender());
        user.setDisplayName(userRegisterRequest.getDisplayName());
        user.setCreatedAt(OffsetDateTime.now());

        user = userRepository.save(user);

        try {
            String token = login(new UserLoginRequest(
                    userRegisterRequest.getUsername(),
                    userRegisterRequest.getPassword())
            );
            log.info("User registered username={}", userRegisterRequest.getUsername());
            return new UserRegisterResponse(user, token);
        } catch (AuthenticationException e) {
            log.error("Failed to generate token for user username={}", userRegisterRequest.getUsername(), e);
            throw e;
        } catch (RuntimeException e) {
            log.error("Failed to complete registration for username={}", userRegisterRequest.getUsername(), e);
            throw e;
        }
    }
}
