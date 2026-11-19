package com.familymap.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * One user's membership in one circle. This row is what every authorization check looks
 * for: no row, no access.
 */
@Entity
@Table(name = "family_members")
public class FamilyMember {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id", nullable = false, updatable = false)
    private Family family;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    // Stored as VARCHAR. Without @JdbcTypeCode, Hibernate 6 on MySQL expects a native ENUM column
    // and schema validation fails.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private FamilyRole role;

    /** Sharing is on by default, and the member can always see and change it (step 5). */
    @Column(name = "sharing_enabled", nullable = false)
    private boolean sharingEnabled = true;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    /** Required by JPA. */
    protected FamilyMember() {
    }

    public FamilyMember(Family family, User user, FamilyRole role) {
        this.id = UUID.randomUUID();
        this.family = family;
        this.user = user;
        this.role = role;
    }

    @PrePersist
    void onCreate() {
        if (joinedAt == null) {
            joinedAt = Instant.now();
        }
    }

    public boolean isAdmin() {
        return role == FamilyRole.ADMIN;
    }

    public UUID getId() {
        return id;
    }

    public Family getFamily() {
        return family;
    }

    public User getUser() {
        return user;
    }

    public FamilyRole getRole() {
        return role;
    }

    public void setRole(FamilyRole role) {
        this.role = role;
    }

    public boolean isSharingEnabled() {
        return sharingEnabled;
    }

    public void setSharingEnabled(boolean sharingEnabled) {
        this.sharingEnabled = sharingEnabled;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
