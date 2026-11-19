package com.familymap.service;

import com.familymap.domain.FamilyMember;
import com.familymap.domain.SosAlert;
import com.familymap.domain.User;
import com.familymap.dto.SosAlertResponse;
import com.familymap.dto.SosRequest;
import com.familymap.exception.InvalidRequestException;
import com.familymap.exception.ResourceNotFoundException;
import com.familymap.repository.FamilyMemberRepository;
import com.familymap.repository.SosAlertRepository;
import com.familymap.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * SOS: one press alerts every circle the sender belongs to, with their position.
 *
 * <p>Consent note: an SOS shares location <b>even with circles where sharing is paused</b>.
 * Pressing SOS is itself an explicit, in-the-moment decision to be found, and the app says so
 * on the button. Regular tracking stays paused.
 */
@Service
public class SosService {

    private final SosAlertRepository alerts;
    private final FamilyMemberRepository members;
    private final FamilyAccessService access;
    private final UserRepository users;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public SosService(SosAlertRepository alerts, FamilyMemberRepository members, FamilyAccessService access,
                      UserRepository users, ApplicationEventPublisher events, Clock clock) {
        this.alerts = alerts;
        this.members = members;
        this.access = access;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Raises an SOS, or, if the caller already has one open, updates its position and battery
     * (pressing again while moving keeps helpers current without spamming new alerts).
     */
    @Transactional
    public SosAlertResponse raise(UUID userId, SosRequest request) {
        Instant now = clock.instant();
        var existing = alerts.findFirstByUserIdAndResolvedAtIsNullOrderByCreatedAtDesc(userId);
        SosAlert alert;
        SosChangedEvent.Kind kind;
        if (existing.isPresent()) {
            alert = existing.get();
            kind = SosChangedEvent.Kind.UPDATED;
        } else {
            List<UUID> circles = members.findByUserIdOrderByJoinedAtAsc(userId).stream()
                    .map(m -> m.getFamily().getId()).toList();
            if (circles.isEmpty()) {
                throw new InvalidRequestException("Join a family circle first, so someone receives your SOS.");
            }
            alert = alerts.save(new SosAlert(userId, circles, now));
            kind = SosChangedEvent.Kind.RAISED;
        }
        alert.update(request.lat(), request.lng(), request.accuracy(), request.battery(), now);
        return publish(kind, alert);
    }

    /**
     * "I'm OK" from the sender, or "handled" from anyone in an alerted circle. Idempotent.
     * Anyone else gets 404, the same as for a circle they're not in.
     */
    @Transactional
    public SosAlertResponse resolve(UUID alertId, UUID userId) {
        SosAlert alert = alerts.findById(alertId)
                .filter(a -> canSee(a, userId))
                .orElseThrow(() -> new ResourceNotFoundException("SOS not found."));
        if (!alert.isActive()) {
            return toResponse(alert);
        }
        alert.resolve(userId, clock.instant());
        return publish(SosChangedEvent.Kind.RESOLVED, alert);
    }

    /** Open SOS alerts in a circle, newest first. Members only. */
    @Transactional(readOnly = true)
    public List<SosAlertResponse> activeIn(UUID familyId, UUID userId) {
        access.requireMember(familyId, userId);
        return alerts.findActiveInFamily(familyId).stream().map(this::toResponse).toList();
    }

    private boolean canSee(SosAlert alert, UUID userId) {
        if (alert.getUserId().equals(userId)) {
            return true;
        }
        Set<UUID> mine = members.findByUserIdOrderByJoinedAtAsc(userId).stream()
                .map(FamilyMember::getFamily).map(f -> f.getId()).collect(Collectors.toSet());
        return alert.getFamilyIds().stream().anyMatch(mine::contains);
    }

    private SosAlertResponse publish(SosChangedEvent.Kind kind, SosAlert alert) {
        SosAlertResponse response = toResponse(alert);
        events.publishEvent(new SosChangedEvent(kind, response, alert.getFamilyIds()));
        return response;
    }

    private SosAlertResponse toResponse(SosAlert a) {
        return SosAlertResponse.from(a, nameOf(a.getUserId()), a.getResolvedBy() == null ? null : nameOf(a.getResolvedBy()));
    }

    private String nameOf(UUID userId) {
        return users.findById(userId).map(User::getName).orElse("Someone");
    }
}
