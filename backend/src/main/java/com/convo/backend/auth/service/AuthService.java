package com.convo.backend.auth.service;

import com.convo.backend.auth.dto.AuthPrincipal;
import com.convo.backend.auth.dto.AuthResponse;
import com.convo.backend.auth.dto.LoginRequest;
import com.convo.backend.auth.dto.SignupRequest;
import com.convo.backend.auth.dto.UserProfileResponse;
import com.convo.backend.auth.entity.User;
import com.convo.backend.auth.repository.UserRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password and confirm password must match");
        }

        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setDisplayName(buildDisplayName(request.firstName(), request.lastName()));
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        try {
            User savedUser = userRepository.save(user);
            String token = jwtService.generateToken(savedUser);

            return new AuthResponse(token, "Bearer", toProfile(savedUser));
        } catch (DataIntegrityViolationException ex) {
            // Handle uniqueness races where another request inserts the same email concurrently.
            if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User already exists");
        }
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, "Bearer", toProfile(user));
    }

    @Transactional(readOnly = true)
    public UserProfileResponse me(AuthPrincipal principal) {
        User user = userRepository.findByEmailIgnoreCase(principal.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        return toProfile(user);
    }

    private UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName()
        );
    }

    private String buildDisplayName(String firstName, String lastName) {
        return (firstName + " " + lastName).trim();
    }
}
