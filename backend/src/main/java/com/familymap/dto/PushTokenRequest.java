package com.familymap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * An Expo push token, e.g. {@code ExponentPushToken[xxxxxxxx]}. {@code platform} is required
 * when registering ({@code PUT}) and ignored when removing ({@code DELETE}).
 */
public record PushTokenRequest(
        @NotBlank @Size(max = 255) @Pattern(regexp = "^Expo(nent)?PushToken\\[[^\\]]+]$",
                message = "must be an Expo push token") String token,
        @Pattern(regexp = "^(ios|android)$", message = "must be ios or android") String platform) {
}
