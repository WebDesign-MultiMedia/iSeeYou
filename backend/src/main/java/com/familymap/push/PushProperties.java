package com.familymap.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Push settings bound from {@code familymap.push.*}.
 *
 * @param enabled     turn off to never call Expo (tests, offline demos)
 * @param url         Expo push API endpoint
 * @param accessToken optional Expo access token, if "enhanced push security" is on for the project
 */
@ConfigurationProperties(prefix = "familymap.push")
public record PushProperties(boolean enabled, String url, String accessToken) {

    public PushProperties {
        if (url == null || url.isBlank()) {
            url = "https://exp.host/--/api/v2/push/send";
        }
    }
}
