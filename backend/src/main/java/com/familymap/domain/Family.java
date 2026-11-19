package com.familymap.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * A family circle. People join with its {@link #getInviteCode() invite code}; who belongs
 * to it, and in what role, lives in {@link FamilyMember}.
 */
@Entity
@Table(name = "families")
public class Family {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "invite_code", nullable = false, unique = true, length = 16)
    private String inviteCode;

    /** Kept for history; the creator may later leave the circle. */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "created_by", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Required by JPA. */
    protected Family() {
    }

    public Family(String name, String inviteCode, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.inviteCode = inviteCode;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS); // match DATETIME(6)
        }
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
