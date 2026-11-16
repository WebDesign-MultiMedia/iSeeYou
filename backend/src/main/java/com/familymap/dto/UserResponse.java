package com.familymap.dto;

import com.familymap.domain.User;

import java.time.Instant;
import java.util.UUID;

/** Public view of a user. Never exposes the password hash. */
public record UserResponse(UUID id, String name, String email, String avatarUrl, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(),
                user.getAvatarUrl(), user.getCreatedAt());
    }
}
