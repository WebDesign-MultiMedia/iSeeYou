package com.familymap.realtime;

import com.familymap.service.MemberUpdatedEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Pushes roster changes (a member paused or resumed sharing) to
 * {@code /topic/family/{id}/members}. The payload is the member's full {@code MemberResponse},
 * so clients can simply replace that member in their roster.
 */
@Component
public class MemberBroadcaster {

    private final SimpMessagingTemplate messaging;

    public MemberBroadcaster(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public static String topicFor(UUID familyId) {
        return "/topic/family/" + familyId + "/members";
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberUpdated(MemberUpdatedEvent event) {
        messaging.convertAndSend(topicFor(event.familyId()), event.member());
    }
}
