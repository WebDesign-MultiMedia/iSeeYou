package com.familymap.repository;

import com.familymap.domain.PushToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PushTokenRepository extends JpaRepository<PushToken, String> {

    List<PushToken> findByUserIdIn(Collection<UUID> userIds);

    void deleteByTokenAndUserId(String token, UUID userId);
}
