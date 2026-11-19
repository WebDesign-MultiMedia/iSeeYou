package com.familymap.realtime;

import com.familymap.dto.LocationRequest;
import com.familymap.exception.InvalidRequestException;
import com.familymap.security.AuthenticatedUser;
import com.familymap.service.LocationService;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

/**
 * Location publishing over the open socket ({@code SEND /app/locations}), for when the app is
 * in the foreground and already connected. It goes through the same {@link LocationService}
 * as the REST endpoint, so the consent and validation rules are identical.
 */
@Controller
public class LocationStompController {

    private final LocationService locationService;

    public LocationStompController(LocationService locationService) {
        this.locationService = locationService;
    }

    @MessageMapping("/locations")
    public void publish(@Valid @Payload LocationRequest request, Principal principal) {
        // The interceptor guarantees every SEND comes from an authenticated session.
        locationService.record(((AuthenticatedUser) principal).id(), request);
    }

    /** Bad payloads go back to the sender only, instead of dropping the whole connection. */
    @MessageExceptionHandler
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public Map<String, String> handleError(Exception ex) {
        String message = switch (ex) {
            case MethodArgumentNotValidException e -> "Invalid location payload.";
            case InvalidRequestException e -> e.getMessage();   // our own, client-safe messages
            default -> "Could not record location.";             // never leak internals
        };
        return Map.of("error", message);
    }
}
