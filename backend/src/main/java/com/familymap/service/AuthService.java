package com.familymap.service;

import com.familymap.domain.User;
import com.familymap.dto.AuthResponse;
import com.familymap.dto.LoginRequest;
import com.familymap.dto.RegisterRequest;
import com.familymap.dto.UserResponse;
import com.familymap.exception.EmailAlreadyRegisteredException;
import com.familymap.exception.InvalidCredentialsException;
import com.familymap.exception.ResourceNotFoundException;
import com.familymap.repository.UserRepository;
import com.familymap.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/**
 * Registration, login, token refresh and "who am I".
 */
@Service
public class AuthService {

    private static final String BAD_LOGIN = "Invalid email or password.";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * Hash compared against when the email is unknown, so a login for a missing account takes
     * about as long as one with a wrong password. This makes it harder to guess which emails exist.
     */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        User user = users.save(new User(request.name().trim(), email, passwordEncoder.encode(request.password())));
        return tokensFor(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(normalizeEmail(request.email())).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;

        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user == null || !passwordMatches) {
            throw new InvalidCredentialsException(BAD_LOGIN);
        }
        return tokensFor(user);
    }

    /** Swaps a valid refresh token for a new access + refresh pair. */
    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        UUID userId = jwtService.verify(refreshToken, JwtService.TokenType.REFRESH)
                .orElseThrow(() -> new InvalidCredentialsException("Refresh token is invalid or expired."))
                .id();
        // Re-load the user so a deleted account can't keep refreshing.
        User user = users.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("Refresh token is invalid or expired."));
        return tokensFor(user);
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(UUID userId) {
        return users.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    private AuthResponse tokensFor(User user) {
        return AuthResponse.bearer(
                jwtService.issueAccessToken(user.getId(), user.getEmail()),
                jwtService.issueRefreshToken(user.getId(), user.getEmail()),
                jwtService.accessTokenTtl().toSeconds(),
                UserResponse.from(user));
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
