package com.familymap.service;

import com.familymap.domain.Family;
import com.familymap.domain.FamilyMember;
import com.familymap.domain.FamilyRole;
import com.familymap.domain.Location;
import com.familymap.domain.User;
import com.familymap.dto.LocationRequest;
import com.familymap.dto.LocationResponse;
import com.familymap.dto.RecordLocationResponse;
import com.familymap.exception.InvalidRequestException;
import com.familymap.repository.FamilyMemberRepository;
import com.familymap.repository.LocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LocationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");

    private LocationRepository locations;
    private FamilyMemberRepository members;
    private ApplicationEventPublisher events;
    private LocationService service;

    private final User leo = new User("Leo", "leo@example.com", "hash");
    private final Family home = new Family("Home", "AAAA-AAAA", leo.getId());
    private final Family grandma = new Family("Grandma's", "BBBB-BBBB", leo.getId());

    @BeforeEach
    void setUp() {
        locations = mock(LocationRepository.class);
        members = mock(FamilyMemberRepository.class);
        events = mock(ApplicationEventPublisher.class);
        when(locations.save(any(Location.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new LocationService(locations, members, events, mock(GeofenceService.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static LocationRequest at(Instant recordedAt) {
        return new LocationRequest(40.4168, -3.7038, 12.5, 80, recordedAt);
    }

    @Test
    void sharesOnlyWithCirclesWhereSharingIsOn() {
        FamilyMember paused = new FamilyMember(grandma, leo, FamilyRole.MEMBER);
        paused.setSharingEnabled(false);
        when(members.findByUserIdOrderByJoinedAtAsc(leo.getId()))
                .thenReturn(List.of(new FamilyMember(home, leo, FamilyRole.MEMBER), paused));

        RecordLocationResponse res = service.record(leo.getId(), at(null));

        assertThat(res.shared()).isTrue();
        assertThat(res.sharedWith()).containsExactly(home.getId());
        assertThat(res.location().recordedAt()).isEqualTo(NOW); // defaulted to server time
        ArgumentCaptor<LocationRecordedEvent> event = ArgumentCaptor.forClass(LocationRecordedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().familyIds()).containsExactly(home.getId());
        assertThat(event.getValue().location().battery()).isEqualTo(80);
    }

    @Test
    void pausedEverywhereMeansNothingIsStoredOrBroadcast() {
        FamilyMember paused = new FamilyMember(home, leo, FamilyRole.MEMBER);
        paused.setSharingEnabled(false);
        when(members.findByUserIdOrderByJoinedAtAsc(leo.getId())).thenReturn(List.of(paused));

        RecordLocationResponse res = service.record(leo.getId(), at(null));

        assertThat(res.shared()).isFalse();
        assertThat(res.location()).isNull();
        verify(locations, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void userInNoCircleIsNotStored() {
        when(members.findByUserIdOrderByJoinedAtAsc(leo.getId())).thenReturn(List.of());

        assertThat(service.record(leo.getId(), at(null)).shared()).isFalse();
        verify(locations, never()).save(any());
    }

    @Test
    void keepsDeviceTimestampWithinAllowedWindow() {
        when(members.findByUserIdOrderByJoinedAtAsc(leo.getId()))
                .thenReturn(List.of(new FamilyMember(home, leo, FamilyRole.MEMBER)));
        Instant anHourAgo = NOW.minus(Duration.ofHours(1));

        assertThat(service.record(leo.getId(), at(anHourAgo)).location().recordedAt()).isEqualTo(anHourAgo);
    }

    @Test
    void rejectsFutureAndExpiredTimestamps() {
        when(members.findByUserIdOrderByJoinedAtAsc(leo.getId()))
                .thenReturn(List.of(new FamilyMember(home, leo, FamilyRole.MEMBER)));

        assertThatThrownBy(() -> service.record(leo.getId(), at(NOW.plus(Duration.ofMinutes(10)))))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.record(leo.getId(), at(NOW.minus(Duration.ofDays(8)))))
                .isInstanceOf(InvalidRequestException.class);
        verify(locations, never()).save(any());
    }

    @Test
    void latestForBreaksTimestampTiesByNewestRow() {
        Location older = new Location(leo.getId(), 1, 1, null, null, NOW);
        Location newer = new Location(leo.getId(), 2, 2, null, null, NOW);
        ReflectionTestUtils.setField(older, "id", 1L);
        ReflectionTestUtils.setField(newer, "id", 2L);
        when(locations.findLatestForUsers(List.of(leo.getId()))).thenReturn(List.of(newer, older));

        Map<java.util.UUID, LocationResponse> latest = service.latestFor(List.of(leo.getId()));

        assertThat(latest.get(leo.getId()).lat()).isEqualTo(2);
    }

    @Test
    void latestForNobodySkipsTheQuery() {
        assertThat(service.latestFor(List.of())).isEmpty();
        verifyNoInteractions(locations);
    }
}
