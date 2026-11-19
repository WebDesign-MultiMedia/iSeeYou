package com.familymap.push;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/** One message in Expo's push API format. {@code data} reaches the app when the user taps it. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PushMessage(String to, String title, String body, Map<String, String> data, String sound,
                          String channelId) {
}
