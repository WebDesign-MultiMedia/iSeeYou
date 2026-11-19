package com.familymap.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** An Expo push token for one of a user's devices. */
@Entity
@Table(name = "push_tokens")
public class PushToken {

    @Id
    @Column(nullable = false, updatable = false)
    private String token;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "user_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 10)
    private String platform;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA. */
    protected PushToken() {
    }

    public PushToken(String token, UUID userId, String platform, Instant now) {
        this.token = token;
        assignTo(userId, platform, now);
    }

    /** Re-registering (or another account signing in on this phone) moves the token. */
    public final void assignTo(UUID userId, String platform, Instant now) {
        this.userId = userId;
        this.platform = platform;
        this.updatedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public String getToken() {
        return token;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getPlatform() {
        return platform;
    }
}
