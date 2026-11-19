package com.familymap.service;

import com.familymap.domain.PushToken;
import com.familymap.exception.InvalidRequestException;
import com.familymap.repository.PushTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class PushTokenService {

    private final PushTokenRepository tokens;
    private final Clock clock;

    public PushTokenService(PushTokenRepository tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    /** Idempotent. If the token belonged to another account (shared phone), it moves to this one. */
    @Transactional
    public void register(UUID userId, String token, String platform) {
        if (platform == null) {
            throw new InvalidRequestException("platform is required.");
        }
        tokens.findById(token).ifPresentOrElse(
                t -> t.assignTo(userId, platform, clock.instant()),
                () -> tokens.save(new PushToken(token, userId, platform, clock.instant())));
    }

    /** Only removes the caller's own token; anything else is a silent no-op. */
    @Transactional
    public void unregister(UUID userId, String token) {
        tokens.deleteByTokenAndUserId(token, userId);
    }
}
