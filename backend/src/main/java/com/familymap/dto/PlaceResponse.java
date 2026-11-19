package com.familymap.dto;

import com.familymap.domain.Place;

import java.time.Instant;
import java.util.UUID;

public record PlaceResponse(UUID id, UUID familyId, String name, double lat, double lng, int radiusM,
                            UUID createdBy, Instant createdAt) {

    public static PlaceResponse from(Place p) {
        return new PlaceResponse(p.getId(), p.getFamilyId(), p.getName(), p.getLat(), p.getLng(), p.getRadiusM(),
                p.getCreatedBy(), p.getCreatedAt());
    }
}
