package com.familymap.service;

import com.familymap.domain.Family;
import com.familymap.domain.FamilyMember;
import com.familymap.domain.FamilyRole;
import com.familymap.domain.User;
import com.familymap.dto.FamilyDetailResponse;
import com.familymap.dto.FamilyResponse;
import com.familymap.dto.LocationResponse;
import com.familymap.dto.MemberResponse;
import com.familymap.exception.ResourceNotFoundException;
import com.familymap.repository.FamilyMemberRepository;
import com.familymap.repository.FamilyRepository;
import com.familymap.repository.PlacePresenceRepository;
import com.familymap.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Creating, joining, listing and leaving family circles.
 *
 * <p>Every method that takes a {@code familyId} goes through {@link FamilyAccessService} first,
 * so a user can only ever see circles they belong to.
 */
@Service
public class FamilyService {

    /** Retries if a freshly generated invite code is already taken (vanishingly rare). */
    private static final int MAX_CODE_ATTEMPTS = 5;

    private final FamilyRepository families;
    private final FamilyMemberRepository members;
    private final UserRepository users;
    private final FamilyAccessService access;
    private final InviteCodeGenerator codes;
    private final LocationService locations;
    private final ApplicationEventPublisher events;
    private final PlacePresenceRepository presence;

    public FamilyService(FamilyRepository families, FamilyMemberRepository members, UserRepository users,
                         FamilyAccessService access, InviteCodeGenerator codes, LocationService locations,
                         ApplicationEventPublisher events, PlacePresenceRepository presence) {
        this.families = families;
        this.members = members;
        this.users = users;
        this.access = access;
        this.codes = codes;
        this.locations = locations;
        this.events = events;
        this.presence = presence;
    }

    /** Creates a circle; the caller becomes its first ADMIN. */
    @Transactional
    public FamilyResponse create(UUID userId, String name) {
        User creator = loadUser(userId);
        Family family = families.save(new Family(name.trim(), uniqueInviteCode(), userId));
        members.save(new FamilyMember(family, creator, FamilyRole.ADMIN));
        return FamilyResponse.of(family, FamilyRole.ADMIN, 1);
    }

    /**
     * Joins the circle matching the invite code as a MEMBER. Idempotent: joining a circle you're
     * already in returns it unchanged, so re-opening an invite link is harmless.
     */
    @Transactional
    public FamilyResponse join(UUID userId, String inviteCode) {
        Family family = families.findByInviteCode(InviteCodeGenerator.normalize(inviteCode))
                .orElseThrow(() -> new ResourceNotFoundException("No circle matches that invite code."));

        FamilyRole role = members.findByFamilyIdAndUserId(family.getId(), userId)
                .map(FamilyMember::getRole)
                .orElseGet(() -> {
                    members.save(new FamilyMember(family, loadUser(userId), FamilyRole.MEMBER));
                    return FamilyRole.MEMBER;
                });
        return FamilyResponse.of(family, role, members.countByFamilyId(family.getId()));
    }

    /** Every circle the caller belongs to, oldest membership first. */
    @Transactional(readOnly = true)
    public List<FamilyResponse> listMine(UUID userId) {
        List<FamilyMember> mine = members.findByUserIdOrderByJoinedAtAsc(userId);
        if (mine.isEmpty()) {
            return List.of();
        }
        Map<UUID, Long> counts = members
                .countMembersByFamilyIds(mine.stream().map(m -> m.getFamily().getId()).toList())
                .stream()
                .collect(Collectors.toMap(FamilyMemberRepository.MemberCount::getFamilyId,
                        FamilyMemberRepository.MemberCount::getMemberCount));

        return mine.stream()
                .map(m -> FamilyResponse.of(m.getFamily(), m.getRole(),
                        counts.getOrDefault(m.getFamily().getId(), 0L)))
                .toList();
    }

    /** A circle with its roster and each member's latest location. Members only. */
    @Transactional(readOnly = true)
    public FamilyDetailResponse get(UUID familyId, UUID userId) {
        FamilyMember me = access.requireMember(familyId, userId);
        return FamilyDetailResponse.of(me.getFamily(), me.getRole(), roster(familyId));
    }

    /** A circle's members. Members only. */
    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(UUID familyId, UUID userId) {
        access.requireMember(familyId, userId);
        return roster(familyId);
    }

    /**
     * Pauses or resumes the caller's sharing in one circle and tells the rest of the circle.
     * Idempotent: setting the current value again changes nothing and broadcasts nothing.
     *
     * <p>Resuming shows the member's latest stored point again (if any); fresh points follow
     * as soon as their phone reports one.
     */
    @Transactional
    public MemberResponse setSharing(UUID familyId, UUID userId, boolean enabled) {
        FamilyMember me = access.requireMember(familyId, userId);
        boolean changed = me.isSharingEnabled() != enabled;
        me.setSharingEnabled(enabled);
        if (!enabled) {
            // While paused nobody may learn where they are, so the first point after resuming
            // only sets a new baseline instead of announcing "left Home".
            presence.deleteForUserInFamily(userId, familyId);
        }

        LocationResponse latest = enabled ? locations.latestFor(List.of(userId)).get(userId) : null;
        MemberResponse member = MemberResponse.from(me, latest);
        if (changed) {
            events.publishEvent(new MemberSharingChangedEvent(familyId, member));
        }
        return member;
    }

    /**
     * Removes the caller from a circle. A circle always keeps at least one admin:
     * <ul>
     *   <li>the last admin leaving promotes the longest-standing remaining member;</li>
     *   <li>the last member leaving deletes the circle.</li>
     * </ul>
     */
    @Transactional
    public void leave(UUID familyId, UUID userId) {
        FamilyMember me = access.requireMember(familyId, userId);
        presence.deleteForUserInFamily(userId, familyId);

        if (members.countByFamilyId(familyId) == 1) {
            members.delete(me);
            families.delete(me.getFamily());
            return;
        }

        if (me.isAdmin() && members.countByFamilyIdAndRole(familyId, FamilyRole.ADMIN) == 1) {
            members.findFirstByFamilyIdAndUserIdNotOrderByJoinedAtAsc(familyId, userId)
                    .ifPresent(successor -> successor.setRole(FamilyRole.ADMIN));
        }
        members.delete(me);
    }

    /** Members plus their latest points, in two queries total (roster + latest-per-user). */
    private List<MemberResponse> roster(UUID familyId) {
        List<FamilyMember> roster = members.findByFamilyIdOrderByJoinedAtAsc(familyId);
        Map<UUID, LocationResponse> latest = locations.latestFor(
                roster.stream().filter(FamilyMember::isSharingEnabled).map(m -> m.getUser().getId()).toList());
        return roster.stream()
                .map(m -> MemberResponse.from(m, latest.get(m.getUser().getId())))
                .toList();
    }

    private User loadUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    private String uniqueInviteCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = codes.generate();
            if (!families.existsByInviteCode(code)) {
                return code;
            }
        }
        // The DB unique constraint is the real guarantee; this only fires if the RNG is badly broken.
        throw new IllegalStateException("Could not generate a unique invite code.");
    }
}
