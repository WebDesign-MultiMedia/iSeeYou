package com.familymap.service;

import com.familymap.domain.Family;
import com.familymap.domain.FamilyMember;
import com.familymap.domain.FamilyRole;
import com.familymap.domain.Place;
import com.familymap.domain.User;
import com.familymap.dto.CircleEvent;
import com.familymap.dto.CreatePlaceRequest;
import com.familymap.exception.ForbiddenException;
import com.familymap.exception.InvalidRequestException;
import com.familymap.repository.FamilyMemberRepository;
import com.familymap.repository.PlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceServiceTest {

    private PlaceRepository places;
    private FamilyMemberRepository members;
    private ApplicationEventPublisher events;
    private PlaceService service;

    private final User ana = new User("Ana", "ana@example.com", "hash");
    private final User ben = new User("Ben", "ben@example.com", "hash");
    private final Family family = new Family("Smiths", "K7QM-3XPA", ana.getId());

    @BeforeEach
    void setUp() {
        places = mock(PlaceRepository.class);
        members = mock(FamilyMemberRepository.class);
        events = mock(ApplicationEventPublisher.class);
        when(places.save(any(Place.class))).thenAnswer(inv -> inv.getArgument(0));
        when(members.findByFamilyIdAndUserId(family.getId(), ana.getId()))
                .thenReturn(Optional.of(new FamilyMember(family, ana, FamilyRole.ADMIN)));
        when(members.findByFamilyIdAndUserId(family.getId(), ben.getId()))
                .thenReturn(Optional.of(new FamilyMember(family, ben, FamilyRole.MEMBER)));
        service = new PlaceService(places, new FamilyAccessService(members), events,
                Clock.fixed(Instant.parse("2026-11-19T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void anyMemberCanAddAPlaceAndTheCircleIsTold() {
        var res = service.create(family.getId(), ben.getId(), new CreatePlaceRequest("  School ", 1.0, 2.0, 150));

        assertThat(res.name()).isEqualTo("School");
        assertThat(res.createdBy()).isEqualTo(ben.getId());
        ArgumentCaptor<CircleEvent> event = ArgumentCaptor.forClass(CircleEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().type()).isEqualTo(CircleEvent.Type.PLACE_ADDED);
        assertThat(event.getValue().userName()).isEqualTo("Ben");
    }

    @Test
    void placesPerCircleAreCapped() {
        when(places.countByFamilyId(family.getId())).thenReturn((long) PlaceService.MAX_PLACES_PER_CIRCLE);

        assertThatThrownBy(() -> service.create(family.getId(), ben.getId(), new CreatePlaceRequest("X", 1.0, 2.0, 100)))
                .isInstanceOf(InvalidRequestException.class);
        verify(places, never()).save(any());
    }

    @Test
    void memberCannotRemoveSomeoneElsesPlace() {
        Place anas = new Place(family.getId(), "Home", 0, 0, 100, ana.getId());
        when(places.findByIdAndFamilyId(anas.getId(), family.getId())).thenReturn(Optional.of(anas));

        assertThatThrownBy(() -> service.delete(family.getId(), anas.getId(), ben.getId()))
                .isInstanceOf(ForbiddenException.class);
        verify(places, never()).delete(any());
    }

    @Test
    void creatorAndAdminCanRemove() {
        Place bens = new Place(family.getId(), "Gym", 0, 0, 100, ben.getId());
        when(places.findByIdAndFamilyId(bens.getId(), family.getId())).thenReturn(Optional.of(bens));

        service.delete(family.getId(), bens.getId(), ben.getId());
        service.delete(family.getId(), bens.getId(), ana.getId()); // admin

        verify(places, org.mockito.Mockito.times(2)).delete(bens);
    }
}
