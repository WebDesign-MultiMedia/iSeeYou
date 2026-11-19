package com.familymap.service;

import com.familymap.domain.FamilyMember;
import com.familymap.dto.MemberResponse;
import com.familymap.repository.FamilyMemberRepository;
import com.familymap.repository.LocationRepository;
import com.familymap.repository.PlacePresenceRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** "Delete my location history": the user's right to erase, effective immediately. */
@Service
public class HistoryService {

    private final LocationRepository locations;
    private final PlacePresenceRepository presence;
    private final FamilyMemberRepository members;
    private final ApplicationEventPublisher events;

    public HistoryService(LocationRepository locations, PlacePresenceRepository presence,
                          FamilyMemberRepository members, ApplicationEventPublisher events) {
        this.locations = locations;
        this.presence = presence;
        this.members = members;
        this.events = events;
    }

    /**
     * Deletes every stored point and place presence for the user, and tells each of their
     * circles so the marker disappears right away. Sharing settings are untouched: if sharing
     * is on, the next point from the phone starts a fresh history.
     *
     * @return how many points were deleted
     */
    @Transactional
    public int deleteMyHistory(UUID userId) {
        int deleted = locations.deleteByUserId(userId);
        presence.deleteAllForUser(userId);
        for (FamilyMember m : members.findByUserIdOrderByJoinedAtAsc(userId)) {
            events.publishEvent(new MemberUpdatedEvent(m.getFamily().getId(), MemberResponse.from(m, null)));
        }
        return deleted;
    }
}
