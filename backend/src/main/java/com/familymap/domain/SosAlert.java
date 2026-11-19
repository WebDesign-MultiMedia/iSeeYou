package com.familymap.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** An SOS: someone asked their circles for help. Open until the sender or a helper resolves it. */
@Entity
@Table(name = "sos_alerts")
public class SosAlert {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "user_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID userId;

    private Double lat;
    private Double lng;
    private Double accuracy;
    private Integer battery;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "resolved_by", columnDefinition = "BINARY(16)")
    private UUID resolvedBy;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "sos_alert_circles", joinColumns = @JoinColumn(name = "alert_id"))
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "family_id", columnDefinition = "BINARY(16)", nullable = false)
    private Set<UUID> familyIds = new HashSet<>();

    /** Required by JPA. */
    protected SosAlert() {
    }

    public SosAlert(UUID userId, Collection<UUID> familyIds, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.familyIds = new HashSet<>(familyIds);
        this.createdAt = now.truncatedTo(ChronoUnit.MICROS);
        this.updatedAt = this.createdAt;
    }

    /** Newer position and battery while the SOS is open (the sender may be moving). */
    public void update(Double lat, Double lng, Double accuracy, Integer battery, Instant now) {
        if (lat != null && lng != null) {
            this.lat = lat;
            this.lng = lng;
            this.accuracy = accuracy;
        }
        if (battery != null) {
            this.battery = battery;
        }
        this.updatedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public void resolve(UUID by, Instant now) {
        this.resolvedAt = now.truncatedTo(ChronoUnit.MICROS);
        this.resolvedBy = by;
        this.updatedAt = this.resolvedAt;
    }

    public boolean isActive() {
        return resolvedAt == null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Double getLat() {
        return lat;
    }

    public Double getLng() {
        return lng;
    }

    public Double getAccuracy() {
        return accuracy;
    }

    public Integer getBattery() {
        return battery;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public UUID getResolvedBy() {
        return resolvedBy;
    }

    public Set<UUID> getFamilyIds() {
        return Set.copyOf(familyIds);
    }
}
