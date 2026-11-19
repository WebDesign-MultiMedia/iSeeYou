package com.familymap.service;

import com.familymap.domain.FamilyMember;
import com.familymap.domain.Location;
import com.familymap.dto.LocationRequest;
import com.familymap.dto.LocationResponse;
import com.familymap.dto.RecordLocationResponse;
import com.familymap.exception.InvalidRequestException;
import com.familymap.repository.FamilyMemberRepository;
import com.familymap.repository.LocationRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

/**
 * Stores location points and decides who may see them.
 *
 * <p>Consent rule: a point is only shared with circles where the member has sharing turned
 * on. If sharing is paused everywhere, the point is dropped and never stored.
 */
@Service
public class LocationService {

    /** Some clock drift between phone and server is normal; more than this is a bad timestamp. */
    static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(2);
    /** Points older than the retention window would only be purged (step 7), so reject them. */
    static final Duration MAX_AGE = Duration.ofDays(7);

    private final LocationRepository locations;
    private final FamilyMemberRepository members;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public LocationService(LocationRepository locations, FamilyMemberRepository members,
                           ApplicationEventPublisher events, Clock clock) {
        this.locations = locations;
        this.members = members;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public RecordLocationResponse record(UUID userId, LocationRequest request) {
        Instant recordedAt = resolveRecordedAt(request.recordedAt()); // validate before anything else

        List<UUID> sharingWith = members.findByUserIdOrderByJoinedAtAsc(userId).stream()
                .filter(FamilyMember::isSharingEnabled)
                .map(m -> m.getFamily().getId())
                .toList();
        if (sharingWith.isEmpty()) {
            return RecordLocationResponse.notShared();
        }

        Location saved = locations.save(new Location(userId, request.lat(), request.lng(),
                request.accuracy(), request.battery(), recordedAt));

        LocationResponse location = LocationResponse.from(saved);
        events.publishEvent(new LocationRecordedEvent(location, sharingWith));
        return new RecordLocationResponse(true, sharingWith, location);
    }

    /** Latest point per user, keyed by user id. Users with no points are absent from the map. */
    @Transactional(readOnly = true)
    public Map<UUID, LocationResponse> latestFor(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        // On a timestamp tie, keep the row inserted last.
        BinaryOperator<Location> newest = BinaryOperator.maxBy(Comparator.comparing(Location::getId));
        return locations.findLatestForUsers(userIds).stream()
                .collect(Collectors.toMap(Location::getUserId, l -> l, newest))
                .values().stream()
                .collect(Collectors.toMap(Location::getUserId, LocationResponse::from));
    }

    private Instant resolveRecordedAt(Instant recordedAt) {
        Instant now = clock.instant();
        if (recordedAt == null) {
            return now;
        }
        if (recordedAt.isAfter(now.plus(MAX_CLOCK_SKEW))) {
            throw new InvalidRequestException("recordedAt is in the future.");
        }
        if (recordedAt.isBefore(now.minus(MAX_AGE))) {
            throw new InvalidRequestException("recordedAt is older than the 7-day retention window.");
        }
        return recordedAt;
    }
}
