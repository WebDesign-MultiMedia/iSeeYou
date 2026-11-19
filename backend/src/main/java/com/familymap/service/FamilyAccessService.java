package com.familymap.service;

import com.familymap.domain.FamilyMember;
import com.familymap.exception.ResourceNotFoundException;
import com.familymap.repository.FamilyMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The one place that decides whether a user may touch a circle's data. Every service that
 * reads or writes circle-scoped data (members now; locations, places and SOS later) calls
 * this first.
 *
 * <p>Non-members get <b>404, not 403</b>: a stranger can't even confirm that a circle id exists.
 */
@Service
public class FamilyAccessService {

    static final String NOT_FOUND = "Circle not found.";

    private final FamilyMemberRepository members;

    public FamilyAccessService(FamilyMemberRepository members) {
        this.members = members;
    }

    /** Returns the caller's membership (with its family loaded), or throws 404. */
    @Transactional(readOnly = true)
    public FamilyMember requireMember(UUID familyId, UUID userId) {
        return members.findByFamilyIdAndUserId(familyId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }
}
