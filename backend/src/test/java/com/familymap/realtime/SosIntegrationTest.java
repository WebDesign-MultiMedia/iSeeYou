package com.familymap.realtime;

import com.familymap.push.ExpoPushClient;
import com.familymap.push.PushMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SOS and "delete my history" end to end, with a real server, a real STOMP client and a
 * mocked Expo push client.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "familymap.push.enabled=true")
class SosIntegrationTest {

    private static final long TIMEOUT_S = 5;
    private static final String MOM_TOKEN = "ExponentPushToken[mom-phone]";

    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @MockitoBean ExpoPushClient pushClient;

    private RestClient http;
    private WebSocketStompClient stompClient;
    private StompSession momSession;

    @BeforeEach
    void setUp() {
        http = RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultStatusHandler(s -> true, (req, res) -> { })
                .build();
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        when(pushClient.send(any())).thenAnswer(inv -> {
            List<?> messages = inv.getArgument(0);
            return messages.stream().map(m -> new ExpoPushClient.Ticket("ok", null)).toList();
        });
    }

    @AfterEach
    void tearDown() {
        if (momSession != null && momSession.isConnected()) {
            momSession.disconnect();
        }
        stompClient.stop();
    }

    @Test
    @SuppressWarnings("unchecked")
    void sosReachesTheCircleEvenWhilePausedAndCanBeHandled() throws Exception {
        Circle c = momAndLeoCircle();
        send(HttpMethod.PUT, "/api/me/push-token", c.mom, """
                {"token":"%s","platform":"android"}""".formatted(MOM_TOKEN));
        send(HttpMethod.PUT, "/api/families/" + c.familyId + "/members/me/sharing", c.leo, """
                {"enabled":false}""");
        BlockingQueue<JsonNode> momSos = subscribe(c.mom, SosBroadcaster.topicFor(c.familyId));

        Response raised = send(HttpMethod.POST, "/api/sos", c.leo, """
                {"lat":40.0,"lng":-3.0,"accuracy":9,"battery":23}""");
        assertThat(raised.status).isEqualTo(201);
        String sosId = raised.body.get("id").asText();

        JsonNode live = momSos.poll(TIMEOUT_S, TimeUnit.SECONDS);
        assertThat(live).as("SOS overrides a pause: pressing it is explicit consent").isNotNull();
        assertThat(live.get("userName").asText()).isEqualTo("Leo");
        assertThat(live.get("lat").asDouble()).isEqualTo(40.0);
        assertThat(live.get("active").asBoolean()).isTrue();

        ArgumentCaptor<List<PushMessage>> pushed = ArgumentCaptor.forClass(List.class);
        verify(pushClient, timeout(TIMEOUT_S * 1000)).send(pushed.capture());
        assertThat(pushed.getValue()).singleElement().satisfies(m -> {
            assertThat(m.to()).isEqualTo(MOM_TOKEN);
            assertThat(m.title()).isEqualTo("🆘 Leo needs help");
            assertThat(m.priority()).isEqualTo("high");
            assertThat(m.channelId()).isEqualTo("sos");
        });

        // Pressing again while it's open updates the same alert.
        Response again = send(HttpMethod.POST, "/api/sos", c.leo, """
                {"lat":40.001,"lng":-3.0}""");
        assertThat(again.status).isEqualTo(200);
        assertThat(again.body.get("id").asText()).isEqualTo(sosId);
        assertThat(momSos.poll(TIMEOUT_S, TimeUnit.SECONDS).get("lat").asDouble()).isEqualTo(40.001);

        JsonNode active = send(HttpMethod.GET, "/api/families/" + c.familyId + "/sos", c.mom, null).body;
        assertThat(active).hasSize(1);

        String eve = register("Eve");
        assertThat(send(HttpMethod.POST, "/api/sos/" + sosId + "/resolve", eve, null).status).isEqualTo(404);

        Response handled = send(HttpMethod.POST, "/api/sos/" + sosId + "/resolve", c.mom, null);
        assertThat(handled.status).isEqualTo(200);
        assertThat(handled.body.get("active").asBoolean()).isFalse();
        assertThat(handled.body.get("resolvedByName").asText()).isEqualTo("Mom");
        assertThat(momSos.poll(TIMEOUT_S, TimeUnit.SECONDS).get("active").asBoolean()).isFalse();
        assertThat(send(HttpMethod.GET, "/api/families/" + c.familyId + "/sos", c.mom, null).body).isEmpty();
    }

