package com.familymap.service;

import com.familymap.dto.SosAlertResponse;

import java.util.Set;
import java.util.UUID;

/** Published when an SOS is raised, updated (new position) or resolved. Handled after commit. */
public record SosChangedEvent(Kind kind, SosAlertResponse alert, Set<UUID> familyIds) {

    public enum Kind { RAISED, UPDATED, RESOLVED }
}
