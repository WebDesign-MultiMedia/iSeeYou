package com.familymap.service;

import com.familymap.dto.MemberResponse;

import java.util.UUID;

/**
 * Published when a member's visible state changes in a circle: they paused or resumed sharing,
 * or deleted their location history. The realtime layer pushes {@code member} to the rest of
 * the circle after commit. A null {@code member.lastLocation} removes their marker.
 */
public record MemberUpdatedEvent(UUID familyId, MemberResponse member) {
}
