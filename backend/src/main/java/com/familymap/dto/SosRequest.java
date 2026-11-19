package com.familymap.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Body of {@code POST /api/sos}. Every field is optional: if the phone has no GPS fix, the SOS
 * still goes out, and the circle sees "location unknown".
 */
public record SosRequest(
        @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double lng,
        @PositiveOrZero @DecimalMax("100000") Double accuracy,
        @Min(0) @Max(100) Integer battery) {

    @AssertTrue(message = "lat and lng must be sent together")
    public boolean isCoordinatePairComplete() {
        return (lat == null) == (lng == null);
    }
}
