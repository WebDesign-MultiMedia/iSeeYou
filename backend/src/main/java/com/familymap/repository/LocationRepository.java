package com.familymap.repository;

import com.familymap.domain.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LocationRepository extends JpaRepository<Location, Long> {

    /**
     * The most recent point for each of the given users, in one query. Uses the
     * (user_id, recorded_at) index. If two points share the newest timestamp, both come back;
     * the caller keeps one.
     */
    @Query("""
            select l from Location l
            where l.userId in :userIds
              and l.recordedAt = (select max(l2.recordedAt) from Location l2 where l2.userId = l.userId)""")
    List<Location> findLatestForUsers(@Param("userIds") Collection<UUID> userIds);
}
