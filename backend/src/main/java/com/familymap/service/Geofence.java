package com.familymap.service;

import java.util.Optional;

/**
 * Inside/outside rules for a circular place. Pure functions so they're easy to test.
 *
 * <p>GPS wobbles, so a member standing near the edge would otherwise "arrive" and "leave"
 * every minute. Two guards stop that:
 * <ul>
 *   <li><b>Hysteresis:</b> you're inside within the radius, but only outside again beyond the
 *       radius plus a margin (20% of the radius, at least 25 m). In between, nothing changes.</li>
 *   <li><b>Accuracy gate:</b> a fix whose error is larger than the place itself can't tell
 *       inside from outside, so it decides nothing.</li>
 * </ul>
 */
public final class Geofence {

    static final double MIN_EXIT_MARGIN_M = 25;
    static final double EXIT_MARGIN_RATIO = 0.2;
    private static final double EARTH_RADIUS_M = 6_371_000;

    private Geofence() {
    }

    /**
     * @param wasInside the last known state, or null if we've never seen this member here
     * @return the new state, or empty if this fix can't decide
     */
    public static Optional<Boolean> decide(Boolean wasInside, double distanceM, int radiusM, Double accuracyM) {
        if (accuracyM != null && accuracyM > radiusM) {
            return Optional.empty();
        }
        if (distanceM <= radiusM) {
            return Optional.of(true);
        }
        if (distanceM > exitDistance(radiusM)) {
            return Optional.of(false);
        }
        // Dead band near the edge: keep whatever we had (a first sighting counts as outside).
        return Optional.of(wasInside != null && wasInside);
    }

    static double exitDistance(int radiusM) {
        return radiusM + Math.max(MIN_EXIT_MARGIN_M, radiusM * EXIT_MARGIN_RATIO);
    }

    /** Great-circle distance in meters (haversine). */
    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(h));
    }
}
