package com.familymap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/families/join}. Case, spaces and dashes in the code are ignored. */
public record JoinFamilyRequest(@NotBlank @Size(max = 32) String inviteCode) {
}
