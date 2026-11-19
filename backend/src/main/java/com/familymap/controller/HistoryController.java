package com.familymap.controller;

import com.familymap.security.AuthenticatedUser;
import com.familymap.service.HistoryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HistoryController {

    private final HistoryService history;

    public HistoryController(HistoryService history) {
        this.history = history;
    }

    /** Erases all of the caller's stored locations. Returns {@code {"deleted": n}}. */
    @DeleteMapping("/api/me/locations")
    public Map<String, Integer> deleteMine(@AuthenticationPrincipal AuthenticatedUser me) {
        return Map.of("deleted", history.deleteMyHistory(me.id()));
    }
}
