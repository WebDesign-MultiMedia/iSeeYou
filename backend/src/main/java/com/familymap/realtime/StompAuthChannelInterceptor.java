package com.familymap.realtime;

import com.familymap.exception.ResourceNotFoundException;
import com.familymap.security.AuthenticatedUser;
import com.familymap.security.JwtService;
import com.familymap.service.FamilyAccessService;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authentication and authorization for every inbound STOMP frame.
 *
 * <ul>
 *   <li><b>CONNECT</b> must carry {@code Authorization: Bearer <access token>} as a STOMP
 *       header. The browser and React Native WebSocket APIs can't set HTTP headers on the
 *       handshake, so the token travels in the CONNECT frame.</li>
 *   <li><b>SUBSCRIBE</b> is allowed only to {@code /topic/family/{id}/locations},
 *       {@code .../members}, {@code .../events} or {@code .../sos} for a circle
 *       the user belongs to (checked by {@link FamilyAccessService}, the same rule as REST), or
 *       to the user's own {@code /user/queue/errors}.</li>
 *   <li><b>SEND</b> is allowed only to application destinations ({@code /app/...}). Clients can
 *       never publish straight to a {@code /topic}, so nobody can fake another member's location.</li>
 * </ul>
 *
 * Any rejection makes Spring send a STOMP ERROR frame and close the session.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern FAMILY_TOPIC =
            Pattern.compile("^/topic/family/([0-9a-fA-F-]{36})/(locations|members|events|sos)$");
    static final String ERRORS_QUEUE = "/user/queue/errors";

    private final JwtService jwtService;
    private final FamilyAccessService access;

    public StompAuthChannelInterceptor(JwtService jwtService, FamilyAccessService access) {
        this.jwtService = jwtService;
        this.access = access;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor stomp = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (stomp == null || stomp.getCommand() == null) {
            return message; // heartbeats etc.
        }
        switch (stomp.getCommand()) {
            case CONNECT -> stomp.setUser(authenticate(stomp));
            case SUBSCRIBE -> authorizeSubscribe(requireUser(stomp), stomp.getDestination());
            case SEND -> authorizeSend(requireUser(stomp), stomp.getDestination());
            default -> { /* DISCONNECT, UNSUBSCRIBE, ACK...: nothing to check */ }
        }
        return message;
    }

    private AuthenticatedUser authenticate(StompHeaderAccessor stomp) {
        String header = stomp.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new MessageDeliveryException("Missing bearer token.");
        }
        return jwtService.verify(header.substring(BEARER_PREFIX.length()).trim(), JwtService.TokenType.ACCESS)
                .orElseThrow(() -> new MessageDeliveryException("Invalid or expired token."));
    }

    private static AuthenticatedUser requireUser(StompHeaderAccessor stomp) {
        Principal user = stomp.getUser();
        if (user instanceof AuthenticatedUser authenticated) {
            return authenticated;
        }
        throw new MessageDeliveryException("Not authenticated.");
    }

    private void authorizeSubscribe(AuthenticatedUser user, String destination) {
        if (ERRORS_QUEUE.equals(destination)) {
            return;
        }
        Matcher m = destination == null ? null : FAMILY_TOPIC.matcher(destination);
        if (m == null || !m.matches()) {
            throw new MessageDeliveryException("Unknown destination.");
        }
        try {
            access.requireMember(UUID.fromString(m.group(1)), user.id());
        } catch (ResourceNotFoundException | IllegalArgumentException e) {
            // Same answer for "not a member" and "no such circle".
            throw new MessageDeliveryException("Circle not found.");
        }
    }

    private static void authorizeSend(AuthenticatedUser user, String destination) {
        if (destination == null || !destination.startsWith("/app/")) {
            throw new MessageDeliveryException("Clients may only send to /app destinations.");
        }
    }
}
