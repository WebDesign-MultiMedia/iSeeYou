package com.familymap.controller;

import com.familymap.dto.PushTokenRequest;
import com.familymap.security.AuthenticatedUser;
import com.familymap.service.PushTokenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * This device's push token. The app registers after the user turns on arrival alerts, and
 * removes it on sign-out so the next person on a shared phone doesn't get someone else's alerts.
 */
@RestController
@RequestMapping("/api/me/push-token")
public class PushTokenController {

    private final PushTokenService pushTokens;

    public PushTokenController(PushTokenService pushTokens) {
        this.pushTokens = pushTokens;
    }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody PushTokenRequest request) {
        pushTokens.register(me.id(), request.token(), request.platform());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody PushTokenRequest request) {
        pushTokens.unregister(me.id(), request.token());
    }
}
