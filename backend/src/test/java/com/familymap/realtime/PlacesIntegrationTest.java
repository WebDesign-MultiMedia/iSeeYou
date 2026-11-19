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
 * Saved places end to end: Mom saves "Home", Leo walks in and out, and Mom gets live events
 * over STOMP plus a push notification (Expo's API is mocked).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "familymap.push.enabled=true")
class PlacesIntegrationTest {

    private static final long TIMEOUT_S = 5;
    private static final String MOM_TOKEN = "ExponentPushToken[mom-phone]";
    // Home at (40.0, -3.0), 100 m radius. 0.0005 deg lat ~ 56 m, 0.003 deg ~ 333 m.
    private static final double HOME_LAT = 40.0;
    private static final double HOME_LNG = -3.0;

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
    void arrivingAndLeavingHomeIsAnnouncedLiveAndPushed() throws Exception {
        Circle c = momAndLeoCircle();
        assertThat(send(HttpMethod.PUT, "/api/me/push-token", c.mom, """
                {"token":"%s","platform":"ios"}""".formatted(MOM_TOKEN)).status).isEqualTo(204);
        BlockingQueue<JsonNode> momEvents = subscribe(c.mom, CircleEventBroadcaster.topicFor(c.familyId));

        Response home = send(HttpMethod.POST, "/api/families/" + c.familyId + "/places", c.mom, """
                {"name":"Home","lat":%s,"lng":%s,"radiusM":100}""".formatted(HOME_LAT, HOME_LNG));
        assertThat(home.status).isEqualTo(201);
        assertThat(momEvents.poll(TIMEOUT_S, TimeUnit.SECONDS).get("type").asText()).isEqualTo("PLACE_ADDED");

        // Leo is away (baseline, silent), then walks in, then out again.
        report(c.leo, HOME_LAT + 0.003, 10);
        assertThat(momEvents.poll(1, TimeUnit.SECONDS)).as("first sighting is silent").isNull();

        report(c.leo, HOME_LAT + 0.0005, 10);
        JsonNode arrived = momEvents.poll(TIMEOUT_S, TimeUnit.SECONDS);
        assertThat(arrived).isNotNull();
        assertThat(arrived.get("type").asText()).isEqualTo("ARRIVED");
        assertThat(arrived.get("userName").asText()).isEqualTo("Leo");
        assertThat(arrived.get("placeName").asText()).isEqualTo("Home");

        ArgumentCaptor<List<PushMessage>> pushed = ArgumentCaptor.forClass(List.class);
        verify(pushClient, timeout(TIMEOUT_S * 1000)).send(pushed.capture());
        assertThat(pushed.getValue()).singleElement().satisfies(m -> {
            assertThat(m.to()).isEqualTo(MOM_TOKEN);
            assertThat(m.title()).isEqualTo("Leo arrived at Home");
        });

        report(c.leo, HOME_LAT + 0.0006, 10); // still home: nothing new
        assertThat(momEvents.poll(1, TimeUnit.SECONDS)).isNull();

        report(c.leo, HOME_LAT + 0.003, 10);
        assertThat(momEvents.poll(TIMEOUT_S, TimeUnit.SECONDS).get("type").asText()).isEqualTo("LEFT");
    }

    @Test
    void pausedCircleGetsNoArrivals() throws Exception {
        Circle c = momAndLeoCircle();
        send(HttpMethod.POST, "/api/families/" + c.familyId + "/places", c.mom, """
                {"name":"Home","lat":%s,"lng":%s,"radiusM":100}""".formatted(HOME_LAT, HOME_LNG));
        report(c.leo, HOME_LAT + 0.003, 10);
        BlockingQueue<JsonNode> momEvents = subscribe(c.mom, CircleEventBroadcaster.topicFor(c.familyId));

        send(HttpMethod.PUT, "/api/families/" + c.familyId + "/members/me/sharing", c.leo, """
                {"enabled":false}""");
        report(c.leo, HOME_LAT + 0.0005, 10); // walks home while paused
        send(HttpMethod.PUT, "/api/families/" + c.familyId + "/members/me/sharing", c.leo, """
                {"enabled":true}""");
        report(c.leo, HOME_LAT + 0.0005, 10); // first point after resuming: new baseline

        assertThat(momEvents.poll(1, TimeUnit.SECONDS)).as("no arrival leaks out of a pause").isNull();
    }

    @Test
    void placeRules() {
        Circle c = momAndLeoCircle();
        String eve = register("Eve");
        String places = "/api/families/" + c.familyId + "/places";
        String body = """
                {"name":"Home","lat":1,"lng":1,"radiusM":100}""";

        assertThat(send(HttpMethod.POST, places, eve, body).status).as("outsider").isEqualTo(404);
        assertThat(send(HttpMethod.GET, places, eve, null).status).isEqualTo(404);
        assertThat(send(HttpMethod.POST, places, c.leo, """
                {"name":"","lat":1,"lng":1,"radiusM":10}""").status).isEqualTo(400);

        String placeId = send(HttpMethod.POST, places, c.mom, body).body.get("id").asText();
        assertThat(send(HttpMethod.GET, places, c.leo, null).body).hasSize(1);
        assertThat(send(HttpMethod.DELETE, places + "/" + placeId, c.leo, null).status)
                .as("not Leo's place, Leo isn't admin").isEqualTo(403);
        assertThat(send(HttpMethod.DELETE, places + "/" + placeId, c.mom, null).status).isEqualTo(204);
        assertThat(send(HttpMethod.GET, places, c.leo, null).body).isEmpty();
    }

    @Test
    void pushTokenValidation() {
        String ana = register("Ana");
        assertThat(send(HttpMethod.PUT, "/api/me/push-token", ana, """
                {"token":"not-a-token","platform":"ios"}""").status).isEqualTo(400);
        assertThat(send(HttpMethod.PUT, "/api/me/push-token", ana, """
                {"token":"ExponentPushToken[abc]","platform":"web"}""").status).isEqualTo(400);
        assertThat(send(HttpMethod.PUT, "/api/me/push-token", ana, """
                {"token":"ExponentPushToken[abc]","platform":"android"}""").status).isEqualTo(204);
        assertThat(send(HttpMethod.DELETE, "/api/me/push-token", ana, """
                {"token":"ExponentPushToken[abc]"}""").status).isEqualTo(204);
        assertThat(send(HttpMethod.PUT, "/api/me/push-token", null, """
                {"token":"ExponentPushToken[abc]","platform":"ios"}""").status).isEqualTo(401);
    }

    // ---------------------------------------------------------------- helpers

    private record Circle(UUID familyId, String mom, String leo) { }

    private record Response(int status, JsonNode body) { }

    private Circle momAndLeoCircle() {
        String mom = register("Mom");
        String leo = register("Leo");
        JsonNode family = send(HttpMethod.POST, "/api/families", mom, """
                {"name":"Places"}""").body;
        send(HttpMethod.POST, "/api/families/join", leo, """
                {"inviteCode":"%s"}""".formatted(family.get("inviteCode").asText()));
        return new Circle(UUID.fromString(family.get("id").asText()), mom, leo);
    }

    /** Points must move forward in time; space them a second apart. */
    private long clockSkewMs = 0;

    private void report(String token, double lat, double accuracy) {
        clockSkewMs += 1000;
        String at = java.time.Instant.now().minusSeconds(60).plusMillis(clockSkewMs).toString();
        Response r = send(HttpMethod.POST, "/api/locations", token, """
                {"lat":%s,"lng":%s,"accuracy":%s,"recordedAt":"%s"}""".formatted(lat, HOME_LNG, accuracy, at));
        assertThat(r.status).isIn(200, 201);
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
