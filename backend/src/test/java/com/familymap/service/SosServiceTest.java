package com.familymap.service;

import com.familymap.domain.Family;
import com.familymap.domain.FamilyMember;
import com.familymap.domain.FamilyRole;
import com.familymap.domain.SosAlert;
import com.familymap.domain.User;
import com.familymap.dto.SosAlertResponse;
import com.familymap.dto.SosRequest;
import com.familymap.exception.InvalidRequestException;
import com.familymap.exception.ResourceNotFoundException;
import com.familymap.repository.FamilyMemberRepository;
import com.familymap.repository.SosAlertRepository;
import com.familymap.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SosServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");

    private SosAlertRepository alerts;
    private FamilyMemberRepository members;
    private ApplicationEventPublisher events;
    private SosService service;

    private final User leo = new User("Leo", "leo@example.com", "hash");
    private final User mom = new User("Mom", "mom@example.com", "hash");
    private final User eve = new User("Eve", "eve@example.com", "hash");
    private final Family home = new Family("Home", "AAAA-BBBB", mom.getId());
    private final Family school = new Family("School friends", "CCCC-DDDD", leo.getId());

    @BeforeEach
    void setUp() {
        alerts = mock(SosAlertRepository.class);
        members = mock(FamilyMemberRepository.class);
        events = mock(ApplicationEventPublisher.class);
        UserRepository users = mock(UserRepository.class);
        for (User u : List.of(leo, mom, eve)) {
            when(users.findById(u.getId())).thenReturn(Optional.of(u));
        }
        when(alerts.save(any(SosAlert.class))).thenAnswer(inv -> inv.getArgument(0));
        when(members.findByUserIdOrderByJoinedAtAsc(leo.getId())).thenReturn(List.of(
                new FamilyMember(home, leo, FamilyRole.MEMBER), new FamilyMember(school, leo, FamilyRole.ADMIN)));
        when(members.findByUserIdOrderByJoinedAtAsc(mom.getId()))
                .thenReturn(List.of(new FamilyMember(home, mom, FamilyRole.ADMIN)));
        when(members.findByUserIdOrderByJoinedAtAsc(eve.getId())).thenReturn(List.of());
        service = new SosService(alerts, members, new FamilyAccessService(members), users, events,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void raisingAlertsEveryCircleWithLocation() {
        SosAlertResponse res = service.raise(leo.getId(), new SosRequest(40.0, -3.0, 12.0, 41));

        assertThat(res.active()).isTrue();
        assertThat(res.userName()).isEqualTo("Leo");
        assertThat(res.lat()).isEqualTo(40.0);
        assertThat(res.battery()).isEqualTo(41);
        ArgumentCaptor<SosChangedEvent> event = ArgumentCaptor.forClass(SosChangedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().kind()).isEqualTo(SosChangedEvent.Kind.RAISED);
        assertThat(event.getValue().familyIds()).containsExactlyInAnyOrder(home.getId(), school.getId());
    }

    @Test
    void sosWithoutLocationStillGoesOut() {
        SosAlertResponse res = service.raise(leo.getId(), new SosRequest(null, null, null, null));
        assertThat(res.lat()).isNull();
        verify(events).publishEvent(any(SosChangedEvent.class));
    }

    @Test
    void pressingAgainUpdatesTheOpenAlert() {
        SosAlert open = new SosAlert(leo.getId(), Set.of(home.getId()), NOW.minusSeconds(60));
        when(alerts.findFirstByUserIdAndResolvedAtIsNullOrderByCreatedAtDesc(leo.getId())).thenReturn(Optional.of(open));

        SosAlertResponse res = service.raise(leo.getId(), new SosRequest(41.0, -3.0, 5.0, 30));

        assertThat(res.id()).isEqualTo(open.getId());
        assertThat(res.lat()).isEqualTo(41.0);
        verify(alerts, never()).save(any());
        ArgumentCaptor<SosChangedEvent> event = ArgumentCaptor.forClass(SosChangedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().kind()).isEqualTo(SosChangedEvent.Kind.UPDATED);
    }

    @Test
    void cannotRaiseWithoutACircle() {
        assertThatThrownBy(() -> service.raise(eve.getId(), new SosRequest(1.0, 1.0, null, null)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void aCircleMemberCanMarkItHandled() {
        SosAlert open = new SosAlert(leo.getId(), Set.of(home.getId()), NOW.minusSeconds(60));
        when(alerts.findById(open.getId())).thenReturn(Optional.of(open));

        SosAlertResponse res = service.resolve(open.getId(), mom.getId());

        assertThat(res.active()).isFalse();
        assertThat(res.resolvedByName()).isEqualTo("Mom");
        verify(events).publishEvent(any(SosChangedEvent.class));
    }

    @Test
    void outsidersGet404() {
        SosAlert open = new SosAlert(leo.getId(), Set.of(home.getId()), NOW);
        when(alerts.findById(open.getId())).thenReturn(Optional.of(open));

        assertThatThrownBy(() -> service.resolve(open.getId(), eve.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resolvingTwiceIsANoOp() {
        SosAlert done = new SosAlert(leo.getId(), Set.of(home.getId()), NOW);
        done.resolve(leo.getId(), NOW);
        when(alerts.findById(done.getId())).thenReturn(Optional.of(done));

        service.resolve(done.getId(), mom.getId());

        verify(events, never()).publishEvent(any());
    }
}
