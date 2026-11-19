package com.familymap.controller;

import com.familymap.dto.CreatePlaceRequest;
import com.familymap.dto.PlaceResponse;
import com.familymap.security.AuthenticatedUser;
import com.familymap.service.PlaceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Saved places of a circle. Members only (non-members get 404, as everywhere else). */
@RestController
@RequestMapping("/api/families/{familyId}/places")
public class PlaceController {

    private final PlaceService placeService;

    public PlaceController(PlaceService placeService) {
        this.placeService = placeService;
    }

    @GetMapping
    public List<PlaceResponse> list(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID familyId) {
        return placeService.list(familyId, me.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlaceResponse create(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID familyId,
                                @Valid @RequestBody CreatePlaceRequest request) {
        return placeService.create(familyId, me.id(), request);
    }

    /** {@code 403} unless you added the place or are a circle admin. */
    @DeleteMapping("/{placeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID familyId,
                       @PathVariable UUID placeId) {
        placeService.delete(familyId, placeId, me.id());
    }
}
