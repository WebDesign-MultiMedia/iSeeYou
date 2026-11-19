package com.familymap.repository;

import com.familymap.domain.PlacePresence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PlacePresenceRepository extends JpaRepository<PlacePresence, PlacePresence.Key> {

    @Query("select p from PlacePresence p where p.id.userId = :userId and p.id.placeId in :placeIds")
    List<PlacePresence> findForUser(@Param("userId") UUID userId, @Param("placeIds") Collection<UUID> placeIds);

    /**
     * Forgets a user's presence in one circle's places. Used when they pause or leave, so the
     * first point after resuming sets a baseline silently instead of announcing a transition
     * that happened while nobody was allowed to see them.
     */
    @Modifying
    @Query("""
            delete from PlacePresence p
            where p.id.userId = :userId
              and p.id.placeId in (select pl.id from Place pl where pl.familyId = :familyId)""")
    void deleteForUserInFamily(@Param("userId") UUID userId, @Param("familyId") UUID familyId);

    @Modifying
    @Query("delete from PlacePresence p where p.id.userId = :userId")
    void deleteAllForUser(@Param("userId") UUID userId);
}
