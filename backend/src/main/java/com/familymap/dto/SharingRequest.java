package com.familymap.dto;

import jakarta.validation.constraints.NotNull;

/** Body of {@code PUT /api/families/{id}/members/me/sharing}: pause ({@code false}) or resume. */
public record SharingRequest(@NotNull Boolean enabled) {
}
