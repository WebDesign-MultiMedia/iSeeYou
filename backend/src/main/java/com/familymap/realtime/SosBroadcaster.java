package com.familymap.realtime;

import com.familymap.service.SosChangedEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/** Pushes every SOS change to {@code /topic/family/{id}/sos} in each alerted circle. */
@Component
public class SosBroadcaster {

    private final SimpMessagingTemplate messaging;

    public SosBroadcaster(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public static String topicFor(UUID familyId) {
        return "/topic/family/" + familyId + "/sos";
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSosChanged(SosChangedEvent event) {
        for (UUID familyId : event.familyIds()) {
            messaging.convertAndSend(topicFor(familyId), event.alert());
        }
    }
}
