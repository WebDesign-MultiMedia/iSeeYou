package com.familymap.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * JWT settings bound from {@code familymap.jwt.*}. Validated at startup so the app
 * fails fast if {@code JWT_SECRET} is missing.
 *
 * @param secret          Base64-encoded HMAC-SHA key (>= 256 bits)
 * @param accessTokenTtl  lifetime of access tokens (short; sent on every request)
 * @param refreshTokenTtl lifetime of refresh tokens (long; only used to mint new access tokens)
 */
@Validated
@ConfigurationProperties(prefix = "familymap.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl) {
}
