package com.familymap.realtime;

import com.familymap.service.LocationRecordedEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Pushes each stored point to {@code /topic/family/{id}/locations} for every circle it was
 * shared with. Runs after the transaction commits, so subscribers never see a point that
 * was rolled back.
 */
@Component
public class LocationBroadcaster {

    private final SimpMessagingTemplate messaging;

    public LocationBroadcaster(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public static String topicFor(UUID familyId) {
        return "/topic/family/" + familyId + "/locations";
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLocationRecorded(LocationRecordedEvent event) {
        for (UUID familyId : event.familyIds()) {
            messaging.convertAndSend(topicFor(familyId), event.location());
        }
    }
}
