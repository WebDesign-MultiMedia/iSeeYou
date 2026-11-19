package com.familymap.controller;

import com.familymap.dto.LocationRequest;
import com.familymap.dto.RecordLocationResponse;
import com.familymap.security.AuthenticatedUser;
import com.familymap.service.LocationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Location ingestion over HTTP. Background tasks on the phone (step 5) use this. */
@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    /** {@code 201} when stored and shared; {@code 200} with {@code shared=false} when sharing is paused everywhere. */
    @PostMapping
    public ResponseEntity<RecordLocationResponse> record(@AuthenticationPrincipal AuthenticatedUser me,
                                                         @Valid @RequestBody LocationRequest request) {
        RecordLocationResponse result = locationService.record(me.id(), request);
        return ResponseEntity.status(result.shared() ? HttpStatus.CREATED : HttpStatus.OK).body(result);
    }
}
