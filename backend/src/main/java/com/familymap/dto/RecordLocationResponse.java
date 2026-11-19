package com.familymap.dto;

import java.util.List;
import java.util.UUID;

/**
 * Result of reporting a location.
 *
 * @param shared       false when sharing is paused in every circle. The point is then
 *                     <b>discarded, not stored</b>.
 * @param sharedWith   circles the point was broadcast to
 * @param location     the stored point, or null if not shared
 */
public record RecordLocationResponse(boolean shared, List<UUID> sharedWith, LocationResponse location) {

    public static RecordLocationResponse notShared() {
        return new RecordLocationResponse(false, List.of(), null);
    }
}
