package com.familymap.security;

import java.util.UUID;

/**
 * The principal stored in the SecurityContext once a request's JWT is verified.
 * Kept deliberately small: services load anything else they need by {@link #id()}.
 */
public record AuthenticatedUser(UUID id, String email) {
}
