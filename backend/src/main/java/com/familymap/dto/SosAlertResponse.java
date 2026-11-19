package com.familymap.dto;

import com.familymap.domain.SosAlert;

import java.time.Instant;
import java.util.UUID;

/**
 * An SOS as clients see it: the REST shape and the realtime message on
 * {@code /topic/family/{id}/sos}. {@code active=false} means it was resolved.
 */
public record SosAlertResponse(UUID id, UUID userId, String userName, Double lat, Double lng, Double accuracy,
                               Integer battery, boolean active, Instant createdAt, Instant updatedAt,
                               Instant resolvedAt, UUID resolvedById, String resolvedByName) {

    public static SosAlertResponse from(SosAlert a, String userName, String resolvedByName) {
        return new SosAlertResponse(a.getId(), a.getUserId(), userName, a.getLat(), a.getLng(), a.getAccuracy(),
                a.getBattery(), a.isActive(), a.getCreatedAt(), a.getUpdatedAt(), a.getResolvedAt(),
                a.getResolvedBy(), resolvedByName);
    }
}
