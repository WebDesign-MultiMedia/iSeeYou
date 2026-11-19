package com.familymap.push;

import com.familymap.domain.FamilyMember;
import com.familymap.domain.PushToken;
import com.familymap.dto.CircleEvent;
import com.familymap.repository.FamilyMemberRepository;
import com.familymap.repository.FamilyRepository;
import com.familymap.repository.PushTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns arrivals and departures into push notifications for the rest of the circle (never
 * the person who moved). Runs after commit and off the request thread, so a slow or failing
 * push service never delays or fails a location upload.
 */
@Component
public class PushNotifier {

    private static final Logger log = LoggerFactory.getLogger(PushNotifier.class);
    /** Expo accepts up to 100 messages per request. */
    static final int BATCH_SIZE = 100;
    static final String ANDROID_CHANNEL = "arrivals";

    private final PushProperties properties;
    private final ExpoPushClient client;
    private final FamilyMemberRepository members;
    private final FamilyRepository families;
    private final PushTokenRepository tokens;

    public PushNotifier(PushProperties properties, ExpoPushClient client, FamilyMemberRepository members,
                        FamilyRepository families, PushTokenRepository tokens) {
        this.properties = properties;
        this.client = client;
        this.members = members;
        this.families = families;
        this.tokens = tokens;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCircleEvent(CircleEvent event) {
        if (!properties.enabled() || !event.isMovement()) {
            return;
        }
        try {
            notify(event);
        } catch (RuntimeException e) {
            // Best effort: the in-app live event already went out; never let push break anything.
            log.warn("Push for {} in circle {} failed: {}", event.type(), event.familyId(), e.toString());
        }
    }

    void notify(CircleEvent event) {
        List<UUID> recipients = members.findByFamilyIdOrderByJoinedAtAsc(event.familyId()).stream()
                .map(FamilyMember::getUser)
                .map(u -> u.getId())
                .filter(id -> !id.equals(event.userId()))
                .toList();
        if (recipients.isEmpty()) {
            return;
        }
        List<PushToken> targets = tokens.findByUserIdIn(recipients);
        if (targets.isEmpty()) {
            return;
        }
        String circleName = families.findById(event.familyId()).map(f -> f.getName()).orElse("Your circle");
        String title = title(event);
        Map<String, String> data = Map.of(
                "familyId", event.familyId().toString(),
                "type", event.type().name(),
                "placeId", event.placeId().toString());

        for (int from = 0; from < targets.size(); from += BATCH_SIZE) {
            List<PushToken> batch = targets.subList(from, Math.min(from + BATCH_SIZE, targets.size()));
            List<PushMessage> messages = new ArrayList<>(batch.size());
            for (PushToken t : batch) {
                messages.add(new PushMessage(t.getToken(), title, circleName, data, "default", ANDROID_CHANNEL));
            }
            List<ExpoPushClient.Ticket> tickets = client.send(messages);
            for (int i = 0; i < tickets.size() && i < batch.size(); i++) {
                if (tickets.get(i).deviceGone()) {
                    tokens.deleteById(batch.get(i).getToken()); // app uninstalled: stop sending
                }
            }
        }
    }

    static String title(CircleEvent e) {
        return e.type() == CircleEvent.Type.ARRIVED
                ? e.userName() + " arrived at " + e.placeName()
                : e.userName() + " left " + e.placeName();
    }
}
