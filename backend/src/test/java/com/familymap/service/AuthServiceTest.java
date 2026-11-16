package com.familymap.service;

import com.familymap.domain.User;
import com.familymap.dto.AuthResponse;
import com.familymap.dto.LoginRequest;
import com.familymap.dto.RegisterRequest;
import com.familymap.exception.EmailAlreadyRegisteredException;
import com.familymap.exception.InvalidCredentialsException;
import com.familymap.repository.UserRepository;
import com.familymap.security.AuthenticatedUser;
import com.familymap.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    // Low BCrypt cost keeps the unit tests fast.
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private UserRepository users;
    private JwtService jwt;
    private AuthService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        jwt = mock(JwtService.class);
        when(jwt.issueAccessToken(any(), anyString())).thenReturn("access");
        when(jwt.issueRefreshToken(any(), anyString())).thenReturn("refresh");
        when(jwt.accessTokenTtl()).thenReturn(Duration.ofMinutes(15));
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new AuthService(users, encoder, jwt);
    }

    @Test
    void registerHashesPasswordAndNormalizesEmail() {
        AuthResponse res = service.register(new RegisterRequest(" Ana ", "  Ana@Example.COM ", "s3cret-pass"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("ana@example.com");
        assertThat(saved.getValue().getName()).isEqualTo("Ana");
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("s3cret-pass");
        assertThat(encoder.matches("s3cret-pass", saved.getValue().getPasswordHash())).isTrue();

        assertThat(res.accessToken()).isEqualTo("access");
        assertThat(res.tokenType()).isEqualTo("Bearer");
        assertThat(res.expiresIn()).isEqualTo(900);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(users.existsByEmail("ana@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("Ana", "ANA@example.com", "s3cret-pass")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        verify(users, never()).save(any());
    }

    @Test
    void loginSucceedsWithCorrectPassword() {
        User ana = new User("Ana", "ana@example.com", encoder.encode("s3cret-pass"));
        when(users.findByEmail("ana@example.com")).thenReturn(Optional.of(ana));

        assertThat(service.login(new LoginRequest("Ana@Example.com", "s3cret-pass")).user().id())
                .isEqualTo(ana.getId());
    }

    @Test
    void loginFailsWithWrongPasswordOrUnknownEmailUsingSameMessage() {
        User ana = new User("Ana", "ana@example.com", encoder.encode("s3cret-pass"));
        when(users.findByEmail("ana@example.com")).thenReturn(Optional.of(ana));
        when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("ana@example.com", "wrong-pass")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password.");
        assertThatThrownBy(() -> service.login(new LoginRequest("ghost@example.com", "whatever")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password.");
    }

    @Test
    void refreshFailsForDeletedUser() {
        User ana = new User("Ana", "ana@example.com", "hash");
        when(jwt.verify("r", JwtService.TokenType.REFRESH))
                .thenReturn(Optional.of(new AuthenticatedUser(ana.getId(), ana.getEmail())));
        when(users.findById(ana.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh("r")).isInstanceOf(InvalidCredentialsException.class);
    }
}
