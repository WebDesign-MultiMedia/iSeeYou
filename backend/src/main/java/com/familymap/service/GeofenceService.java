package com.familymap.service;

import com.familymap.domain.Location;
import com.familymap.domain.Place;
import com.familymap.domain.PlacePresence;
import com.familymap.dto.CircleEvent;
import com.familymap.repository.PlacePresenceRepository;
import com.familymap.repository.PlaceRepository;
import com.familymap.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Server-side geofencing: every stored point is checked against the saved places of the
 * circles it was shared with, and a transition (outside to inside, or back) becomes an
 * {@code ARRIVED} / {@code LEFT} event.
 *
 * <p>Why on the server and not with the phone's geofencing API: it costs the phone no extra
 * battery or permissions, works identically on iOS, Android and Expo Go, and it can only use
 * points the member actually agreed to share, with those circles. A paused circle never
 * gets arrival alerts.
 */
@Service
public class GeofenceService {

    private final PlaceRepository places;
    private final PlacePresenceRepository presence;
    private final UserRepository users;
    private final ApplicationEventPublisher events;

    public GeofenceService(PlaceRepository places, PlacePresenceRepository presence, UserRepository users,
                           ApplicationEventPublisher events) {
        this.places = places;
        this.presence = presence;
        this.users = users;
        this.events = events;
    }

    /** Runs inside the caller's transaction, so events only go out if the point is stored. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void evaluate(Location point, List<UUID> sharedWithFamilyIds) {
        List<Place> candidates = places.findByFamilyIdIn(sharedWithFamilyIds);
        if (candidates.isEmpty()) {
            return;
        }
        UUID userId = point.getUserId();
        Map<UUID, PlacePresence> known = presence
                .findForUser(userId, candidates.stream().map(Place::getId).toList()).stream()
                .collect(Collectors.toMap(p -> p.getId().placeId(), Function.identity()));

        String userName = null;
        for (Place place : candidates) {
            PlacePresence current = known.get(place.getId());
            // A late upload (sent after an offline stretch) must not rewind a newer state.
            if (current != null && !point.getRecordedAt().isAfter(current.getChangedAt())) {
                continue;
            }
            double distance = Geofence.distanceMeters(point.getLat(), point.getLng(), place.getLat(), place.getLng());
            Optional<Boolean> decided = Geofence.decide(current == null ? null : current.isInside(),
                    distance, place.getRadiusM(), point.getAccuracy());
            if (decided.isEmpty()) {
                continue;
            }
            boolean inside = decided.get();

            if (current == null) {
                // First sighting is a baseline, not news: no "arrived at Home" just for turning sharing on.
                presence.save(new PlacePresence(place.getId(), userId, inside, point.getRecordedAt()));
                continue;
            }
            if (current.isInside() == inside) {
                continue;
            }
            current.set(inside, point.getRecordedAt());
            if (userName == null) {
                userName = users.findById(userId).map(u -> u.getName()).orElse("Someone");
            }
            events.publishEvent(new CircleEvent(inside ? CircleEvent.Type.ARRIVED : CircleEvent.Type.LEFT,
                    place.getFamilyId(), place.getId(), place.getName(), userId, userName, point.getRecordedAt()));
        }
    }
}
