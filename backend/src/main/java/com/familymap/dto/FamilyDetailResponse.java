package com.familymap.dto;

import com.familymap.domain.Family;
import com.familymap.domain.FamilyRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code GET /api/families/{id}}: the circle plus its full roster (step 3 adds latest locations). */
public record FamilyDetailResponse(UUID id, String name, String inviteCode, FamilyRole myRole,
                                   Instant createdAt, List<MemberResponse> members) {

    public static FamilyDetailResponse of(Family family, FamilyRole myRole, List<MemberResponse> members) {
        return new FamilyDetailResponse(family.getId(), family.getName(), family.getInviteCode(),
                myRole, family.getCreatedAt(), members);
    }
}
