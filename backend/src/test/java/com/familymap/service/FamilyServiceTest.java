package com.familymap.service;

import com.familymap.domain.Family;
import com.familymap.domain.FamilyMember;
import com.familymap.domain.FamilyRole;
import com.familymap.domain.User;
import com.familymap.dto.FamilyResponse;
import com.familymap.dto.LocationResponse;
import com.familymap.dto.MemberResponse;
import com.familymap.exception.ResourceNotFoundException;
import com.familymap.repository.FamilyMemberRepository;
import com.familymap.repository.FamilyRepository;
import com.familymap.repository.PlacePresenceRepository;
import com.familymap.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FamilyServiceTest {

    private FamilyRepository families;
    private FamilyMemberRepository members;
    private UserRepository users;
    private InviteCodeGenerator codes;
    private LocationService locations;
    private ApplicationEventPublisher events;
    private PlacePresenceRepository presence;
    private FamilyService service;

    private final User ana = new User("Ana", "ana@example.com", "hash");
    private final User ben = new User("Ben", "ben@example.com", "hash");

    @BeforeEach
    void setUp() {
        families = mock(FamilyRepository.class);
        members = mock(FamilyMemberRepository.class);
        users = mock(UserRepository.class);
        codes = mock(InviteCodeGenerator.class);
        locations = mock(LocationService.class);
        events = mock(ApplicationEventPublisher.class);
        presence = mock(PlacePresenceRepository.class);
        when(families.save(any(Family.class))).thenAnswer(inv -> inv.getArgument(0));
        when(members.save(any(FamilyMember.class))).thenAnswer(inv -> inv.getArgument(0));
        when(users.findById(ana.getId())).thenReturn(Optional.of(ana));
        when(users.findById(ben.getId())).thenReturn(Optional.of(ben));
        service = new FamilyService(families, members, users, new FamilyAccessService(members), codes,
                locations, events, presence);
    }

    @Test
    void createMakesCreatorAdmin() {
        when(codes.generate()).thenReturn("AAAA-BBBB");

        FamilyResponse res = service.create(ana.getId(), "  The Smiths ");

        ArgumentCaptor<FamilyMember> saved = ArgumentCaptor.forClass(FamilyMember.class);
        verify(members).save(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(ana);
        assertThat(saved.getValue().getRole()).isEqualTo(FamilyRole.ADMIN);
        assertThat(saved.getValue().isSharingEnabled()).isTrue();
        assertThat(res.name()).isEqualTo("The Smiths");
        assertThat(res.inviteCode()).isEqualTo("AAAA-BBBB");
        assertThat(res.myRole()).isEqualTo(FamilyRole.ADMIN);
        assertThat(res.memberCount()).isEqualTo(1);
    }

    @Test
    void createRetriesWhenInviteCodeCollides() {
        when(codes.generate()).thenReturn("TAKE-NNNN", "FREE-CODE");
        when(families.existsByInviteCode("TAKE-NNNN")).thenReturn(true);

        assertThat(service.create(ana.getId(), "Smiths").inviteCode()).isEqualTo("FREE-CODE");
    }

    @Test
    void joinAddsMemberWithNormalizedCode() {
        Family family = new Family("Smiths", "K7QM-3XPA", ana.getId());
        when(families.findByInviteCode("K7QM-3XPA")).thenReturn(Optional.of(family));
        when(members.findByFamilyIdAndUserId(family.getId(), ben.getId())).thenReturn(Optional.empty());
        when(members.countByFamilyId(family.getId())).thenReturn(2L);

        FamilyResponse res = service.join(ben.getId(), "k7qm 3xpa");

        ArgumentCaptor<FamilyMember> saved = ArgumentCaptor.forClass(FamilyMember.class);
        verify(members).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(FamilyRole.MEMBER);
        assertThat(res.myRole()).isEqualTo(FamilyRole.MEMBER);
        assertThat(res.memberCount()).isEqualTo(2);
    }

    @Test
    void joinIsIdempotentForExistingMember() {
        Family family = new Family("Smiths", "K7QM-3XPA", ana.getId());
        when(families.findByInviteCode("K7QM-3XPA")).thenReturn(Optional.of(family));
        when(members.findByFamilyIdAndUserId(family.getId(), ana.getId()))
                .thenReturn(Optional.of(new FamilyMember(family, ana, FamilyRole.ADMIN)));

        FamilyResponse res = service.join(ana.getId(), "K7QM-3XPA");

        verify(members, never()).save(any());
        assertThat(res.myRole()).isEqualTo(FamilyRole.ADMIN); // not downgraded
    }

    @Test
    void joinWithUnknownCodeIs404() {
        when(families.findByInviteCode(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.join(ben.getId(), "NOPE-NOPE"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void nonMemberCannotReadCircle() {
        UUID familyId = UUID.randomUUID();
        when(members.findByFamilyIdAndUserId(familyId, ben.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(familyId, ben.getId()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(FamilyAccessService.NOT_FOUND);
        assertThatThrownBy(() -> service.listMembers(familyId, ben.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(members, never()).findByFamilyIdOrderByJoinedAtAsc(any());
    }

    @Test
    void lastAdminLeavingPromotesLongestStandingMember() {
        Family family = new Family("Smiths", "K7QM-3XPA", ana.getId());
        FamilyMember anaAdmin = new FamilyMember(family, ana, FamilyRole.ADMIN);
        FamilyMember benMember = new FamilyMember(family, ben, FamilyRole.MEMBER);
        when(members.findByFamilyIdAndUserId(family.getId(), ana.getId())).thenReturn(Optional.of(anaAdmin));
        when(members.countByFamilyId(family.getId())).thenReturn(2L);
        when(members.countByFamilyIdAndRole(family.getId(), FamilyRole.ADMIN)).thenReturn(1L);
        when(members.findFirstByFamilyIdAndUserIdNotOrderByJoinedAtAsc(family.getId(), ana.getId()))
                .thenReturn(Optional.of(benMember));

        service.leave(family.getId(), ana.getId());

        assertThat(benMember.getRole()).isEqualTo(FamilyRole.ADMIN);
        verify(members).delete(anaAdmin);
        verify(families, never()).delete(any());
    }

    @Test
    void memberLeavingDoesNotChangeAdmins() {
        Family family = new Family("Smiths", "K7QM-3XPA", ana.getId());
        FamilyMember benMember = new FamilyMember(family, ben, FamilyRole.MEMBER);
        when(members.findByFamilyIdAndUserId(family.getId(), ben.getId())).thenReturn(Optional.of(benMember));
        when(members.countByFamilyId(family.getId())).thenReturn(2L);

        service.leave(family.getId(), ben.getId());

        verify(members).delete(benMember);
        verify(members, never()).findFirstByFamilyIdAndUserIdNotOrderByJoinedAtAsc(any(), any());
    }

    @Test
    void lastMemberLeavingDeletesCircle() {
        Family family = new Family("Smiths", "K7QM-3XPA", ana.getId());
        FamilyMember anaAdmin = new FamilyMember(family, ana, FamilyRole.ADMIN);
        when(members.findByFamilyIdAndUserId(family.getId(), ana.getId())).thenReturn(Optional.of(anaAdmin));
        when(members.countByFamilyId(family.getId())).thenReturn(1L);

        service.leave(family.getId(), ana.getId());

        verify(members).delete(anaAdmin);
        verify(families).delete(family);
    }

    // ---------------------------------------------------------------- pause / resume sharing

    @Test
    void pausingHidesLocationAndTellsTheCircle() {
        Family family = new Family("Smiths", "K7QM-3XPA", ana.getId());
        FamilyMember benMember = new FamilyMember(family, ben, FamilyRole.MEMBER);
        when(members.findByFamilyIdAndUserId(family.getId(), ben.getId())).thenReturn(Optional.of(benMember));

        MemberResponse res = service.setSharing(family.getId(), ben.getId(), false);

        assertThat(benMember.isSharingEnabled()).isFalse();
        assertThat(res.sharingEnabled()).isFalse();
        assertThat(res.lastLocation()).isNull();
        verify(locations, never()).latestFor(any());
        ArgumentCaptor<MemberSharingChangedEvent> event = ArgumentCaptor.forClass(MemberSharingChangedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().familyId()).isEqualTo(family.getId());
        assertThat(event.getValue().member()).isEqualTo(res);
        verify(presence).deleteForUserInFamily(ben.getId(), family.getId()); // no "left Home" after resuming
    }

    @Test
    void resumingShowsLatestPointAgain() {
        Family family = new Family("Smiths", "K7QM-3XPA", ana.getId());
        FamilyMember benMember = new FamilyMember(family, ben, FamilyRole.MEMBER);
        benMember.setSharingEnabled(false);
        when(members.findByFamilyIdAndUserId(family.getId(), ben.getId())).thenReturn(Optional.of(benMember));
        LocationResponse last = new LocationResponse(ben.getId(), 1, 2, null, 50, Instant.now());
        when(locations.latestFor(List.of(ben.getId()))).thenReturn(Map.of(ben.getId(), last));

        MemberResponse res = service.setSharing(family.getId(), ben.getId(), true);

        assertThat(res.sharingEnabled()).isTrue();
        assertThat(res.lastLocation()).isEqualTo(last);
        verify(events).publishEvent(any(MemberSharingChangedEvent.class));
    }

    @Test
    void settingSameValueIsANoOpBroadcast() {
        Family family = new Family("Smiths", "K7QM-3XPA", ana.getId());
        FamilyMember benMember = new FamilyMember(family, ben, FamilyRole.MEMBER);
        when(members.findByFamilyIdAndUserId(family.getId(), ben.getId())).thenReturn(Optional.of(benMember));

        service.setSharing(family.getId(), ben.getId(), true);

        verify(events, never()).publishEvent(any());
    }

    @Test
    void outsiderCannotChangeSharing() {
        UUID familyId = UUID.randomUUID();
        when(members.findByFamilyIdAndUserId(familyId, ben.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setSharing(familyId, ben.getId(), false))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(events, never()).publishEvent(any());
    }
}
