package com.familymap.repository;

import com.familymap.domain.SosAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SosAlertRepository extends JpaRepository<SosAlert, UUID> {

    Optional<SosAlert> findFirstByUserIdAndResolvedAtIsNullOrderByCreatedAtDesc(UUID userId);

    @Query("""
            select a from SosAlert a join a.familyIds f
            where f = :familyId and a.resolvedAt is null
            order by a.createdAt desc""")
    List<SosAlert> findActiveInFamily(@Param("familyId") UUID familyId);

    /** One batch of ids to purge; the caller deletes them and loops (keeps transactions small). */
    @Query(value = "select id from sos_alerts where created_at < :cutoff order by created_at limit :limit",
            nativeQuery = true)
    List<byte[]> findIdsCreatedBefore(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
