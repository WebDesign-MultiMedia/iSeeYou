package com.familymap.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and verifies HMAC-signed JWTs.
 *
 * <p>Two token types share one key but carry a {@code typ} claim, so a refresh token
 * can never be used as an access token (and vice versa).
 *
 * <p>Tradeoff: refresh tokens are stateless, so they can't be revoked before expiry.
 * A server-side refresh-token table (rotation + revocation) is the next hardening step.
 */
@Service
public class JwtService {

    static final String CLAIM_TYPE = "typ";
    static final String CLAIM_EMAIL = "email";
    private static final String ISSUER = "familymap";

    public enum TokenType { ACCESS, REFRESH }

    private final SecretKey key;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        byte[] keyBytes = Decoders.BASE64.decode(properties.secret());
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must decode to at least 256 bits (32 bytes)");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String issueAccessToken(UUID userId, String email) {
        return issue(userId, email, TokenType.ACCESS, properties.accessTokenTtl());
    }

    public String issueRefreshToken(UUID userId, String email) {
        return issue(userId, email, TokenType.REFRESH, properties.refreshTokenTtl());
    }

    public Duration accessTokenTtl() {
        return properties.accessTokenTtl();
    }

    /**
     * Verifies signature, issuer, expiry and token type.
     *
     * @return the authenticated user, or empty if the token is invalid for any reason
     */
    public Optional<AuthenticatedUser> verify(String token, TokenType expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!expectedType.name().equals(claims.get(CLAIM_TYPE, String.class))) {
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedUser(
                    UUID.fromString(claims.getSubject()),
                    claims.get(CLAIM_EMAIL, String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            // Expired, tampered, malformed, wrong issuer, bad UUID: all treated as "not authenticated".
            return Optional.empty();
        }
    }

    private String issue(UUID userId, String email, TokenType type, Duration ttl) {
        Instant now = clock.instant();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .subject(userId.toString())
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_TYPE, type.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }
}
