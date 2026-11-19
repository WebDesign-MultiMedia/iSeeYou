package com.familymap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/families}. */
public record CreateFamilyRequest(@NotBlank @Size(max = 100) String name) {
}
