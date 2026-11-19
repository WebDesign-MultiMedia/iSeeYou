package com.familymap.dto;

import com.familymap.domain.FamilyMember;
import com.familymap.domain.FamilyRole;

import java.time.Instant;
import java.util.UUID;

/**
 * One member of a circle. Email is left out on purpose: members need a name and avatar on
 * the map, not each other's login identifiers.
 *
 * @param lastLocation the member's most recent point, or null if they have none or have
 *                     paused sharing in this circle
 */
public record MemberResponse(UUID userId, String name, String avatarUrl, FamilyRole role,
                             boolean sharingEnabled, Instant joinedAt, LocationResponse lastLocation) {

    public static MemberResponse from(FamilyMember m, LocationResponse lastLocation) {
        // Consent: a paused member's location is never shown, not even their last known one.
        LocationResponse visible = m.isSharingEnabled() ? lastLocation : null;
        return new MemberResponse(m.getUser().getId(), m.getUser().getName(), m.getUser().getAvatarUrl(),
                m.getRole(), m.isSharingEnabled(), m.getJoinedAt(), visible);
    }
}
