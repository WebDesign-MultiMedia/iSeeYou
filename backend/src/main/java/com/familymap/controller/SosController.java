package com.familymap.controller;

import com.familymap.dto.SosAlertResponse;
import com.familymap.dto.SosRequest;
import com.familymap.security.AuthenticatedUser;
import com.familymap.service.SosService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class SosController {

    private final SosService sos;

    public SosController(SosService sos) {
        this.sos = sos;
    }

    /** {@code 201} for a new SOS; {@code 200} when it updated the caller's open one. */
    @PostMapping("/api/sos")
    public ResponseEntity<SosAlertResponse> raise(@AuthenticationPrincipal AuthenticatedUser me,
                                                  @Valid @RequestBody SosRequest request) {
        SosAlertResponse alert = sos.raise(me.id(), request);
        boolean created = alert.createdAt().equals(alert.updatedAt());
        return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK).body(alert);
    }

    @PostMapping("/api/sos/{alertId}/resolve")
    public SosAlertResponse resolve(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID alertId) {
        return sos.resolve(alertId, me.id());
    }

    @GetMapping("/api/families/{familyId}/sos")
    public List<SosAlertResponse> active(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID familyId) {
        return sos.activeIn(familyId, me.id());
    }
}
