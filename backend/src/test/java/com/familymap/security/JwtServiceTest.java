package com.familymap.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "dGVzdC1vbmx5LXNlY3JldC1mb3ItZmFtaWx5bWFwLWp3dC1zaWduaW5nLWtleS0xMjM0NTY=";
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    private final UUID userId = UUID.randomUUID();
    private JwtService jwt;

    @BeforeEach
    void setUp() {
        jwt = serviceAt(NOW);
    }

    @Test
    void accessTokenRoundTrips() {
        String token = jwt.issueAccessToken(userId, "ana@example.com");

        assertThat(jwt.verify(token, JwtService.TokenType.ACCESS))
                .contains(new AuthenticatedUser(userId, "ana@example.com"));
    }

    @Test
    void refreshTokenIsRejectedWhereAccessTokenIsExpected() {
        String refresh = jwt.issueRefreshToken(userId, "ana@example.com");

        assertThat(jwt.verify(refresh, JwtService.TokenType.ACCESS)).isEmpty();
        assertThat(jwt.verify(refresh, JwtService.TokenType.REFRESH)).isPresent();
    }

    @Test
    void expiredTokenIsRejected() {
        String token = jwt.issueAccessToken(userId, "ana@example.com");

        JwtService later = serviceAt(NOW.plus(Duration.ofMinutes(16)));
        assertThat(later.verify(token, JwtService.TokenType.ACCESS)).isEmpty();
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwt.issueAccessToken(userId, "ana@example.com");
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");

        assertThat(jwt.verify(tampered, JwtService.TokenType.ACCESS)).isEmpty();
        assertThat(jwt.verify("not-a-jwt", JwtService.TokenType.ACCESS)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String otherSecret = "YW5vdGhlci1zZWNyZXQtdGhhdC1pcy1hbHNvLWxvbmctZW5vdWdoLWZvci1obWFjLTI1Ng==";
        JwtService other = new JwtService(
                new JwtProperties(otherSecret, Duration.ofMinutes(15), Duration.ofDays(7)),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(jwt.verify(other.issueAccessToken(userId, "x@y.z"), JwtService.TokenType.ACCESS)).isEmpty();
    }

    @Test
    void shortSecretFailsFast() {
        assertThatThrownBy(() -> new JwtService(
                new JwtProperties("c2hvcnQ=", Duration.ofMinutes(15), Duration.ofDays(7)), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }

    private static JwtService serviceAt(Instant instant) {
        return new JwtService(
                new JwtProperties(SECRET, Duration.ofMinutes(15), Duration.ofDays(7)),
                Clock.fixed(instant, ZoneOffset.UTC));
    }
}
