package com.familymap.push;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/** {@link ExpoPushClient} over HTTP: {@code POST https://exp.host/--/api/v2/push/send}. */
@Component
public class HttpExpoPushClient implements ExpoPushClient {

    private final RestClient http;

    public HttpExpoPushClient(PushProperties properties, RestClient.Builder builder) {
        this.http = builder
                .baseUrl(properties.url())
                .defaultHeaders(h -> {
                    h.setContentType(MediaType.APPLICATION_JSON);
                    h.setAccept(List.of(MediaType.APPLICATION_JSON));
                    if (properties.accessToken() != null && !properties.accessToken().isBlank()) {
                        h.setBearerAuth(properties.accessToken());
                    }
                })
                .build();
    }

    @Override
    public List<Ticket> send(List<PushMessage> messages) {
        JsonNode response = http.post().body(messages).retrieve().body(JsonNode.class);
        List<Ticket> tickets = new ArrayList<>();
        if (response != null && response.has("data")) {
            for (JsonNode t : response.get("data")) {
                JsonNode details = t.path("details");
                tickets.add(new Ticket(t.path("status").asText(null), details.path("error").asText(null)));
            }
        }
        return tickets;
    }
}
