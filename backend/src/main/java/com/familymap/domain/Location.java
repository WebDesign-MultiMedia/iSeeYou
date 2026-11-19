package com.familymap.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * One location fix from a member's device. Points belong to the <em>user</em>, not to a
 * circle: a user in two circles reports once, and each circle where sharing is on sees it.
 *
 * <p>{@code userId} is a plain column rather than a {@code @ManyToOne}: this is the busiest
 * table, and nothing here needs the User entity loaded.
 */
@Entity
@Table(name = "locations")
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "user_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private double lat;

    @Column(nullable = false, updatable = false)
    private double lng;

    /** Meters; null if the device didn't report it. */
    @Column(updatable = false)
    private Double accuracy;

    /** 0-100; null if unknown. */
    @Column(updatable = false)
    private Integer battery;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    /** Required by JPA. */
    protected Location() {
    }

    public Location(UUID userId, double lat, double lng, Double accuracy, Integer battery, Instant recordedAt) {
        this.userId = userId;
        this.lat = lat;
        this.lng = lng;
        this.accuracy = accuracy;
        this.battery = battery;
        this.recordedAt = recordedAt.truncatedTo(ChronoUnit.MICROS); // match DATETIME(6)
    }

    public Long getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public Double getAccuracy() {
        return accuracy;
    }

    public Integer getBattery() {
        return battery;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
