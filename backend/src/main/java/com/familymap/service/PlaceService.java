package com.familymap.service;

import com.familymap.domain.FamilyMember;
import com.familymap.domain.Place;
import com.familymap.dto.CircleEvent;
import com.familymap.dto.CreatePlaceRequest;
import com.familymap.dto.PlaceResponse;
import com.familymap.exception.ForbiddenException;
import com.familymap.exception.InvalidRequestException;
import com.familymap.exception.ResourceNotFoundException;
import com.familymap.repository.PlaceRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Saved places in a circle. Any member can add one; only whoever added it, or an admin,
 * can remove it.
 */
@Service
public class PlaceService {

    /** Keeps the per-point geofence check cheap and the map readable. */
    static final int MAX_PLACES_PER_CIRCLE = 20;

    private final PlaceRepository places;
    private final FamilyAccessService access;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public PlaceService(PlaceRepository places, FamilyAccessService access, ApplicationEventPublisher events,
                        Clock clock) {
        this.places = places;
        this.access = access;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PlaceResponse> list(UUID familyId, UUID userId) {
        access.requireMember(familyId, userId);
        return places.findByFamilyIdOrderByCreatedAtAsc(familyId).stream().map(PlaceResponse::from).toList();
    }

    @Transactional
    public PlaceResponse create(UUID familyId, UUID userId, CreatePlaceRequest request) {
        FamilyMember me = access.requireMember(familyId, userId);
        if (places.countByFamilyId(familyId) >= MAX_PLACES_PER_CIRCLE) {
            throw new InvalidRequestException("A circle can have at most " + MAX_PLACES_PER_CIRCLE + " places.");
        }
        Place place = places.save(new Place(familyId, request.name().trim(), request.lat(), request.lng(),
                request.radiusM(), userId));
        events.publishEvent(new CircleEvent(CircleEvent.Type.PLACE_ADDED, familyId, place.getId(), place.getName(),
                userId, me.getUser().getName(), clock.instant()));
        return PlaceResponse.from(place);
    }

    @Transactional
    public void delete(UUID familyId, UUID placeId, UUID userId) {
        FamilyMember me = access.requireMember(familyId, userId);
        Place place = places.findByIdAndFamilyId(placeId, familyId)
                .orElseThrow(() -> new ResourceNotFoundException("Place not found."));
        if (!me.isAdmin() && !place.getCreatedBy().equals(userId)) {
            throw new ForbiddenException("Only whoever added this place, or a circle admin, can remove it.");
        }
        places.delete(place); // presence rows go with it (ON DELETE CASCADE)
        events.publishEvent(new CircleEvent(CircleEvent.Type.PLACE_REMOVED, familyId, place.getId(), place.getName(),
                userId, me.getUser().getName(), clock.instant()));
    }
}
