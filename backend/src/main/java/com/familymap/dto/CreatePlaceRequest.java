package com.familymap.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/families/{id}/places}.
 *
 * @param radiusM geofence radius; under 50 m GPS noise dominates, over 2 km it stops meaning "at"
 */
public record CreatePlaceRequest(
        @NotBlank @Size(max = 60) String name,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double lng,
        @NotNull @Min(50) @Max(2000) Integer radiusM) {
}
