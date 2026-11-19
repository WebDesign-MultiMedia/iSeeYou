package com.familymap.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Something that happened in a circle, pushed live on {@code /topic/family/{id}/events} and,
 * for arrivals and departures, as a push notification to the other members.
 *
 * @param userId   who arrived or left, or who added/removed the place
 * @param at       when it happened (for arrivals: when the fix was recorded)
 */
public record CircleEvent(Type type, UUID familyId, UUID placeId, String placeName,
                          UUID userId, String userName, Instant at) {

    public enum Type { ARRIVED, LEFT, PLACE_ADDED, PLACE_REMOVED }

    public boolean isMovement() {
        return type == Type.ARRIVED || type == Type.LEFT;
    }
}
