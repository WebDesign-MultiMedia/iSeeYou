package com.familymap.realtime;

import com.familymap.dto.CircleEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/** Pushes arrivals, departures and place changes to {@code /topic/family/{id}/events}. */
@Component
public class CircleEventBroadcaster {

    private final SimpMessagingTemplate messaging;

    public CircleEventBroadcaster(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public static String topicFor(UUID familyId) {
        return "/topic/family/" + familyId + "/events";
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCircleEvent(CircleEvent event) {
        messaging.convertAndSend(topicFor(event.familyId()), event);
    }
}
