package com.familymap.repository;

import com.familymap.domain.Place;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlaceRepository extends JpaRepository<Place, UUID> {

    List<Place> findByFamilyIdOrderByCreatedAtAsc(UUID familyId);

    /** Every place a point must be checked against: all places in the circles it's shared with. */
    List<Place> findByFamilyIdIn(Collection<UUID> familyIds);

    Optional<Place> findByIdAndFamilyId(UUID id, UUID familyId);

    long countByFamilyId(UUID familyId);
}
