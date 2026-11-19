package com.familymap.service;

import com.familymap.dto.MemberResponse;

import java.util.UUID;

/**
 * Published when a member pauses or resumes sharing in a circle. The realtime layer pushes
 * {@code member} to the rest of the circle after commit. When paused, {@code member.lastLocation}
 * is null, so the update itself also hides their last known position.
 */
public record MemberSharingChangedEvent(UUID familyId, MemberResponse member) {
}
