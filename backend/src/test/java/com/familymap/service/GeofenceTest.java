package com.familymap.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeofenceTest {

    @Test
    void insideWithinRadius() {
        assertThat(Geofence.decide(false, 99, 100, 10.0)).contains(true);
        assertThat(Geofence.decide(null, 100, 100, null)).contains(true);
    }

    @Test
    void outsideOnlyBeyondTheExitMargin() {
        // radius 100 -> margin max(25, 20) = 25 -> exit beyond 125 m
        assertThat(Geofence.exitDistance(100)).isEqualTo(125);
        assertThat(Geofence.decide(true, 126, 100, 10.0)).contains(false);
    }

    @Test
    void deadBandKeepsThePreviousState() {
        assertThat(Geofence.decide(true, 110, 100, 10.0)).contains(true);
        assertThat(Geofence.decide(false, 110, 100, 10.0)).contains(false);
        assertThat(Geofence.decide(null, 110, 100, 10.0)).as("first sighting counts as outside").contains(false);
    }

    @Test
    void marginScalesWithLargePlaces() {
        assertThat(Geofence.exitDistance(1000)).isEqualTo(1200);
    }

    @Test
    void fixLessPreciseThanThePlaceDecidesNothing() {
        assertThat(Geofence.decide(false, 10, 100, 150.0)).isEmpty();
    }

    @Test
    void haversineDistance() {
        // One degree of latitude is ~111.2 km.
        assertThat(Geofence.distanceMeters(0, 0, 1, 0)).isCloseTo(111_195, within(5.0));
        assertThat(Geofence.distanceMeters(40.4168, -3.7038, 40.4168, -3.7038)).isZero();
    }
}
