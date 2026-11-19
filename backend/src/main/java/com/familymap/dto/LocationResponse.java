package com.familymap.dto;

import com.familymap.domain.Location;

import java.time.Instant;
import java.util.UUID;

/**
 * A member's location as clients see it. This is both the REST shape and the realtime message
 * on {@code /topic/family/{id}/locations}. Clients should ignore a point whose
 * {@code recordedAt} is older than the one they're already showing (late uploads can arrive
 * out of order).
 */
public record LocationResponse(UUID userId, double lat, double lng, Double accuracy, Integer battery,
                               Instant recordedAt) {

    public static LocationResponse from(Location l) {
        return new LocationResponse(l.getUserId(), l.getLat(), l.getLng(), l.getAccuracy(), l.getBattery(),
                l.getRecordedAt());
    }
}
