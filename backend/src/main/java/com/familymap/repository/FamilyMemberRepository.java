package com.familymap.repository;

import com.familymap.domain.FamilyMember;
import com.familymap.domain.FamilyRole;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FamilyMemberRepository extends JpaRepository<FamilyMember, UUID> {

    /** The membership check behind every circle-scoped request. */
    @EntityGraph(attributePaths = "family")
    Optional<FamilyMember> findByFamilyIdAndUserId(UUID familyId, UUID userId);

    /** "My circles", with each family loaded in the same query. */
    @EntityGraph(attributePaths = "family")
    List<FamilyMember> findByUserIdOrderByJoinedAtAsc(UUID userId);

    /** A circle's roster, with each user loaded in the same query. */
    @EntityGraph(attributePaths = "user")
    List<FamilyMember> findByFamilyIdOrderByJoinedAtAsc(UUID familyId);

    long countByFamilyId(UUID familyId);

    long countByFamilyIdAndRole(UUID familyId, FamilyRole role);

    /** Longest-standing other member: the one promoted when the last admin leaves. */
    Optional<FamilyMember> findFirstByFamilyIdAndUserIdNotOrderByJoinedAtAsc(UUID familyId, UUID excludedUserId);

    /** Member counts for several circles in one round-trip (avoids N+1 on "my circles"). */
    @Query("""
            select m.family.id as familyId, count(m) as memberCount
            from FamilyMember m
            where m.family.id in :familyIds
            group by m.family.id""")
    List<MemberCount> countMembersByFamilyIds(@Param("familyIds") Collection<UUID> familyIds);

    interface MemberCount {
        UUID getFamilyId();

        long getMemberCount();
    }
}
