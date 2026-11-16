package com.familymap.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /api/auth/refresh}. */
public record RefreshRequest(@NotBlank String refreshToken) {
}
