package com.familymap.dto;

import com.familymap.domain.FamilyMember;
import com.familymap.domain.FamilyRole;

import java.time.Instant;
import java.util.UUID;

/**
 * One member of a circle. Email is left out on purpose: members need a name and avatar on
 * the map, not each other's login identifiers.
 */
public record MemberResponse(UUID userId, String name, String avatarUrl, FamilyRole role,
                             boolean sharingEnabled, Instant joinedAt) {

    public static MemberResponse from(FamilyMember m) {
        return new MemberResponse(m.getUser().getId(), m.getUser().getName(), m.getUser().getAvatarUrl(),
                m.getRole(), m.isSharingEnabled(), m.getJoinedAt());
    }
}
