package com.familymap.controller;

import com.familymap.dto.CreateFamilyRequest;
import com.familymap.dto.FamilyDetailResponse;
import com.familymap.dto.FamilyResponse;
import com.familymap.dto.JoinFamilyRequest;
import com.familymap.dto.MemberResponse;
import com.familymap.dto.SharingRequest;
import com.familymap.security.AuthenticatedUser;
import com.familymap.service.FamilyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Family circle endpoints. All require a JWT. Membership checks are done in
 * {@link FamilyService}, not here, so they also apply to every other caller of the service.
 */
@RestController
@RequestMapping("/api/families")
public class FamilyController {

    private final FamilyService familyService;

    public FamilyController(FamilyService familyService) {
        this.familyService = familyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FamilyResponse create(@AuthenticationPrincipal AuthenticatedUser me,
                                 @Valid @RequestBody CreateFamilyRequest request) {
        return familyService.create(me.id(), request.name());
    }

    @PostMapping("/join")
    public FamilyResponse join(@AuthenticationPrincipal AuthenticatedUser me,
                               @Valid @RequestBody JoinFamilyRequest request) {
        return familyService.join(me.id(), request.inviteCode());
    }

    @GetMapping
    public List<FamilyResponse> listMine(@AuthenticationPrincipal AuthenticatedUser me) {
        return familyService.listMine(me.id());
    }

    @GetMapping("/{familyId}")
    public FamilyDetailResponse get(@AuthenticationPrincipal AuthenticatedUser me,
                                    @PathVariable UUID familyId) {
        return familyService.get(familyId, me.id());
    }

    @GetMapping("/{familyId}/members")
    public List<MemberResponse> members(@AuthenticationPrincipal AuthenticatedUser me,
                                        @PathVariable UUID familyId) {
        return familyService.listMembers(familyId, me.id());
    }

    /**
     * Pause or resume sharing my location with this circle. Paused members are shown as
     * "paused" to the others, with no location at all (not even the last known one).
     */
    @PutMapping("/{familyId}/members/me/sharing")
    public MemberResponse setSharing(@AuthenticationPrincipal AuthenticatedUser me,
                                     @PathVariable UUID familyId,
                                     @Valid @RequestBody SharingRequest request) {
        return familyService.setSharing(familyId, me.id(), request.enabled());
    }

    /** Leave a circle. */
    @DeleteMapping("/{familyId}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@AuthenticationPrincipal AuthenticatedUser me,
                      @PathVariable UUID familyId) {
        familyService.leave(familyId, me.id());
    }
}
