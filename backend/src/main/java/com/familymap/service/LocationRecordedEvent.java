package com.familymap.service;

import com.familymap.dto.LocationResponse;

import java.util.List;
import java.util.UUID;

/**
 * Published when a point is stored. The realtime layer listens for it <em>after commit</em>,
 * so subscribers never see a point that was rolled back.
 */
public record LocationRecordedEvent(LocationResponse location, List<UUID> familyIds) {
}
