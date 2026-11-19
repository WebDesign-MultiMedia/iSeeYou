package com.familymap.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Whether a user was last seen inside or outside a place. Transitions are what we notify on. */
@Entity
@Table(name = "place_presence")
public class PlacePresence {

    @EmbeddedId
    private Key id;

    @Column(nullable = false)
    private boolean inside;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    /** Required by JPA. */
    protected PlacePresence() {
    }

    public PlacePresence(UUID placeId, UUID userId, boolean inside, Instant changedAt) {
        this.id = new Key(placeId, userId);
        set(inside, changedAt);
    }

    public final void set(boolean inside, Instant changedAt) {
        this.inside = inside;
        this.changedAt = changedAt.truncatedTo(ChronoUnit.MICROS); // match DATETIME(6)
    }

    public Key getId() {
        return id;
    }

    public boolean isInside() {
        return inside;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    @Embeddable
    public record Key(
            @JdbcTypeCode(SqlTypes.BINARY)
            @Column(name = "place_id", columnDefinition = "BINARY(16)", nullable = false) UUID placeId,
            @JdbcTypeCode(SqlTypes.BINARY)
            @Column(name = "user_id", columnDefinition = "BINARY(16)", nullable = false) UUID userId)
            implements Serializable {
    }
}
