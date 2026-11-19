package com.familymap.service;

import com.familymap.domain.Location;
import com.familymap.domain.Place;
import com.familymap.domain.PlacePresence;
import com.familymap.domain.User;
import com.familymap.dto.CircleEvent;
import com.familymap.repository.PlacePresenceRepository;
import com.familymap.repository.PlaceRepository;
import com.familymap.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeofenceServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-19T12:00:00Z");
    // Home at the origin, 100 m radius; 0.0001 deg latitude is ~11 m.
    private static final double INSIDE_LAT = 0.0005;   // ~56 m from home
    private static final double OUTSIDE_LAT = 0.002;   // ~222 m from home

    private PlaceRepository places;
    private PlacePresenceRepository presence;
    private ApplicationEventPublisher events;
    private GeofenceService service;

    private final User leo = new User("Leo", "leo@example.com", "hash");
    private final UUID familyId = UUID.randomUUID();
    private final Place home = new Place(familyId, "Home", 0, 0, 100, UUID.randomUUID());

    @BeforeEach
    void setUp() {
        places = mock(PlaceRepository.class);
        presence = mock(PlacePresenceRepository.class);
        events = mock(ApplicationEventPublisher.class);
        UserRepository users = mock(UserRepository.class);
        when(users.findById(leo.getId())).thenReturn(Optional.of(leo));
        when(places.findByFamilyIdIn(List.of(familyId))).thenReturn(List.of(home));
        service = new GeofenceService(places, presence, users, events);
    }

    private Location point(double lat, Instant at, Double accuracy) {
        return new Location(leo.getId(), lat, 0, accuracy, 80, at);
    }

    private void known(boolean inside, Instant at) {
        when(presence.findForUser(eq(leo.getId()), anyCollection()))
                .thenReturn(List.of(new PlacePresence(home.getId(), leo.getId(), inside, at)));
    }

    @Test
    void firstSightingIsASilentBaseline() {
        when(presence.findForUser(eq(leo.getId()), anyCollection())).thenReturn(List.of());

        service.evaluate(point(INSIDE_LAT, T0, 10.0), List.of(familyId));

        ArgumentCaptor<PlacePresence> saved = ArgumentCaptor.forClass(PlacePresence.class);
        verify(presence).save(saved.capture());
        assertThat(saved.getValue().isInside()).isTrue();
        verify(events, never()).publishEvent(any());
    }

    @Test
    void walkingInAnnouncesArrival() {
        known(false, T0);

        service.evaluate(point(INSIDE_LAT, T0.plusSeconds(60), 10.0), List.of(familyId));

        ArgumentCaptor<CircleEvent> event = ArgumentCaptor.forClass(CircleEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().type()).isEqualTo(CircleEvent.Type.ARRIVED);
        assertThat(event.getValue().userName()).isEqualTo("Leo");
        assertThat(event.getValue().placeName()).isEqualTo("Home");
        assertThat(event.getValue().familyId()).isEqualTo(familyId);
    }

    @Test
    void walkingOutAnnouncesDeparture() {
        known(true, T0);

        service.evaluate(point(OUTSIDE_LAT, T0.plusSeconds(60), 10.0), List.of(familyId));

        ArgumentCaptor<CircleEvent> event = ArgumentCaptor.forClass(CircleEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().type()).isEqualTo(CircleEvent.Type.LEFT);
    }

    @Test
    void stayingPutAnnouncesNothing() {
        known(true, T0);
        service.evaluate(point(INSIDE_LAT, T0.plusSeconds(60), 10.0), List.of(familyId));
        verify(events, never()).publishEvent(any());
    }

    @Test
    void lateUploadNeverRewindsState() {
        known(true, T0);
        service.evaluate(point(OUTSIDE_LAT, T0.minusSeconds(60), 10.0), List.of(familyId));
        verify(events, never()).publishEvent(any());
    }

    @Test
    void imprecisePointIsIgnored() {
        known(false, T0);
        service.evaluate(point(INSIDE_LAT, T0.plusSeconds(60), 500.0), List.of(familyId));
        verify(events, never()).publishEvent(any());
    }

    @Test
    void noPlacesNoWork() {
        when(places.findByFamilyIdIn(any())).thenReturn(List.of());
        service.evaluate(point(INSIDE_LAT, T0, 10.0), List.of(UUID.randomUUID()));
        verify(presence, never()).findForUser(any(), any());
    }
}
