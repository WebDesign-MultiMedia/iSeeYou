package com.familymap.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

/**
 * A location fix from the device, sent as {@code POST /api/locations} or over STOMP to
 * {@code /app/locations}.
 *
 * @param accuracy   horizontal accuracy in meters (optional)
 * @param battery    battery level 0-100 (optional)
 * @param recordedAt when the fix was taken; defaults to "now". Lets a device that was
 *                   offline upload points late with their real timestamps.
 */
public record LocationRequest(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double lng,
        @PositiveOrZero @DecimalMax("100000") Double accuracy,
        @Min(0) @Max(100) Integer battery,
        Instant recordedAt) {
}
