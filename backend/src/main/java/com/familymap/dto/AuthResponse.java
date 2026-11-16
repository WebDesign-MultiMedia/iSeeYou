package com.familymap.dto;

/**
 * Returned by register, login and refresh.
 *
 * @param expiresIn access-token lifetime in seconds, so clients can refresh proactively
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserResponse user) {

    public static AuthResponse bearer(String accessToken, String refreshToken, long expiresIn, UserResponse user) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresIn, user);
    }
}
