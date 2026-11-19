package com.familymap.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real server on a random port, real WebSocket + STOMP client: proves that a posted location
 * reaches a subscribed family member live, and that outsiders can't listen in.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LocationRealtimeIntegrationTest {

    private static final long TIMEOUT_S = 5;

    @LocalServerPort int port;
    @Autowired ObjectMapper json;

    private RestClient http;
    private WebSocketStompClient stompClient;
    private final List<StompSession> sessions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        http = RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultStatusHandler(s -> true, (req, res) -> { }) // let tests assert on status codes
                .build();
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    }

    @AfterEach
    void tearDown() {
        sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
        stompClient.stop();
    }

    @Test
    void postedLocationIsPushedLiveToSubscribedMember() throws Exception {
        Circle c = momAndLeoCircle();
        Client mom = connect(c.momToken);
        BlockingQueue<JsonNode> momInbox = mom.subscribe(LocationBroadcaster.topicFor(c.familyId));

        var res = post("/api/locations", c.leoToken, """
                {"lat":40.4168,"lng":-3.7038,"accuracy":8.5,"battery":64}""");
        assertThat(res.status).isEqualTo(HttpStatus.CREATED.value());
        assertThat(res.body.get("sharedWith").get(0).asText()).isEqualTo(c.familyId.toString());

        JsonNode pushed = momInbox.poll(TIMEOUT_S, TimeUnit.SECONDS);
        assertThat(pushed).as("Mom should receive Leo's location live").isNotNull();
        assertThat(pushed.get("userId").asText()).isEqualTo(c.leoId);
        assertThat(pushed.get("lat").asDouble()).isEqualTo(40.4168);
        assertThat(pushed.get("battery").asInt()).isEqualTo(64);
        assertThat(pushed.get("recordedAt").asText()).isNotBlank();

        // And it's the latest location in the circle fetch.
        JsonNode circle = get("/api/families/" + c.familyId, c.momToken).body;
        JsonNode leo = memberNamed(circle, "Leo");
        assertThat(leo.get("lastLocation").get("lat").asDouble()).isEqualTo(40.4168);
        assertThat(memberNamed(circle, "Mom").get("lastLocation").isNull()).isTrue();
    }

    @Test
    void locationSentOverStompIsBroadcastToo() throws Exception {
        Circle c = momAndLeoCircle();
        Client mom = connect(c.momToken);
        BlockingQueue<JsonNode> momInbox = mom.subscribe(LocationBroadcaster.topicFor(c.familyId));
        Client leo = connect(c.leoToken);

        leo.session.send("/app/locations", """
                {"lat":51.5072,"lng":-0.1276,"battery":90}""".getBytes());

        JsonNode pushed = momInbox.poll(TIMEOUT_S, TimeUnit.SECONDS);
        assertThat(pushed).isNotNull();
        assertThat(pushed.get("userId").asText()).isEqualTo(c.leoId);
        assertThat(pushed.get("lng").asDouble()).isEqualTo(-0.1276);
    }

    @Test
    void invalidStompPayloadIsReportedToSenderOnly() throws Exception {
        Circle c = momAndLeoCircle();
        Client leo = connect(c.leoToken);
        BlockingQueue<JsonNode> leoErrors = leo.subscribe("/user/queue/errors");

        leo.session.send("/app/locations", """
                {"lat":999,"lng":0}""".getBytes());

        JsonNode error = leoErrors.poll(TIMEOUT_S, TimeUnit.SECONDS);
        assertThat(error).isNotNull();
        assertThat(error.get("error").asText()).isEqualTo("Invalid location payload.");
        assertThat(leo.session.isConnected()).isTrue(); // connection survives a bad message
    }

    @Test
    void outsiderCannotSubscribeToAnotherCircle() throws Exception {
        Circle c = momAndLeoCircle();
        Client eve = connect(register("Eve"));

        BlockingQueue<JsonNode> eveInbox = eve.subscribe(LocationBroadcaster.topicFor(c.familyId));

        assertThat(eve.errors.poll(TIMEOUT_S, TimeUnit.SECONDS))
                .as("server rejects the SUBSCRIBE with an ERROR frame").isNotNull();
        post("/api/locations", c.leoToken, """
                {"lat":1,"lng":1}""");
        assertThat(eveInbox.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void connectWithoutValidTokenIsRejected() throws Exception {
        BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        StompHeaders connect = new StompHeaders();
        connect.add("Authorization", "Bearer not-a-real-token");
        stompClient.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connect,
                new ErrorCollector(errors));

        assertThat(errors.poll(TIMEOUT_S, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void pausedMemberIsNeitherStoredNorShown() throws Exception {
        Circle c = momAndLeoCircle();
        post("/api/locations", c.leoToken, """
                {"lat":10,"lng":10}""");
        assertThat(setSharing(c.familyId, c.leoToken, false).status).isEqualTo(200);

        var res = post("/api/locations", c.leoToken, """
                {"lat":20,"lng":20}""");
        assertThat(res.status).isEqualTo(HttpStatus.OK.value());
        assertThat(res.body.get("shared").asBoolean()).isFalse();

        JsonNode leo = memberNamed(get("/api/families/" + c.familyId, c.momToken).body, "Leo");
        assertThat(leo.get("sharingEnabled").asBoolean()).isFalse();
        assertThat(leo.get("lastLocation").isNull())
                .as("not even the last point from before the pause is shown").isTrue();
    }

    @Test
    void pauseAndResumeArePushedLiveToTheCircle() throws Exception {
        Circle c = momAndLeoCircle();
        post("/api/locations", c.leoToken, """
                {"lat":10,"lng":10,"battery":80}""");
        Client mom = connect(c.momToken);
        BlockingQueue<JsonNode> momRoster = mom.subscribe(MemberBroadcaster.topicFor(c.familyId));

        Response paused = setSharing(c.familyId, c.leoToken, false);
        assertThat(paused.body.get("sharingEnabled").asBoolean()).isFalse();
        JsonNode pushed = momRoster.poll(TIMEOUT_S, TimeUnit.SECONDS);
        assertThat(pushed).as("Mom sees Leo pause live").isNotNull();
        assertThat(pushed.get("userId").asText()).isEqualTo(c.leoId);
        assertThat(pushed.get("sharingEnabled").asBoolean()).isFalse();
        assertThat(pushed.get("lastLocation").isNull()).isTrue();

        // Pausing twice is harmless and doesn't spam the circle.
        assertThat(setSharing(c.familyId, c.leoToken, false).status).isEqualTo(200);
        assertThat(momRoster.poll(1, TimeUnit.SECONDS)).isNull();

        setSharing(c.familyId, c.leoToken, true);
        JsonNode resumed = momRoster.poll(TIMEOUT_S, TimeUnit.SECONDS);
        assertThat(resumed).isNotNull();
        assertThat(resumed.get("sharingEnabled").asBoolean()).isTrue();
        assertThat(resumed.get("lastLocation").get("lat").asDouble()).isEqualTo(10);
    }

    @Test
    void sharingEndpointRules() {
        Circle c = momAndLeoCircle();
        String eve = register("Eve");
        assertThat(setSharing(c.familyId, eve, false).status).as("outsider").isEqualTo(404);
        assertThat(setSharing(c.familyId, null, false).status).isEqualTo(401);
        assertThat(put("/api/families/" + c.familyId + "/members/me/sharing", c.leoToken, "{}").status)
                .as("enabled is required").isEqualTo(400);
    }

    @Test
    void restValidation() {
        String token = register("Val");
        assertThat(post("/api/locations", token, """
                {"lat":91,"lng":0,"battery":101}""").status).isEqualTo(400);
        assertThat(post("/api/locations", token, """
                {"lat":0,"lng":0,"recordedAt":"2999-01-01T00:00:00Z"}""").status).isEqualTo(400);
        assertThat(post("/api/locations", null, """
                {"lat":0,"lng":0}""").status).isEqualTo(401);
    }

    // ---------------------------------------------------------------- helpers

    private record Circle(UUID familyId, String momToken, String leoToken, String leoId) { }

    private record Response(int status, JsonNode body) { }

    private Circle momAndLeoCircle() {
        String mom = register("Mom");
        String leo = register("Leo");
        JsonNode family = post("/api/families", mom, """
                {"name":"Realtime"}""").body;
        post("/api/families/join", leo, """
                {"inviteCode":"%s"}""".formatted(family.get("inviteCode").asText()));
        String leoId = get("/api/auth/me", leo).body.get("id").asText();
        return new Circle(UUID.fromString(family.get("id").asText()), mom, leo, leoId);
    }

    private String register(String name) {
        return post("/api/auth/register", null, """
                {"name":"%s","email":"%s-%s@example.com","password":"s3cret-pass"}"""
                .formatted(name, name.toLowerCase(), UUID.randomUUID())).body.get("accessToken").asText();
    }

    private Response setSharing(UUID familyId, String token, boolean enabled) {
        return put("/api/families/" + familyId + "/members/me/sharing", token, """
                {"enabled":%s}""".formatted(enabled));
    }

    private static JsonNode memberNamed(JsonNode circle, String name) {
        for (JsonNode m : circle.get("members")) {
            if (name.equals(m.get("name").asText())) {
                return m;
            }
        }
        throw new AssertionError("No member named " + name);
    }

    private Response post(String path, String token, String body) {
        return http.post().uri(path).contentType(MediaType.APPLICATION_JSON)
                .headers(h -> { if (token != null) h.setBearerAuth(token); })
                .body(body)
                .exchange((req, res) -> new Response(res.getStatusCode().value(), readJson(res.getBody().readAllBytes())));
    }

    private Response put(String path, String token, String body) {
        return http.put().uri(path).contentType(MediaType.APPLICATION_JSON)
                .headers(h -> { if (token != null) h.setBearerAuth(token); })
                .body(body)
                .exchange((req, res) -> new Response(res.getStatusCode().value(), readJson(res.getBody().readAllBytes())));
    }

    private Response get(String path, String token) {
        return http.get().uri(path).headers(h -> h.setBearerAuth(token))
                .exchange((req, res) -> new Response(res.getStatusCode().value(), readJson(res.getBody().readAllBytes())));
    }

    private JsonNode readJson(byte[] bytes) {
        try {
            return bytes.length == 0 ? null : json.readTree(bytes);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** A connected STOMP session plus a queue of ERROR frames the server sent it. */
    private final class Client {
        final StompSession session;
        final BlockingQueue<String> errors;

        Client(StompSession session, BlockingQueue<String> errors) {
            this.session = session;
            this.errors = errors;
        }

        BlockingQueue<JsonNode> subscribe(String destination) {
            BlockingQueue<JsonNode> inbox = new LinkedBlockingQueue<>();
            session.subscribe(destination, new StompFrameHandler() {
                @Override
                @NonNull
                public Type getPayloadType(@NonNull StompHeaders headers) {
                    return byte[].class;
                }

                @Override
                public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                    inbox.add(readJson((byte[]) payload));
                }
            });
            return inbox;
        }
    }

    private Client connect(String token) throws Exception {
        BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        StompHeaders connect = new StompHeaders();
        connect.add("Authorization", "Bearer " + token);
        StompSession session = stompClient
                .connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connect,
                        new ErrorCollector(errors))
                .get(TIMEOUT_S, TimeUnit.SECONDS);
        sessions.add(session);
        return new Client(session, errors);
    }

    /** Records ERROR frames and transport failures so tests can assert on rejections. */
    private static final class ErrorCollector extends StompSessionHandlerAdapter {
        private final BlockingQueue<String> errors;

        ErrorCollector(BlockingQueue<String> errors) {
            this.errors = errors;
        }

        @Override
        public void handleFrame(@NonNull StompHeaders headers, Object payload) {
            errors.add(String.valueOf(headers.getFirst("message")));
        }

        @Override
        public void handleTransportError(@NonNull StompSession session, @NonNull Throwable exception) {
            errors.add("transport: " + exception.getMessage());
        }

        @Override
        public void handleException(@NonNull StompSession s, org.springframework.messaging.simp.stomp.StompCommand c,
                                    @NonNull StompHeaders h, byte[] p, @NonNull Throwable ex) {
            errors.add("exception: " + ex.getMessage());
        }
    }
}
