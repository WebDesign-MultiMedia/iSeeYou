package com.familymap.push;

import com.familymap.domain.Family;
import com.familymap.domain.FamilyMember;
import com.familymap.domain.FamilyRole;
import com.familymap.domain.PushToken;
import com.familymap.domain.User;
import com.familymap.dto.CircleEvent;
import com.familymap.repository.FamilyMemberRepository;
import com.familymap.repository.FamilyRepository;
import com.familymap.repository.PushTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PushNotifierTest {

    private ExpoPushClient client;
    private PushTokenRepository tokens;
    private PushNotifier notifier;

    private final User mom = new User("Mom", "mom@example.com", "hash");
    private final User leo = new User("Leo", "leo@example.com", "hash");
    private final Family family = new Family("The Smiths", "K7QM-3XPA", mom.getId());
    private final PushToken momPhone = new PushToken("ExponentPushToken[mom]", mom.getId(), "ios", Instant.now());
    private final PushToken leoPhone = new PushToken("ExponentPushToken[leo]", leo.getId(), "android", Instant.now());

    @BeforeEach
    void setUp() {
        client = mock(ExpoPushClient.class);
        tokens = mock(PushTokenRepository.class);
        FamilyMemberRepository members = mock(FamilyMemberRepository.class);
        FamilyRepository families = mock(FamilyRepository.class);
        when(members.findByFamilyIdOrderByJoinedAtAsc(family.getId())).thenReturn(List.of(
                new FamilyMember(family, mom, FamilyRole.ADMIN), new FamilyMember(family, leo, FamilyRole.MEMBER)));
        when(families.findById(family.getId())).thenReturn(Optional.of(family));
        when(tokens.findByUserIdIn(List.of(mom.getId()))).thenReturn(List.of(momPhone));
        notifier = new PushNotifier(new PushProperties(true, null, null), client, members, families, tokens);
    }

    private CircleEvent leoArrivedHome() {
        return new CircleEvent(CircleEvent.Type.ARRIVED, family.getId(), UUID.randomUUID(), "Home",
                leo.getId(), "Leo", Instant.now());
    }

    @Test
    @SuppressWarnings("unchecked")
    void notifiesEveryoneButTheMover() {
        when(client.send(any())).thenReturn(List.of(new ExpoPushClient.Ticket("ok", null)));

        notifier.onCircleEvent(leoArrivedHome());

        ArgumentCaptor<List<PushMessage>> sent = ArgumentCaptor.forClass(List.class);
        verify(client).send(sent.capture());
        assertThat(sent.getValue()).hasSize(1);
        PushMessage m = sent.getValue().getFirst();
        assertThat(m.to()).isEqualTo(momPhone.getToken()).isNotEqualTo(leoPhone.getToken());
        assertThat(m.title()).isEqualTo("Leo arrived at Home");
        assertThat(m.body()).isEqualTo("The Smiths");
        assertThat(m.data()).containsEntry("familyId", family.getId().toString()).containsEntry("type", "ARRIVED");
    }

    @Test
    void uninstalledAppTokenIsDeleted() {
        when(client.send(any())).thenReturn(List.of(new ExpoPushClient.Ticket("error", "DeviceNotRegistered")));

        notifier.onCircleEvent(leoArrivedHome());

        verify(tokens).deleteById(momPhone.getToken());
    }

    @Test
    void placeChangesAreNotPushed() {
        notifier.onCircleEvent(new CircleEvent(CircleEvent.Type.PLACE_ADDED, family.getId(), UUID.randomUUID(),
                "Home", mom.getId(), "Mom", Instant.now()));
        verify(client, never()).send(any());
    }

    @Test
    void failuresAreSwallowed() {
        when(client.send(any())).thenThrow(new IllegalStateException("Expo is down"));
        notifier.onCircleEvent(leoArrivedHome()); // must not throw
    }

    @Test
    void departureTitle() {
        var left = new CircleEvent(CircleEvent.Type.LEFT, family.getId(), UUID.randomUUID(), "School",
                leo.getId(), "Leo", Instant.now());
        assertThat(PushNotifier.title(left)).isEqualTo("Leo left School");
    }
}