    @Test
    void sosValidation() {
        String loner = register("Loner");
        assertThat(send(HttpMethod.POST, "/api/sos", loner, "{}").status).as("no circle to alert").isEqualTo(400);
        Circle c = momAndLeoCircle();
        assertThat(send(HttpMethod.POST, "/api/sos", c.leo, """
                {"lat":40.0}""").status).as("lat without lng").isEqualTo(400);
        assertThat(send(HttpMethod.POST, "/api/sos", null, "{}").status).isEqualTo(401);
    }

    @Test
    void deletingHistoryRemovesTheMarkerLive() throws Exception {
        Circle c = momAndLeoCircle();
        send(HttpMethod.POST, "/api/locations", c.leo, """
                {"lat":10,"lng":10}""");
        send(HttpMethod.POST, "/api/locations", c.leo, """
                {"lat":11,"lng":11}""");
        BlockingQueue<JsonNode> momRoster = subscribe(c.mom, MemberBroadcaster.topicFor(c.familyId));

        Response res = send(HttpMethod.DELETE, "/api/me/locations", c.leo, null);
        assertThat(res.status).isEqualTo(200);
        assertThat(res.body.get("deleted").asInt()).isEqualTo(2);

        JsonNode pushed = momRoster.poll(TIMEOUT_S, TimeUnit.SECONDS);
        assertThat(pushed).isNotNull();
        assertThat(pushed.get("userId").asText()).isEqualTo(c.leoId);
        assertThat(pushed.get("lastLocation").isNull()).isTrue();

        JsonNode circle = send(HttpMethod.GET, "/api/families/" + c.familyId, c.mom, null).body;
        for (JsonNode m : circle.get("members")) {
            assertThat(m.get("lastLocation").isNull()).isTrue();
        }
    }

    // ---------------------------------------------------------------- helpers

    private record Circle(UUID familyId, String mom, String leo, String leoId) { }

    private record Response(int status, JsonNode body) { }

    private Circle momAndLeoCircle() {
        String mom = register("Mom");
        String leo = register("Leo");
        JsonNode family = send(HttpMethod.POST, "/api/families", mom, """
                {"name":"Places"}""").body;
        send(HttpMethod.POST, "/api/families/join", leo, """
                {"inviteCode":"%s"}""".formatted(family.get("inviteCode").asText()));
        String leoId = send(HttpMethod.GET, "/api/auth/me", leo, null).body.get("id").asText();
        return new Circle(UUID.fromString(family.get("id").asText()), mom, leo, leoId);
    }

    private String register(String name) {
        return send(HttpMethod.POST, "/api/auth/register", null, """
                {"name":"%s","email":"%s-%s@example.com","password":"s3cret-pass"}"""
                .formatted(name, name.toLowerCase(), UUID.randomUUID())).body.get("accessToken").asText();
    }

    private Response send(HttpMethod method, String path, String token, String body) {
        var req = http.method(method).uri(path).contentType(MediaType.APPLICATION_JSON)
                .headers(h -> { if (token != null) h.setBearerAuth(token); });
        if (body != null) {
            req = req.body(body);
        }
        return req.exchange((rq, res) -> {
            byte[] bytes = res.getBody().readAllBytes();
            return new Response(res.getStatusCode().value(), bytes.length == 0 ? null : json.readTree(bytes));
        });
    }

    private BlockingQueue<JsonNode> subscribe(String token, String destination) throws Exception {
        StompHeaders connect = new StompHeaders();
        connect.add("Authorization", "Bearer " + token);
        momSession = stompClient.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connect,
                new StompSessionHandlerAdapter() { }).get(TIMEOUT_S, TimeUnit.SECONDS);
        BlockingQueue<JsonNode> inbox = new LinkedBlockingQueue<>();
        momSession.subscribe(destination, new StompFrameHandler() {
            @Override
            @NonNull
            public Type getPayloadType(@NonNull StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                try {
                    inbox.add(json.readTree((byte[]) payload));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        });
        Thread.sleep(200); // let the SUBSCRIBE reach the broker before we trigger events
        return inbox;
    }
}
