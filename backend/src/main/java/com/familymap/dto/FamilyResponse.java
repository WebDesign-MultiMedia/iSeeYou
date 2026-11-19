package com.familymap.dto;

import com.familymap.domain.Family;
import com.familymap.domain.FamilyRole;

import java.time.Instant;
import java.util.UUID;

/**
 * A circle as seen by one of its members: {@code myRole} is the caller's own role.
 * Only members ever receive this, so including the invite code is safe; any member can
 * invite family.
 */
public record FamilyResponse(UUID id, String name, String inviteCode, FamilyRole myRole,
                             long memberCount, Instant createdAt) {

    public static FamilyResponse of(Family family, FamilyRole myRole, long memberCount) {
        return new FamilyResponse(family.getId(), family.getName(), family.getInviteCode(),
                myRole, memberCount, family.getCreatedAt());
    }
}
