package com.familymap.security;

import java.security.Principal;
import java.util.UUID;

/**
 * The principal stored in the SecurityContext (HTTP) or STOMP session (WebSocket) once a
 * JWT is verified. Kept deliberately small: services load anything else they need by {@link #id()}.
 *
 * <p>Implements {@link Principal} so STOMP user destinations ({@code /user/queue/...}) are
 * keyed by user id.
 */
public record AuthenticatedUser(UUID id, String email) implements Principal {

    @Override
    public String getName() {
        return id.toString();
    }
}
