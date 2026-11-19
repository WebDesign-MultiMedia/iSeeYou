package com.familymap.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The authorization contract, endpoint by endpoint, through the real security chain:
 * <ul>
 *   <li>no token: every endpoint except register/login/refresh/health answers 401;</li>
 *   <li>a signed-in stranger: every circle-scoped endpoint answers 404, exactly like a circle
 *       that doesn't exist, so circle ids can't be probed;</li>
 *   <li>a member: can read the circle but can't delete someone else's place (403).</li>
 * </ul>
 * If a new endpoint is added, it belongs in one of these lists.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthorizationIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String ana;       // admin of the circle
    private String ben;       // plain member
    private String eve;       // signed in, not in the circle
    private String familyId;
    private String placeId;
    private String sosId;

    @BeforeEach
    void circleWithAPlaceAndAnOpenSos() throws Exception {
        ana = register("Ana");
        ben = register("Ben");
        eve = register("Eve");

        JsonNode family = body(mvc.perform(as(ana, post("/api/families")).content("""
                {"name":"The Smiths"}""")).andExpect(status().isCreated()));
        familyId = family.get("id").asText();
        mvc.perform(as(ben, post("/api/families/join")).content("""
                {"inviteCode":"%s"}""".formatted(family.get("inviteCode").asText())))
                .andExpect(status().isOk());

        placeId = body(mvc.perform(as(ana, post(circle("/places"))).content("""
                {"name":"Home","lat":40.0,"lng":-73.0,"radiusM":100}""")).andExpect(status().isCreated()))
                .get("id").asText();
        sosId = body(mvc.perform(as(ana, post("/api/sos")).content("{}")).andExpect(status().isCreated()))
                .get("id").asText();
    }

    @TestFactory
    Stream<DynamicTest> strangersGet404OnEveryCircleEndpoint() {
        return Stream.of(
                        call("GET circle", () -> get(circle(""))),
                        call("GET members", () -> get(circle("/members"))),
                        call("PUT sharing", () -> put(circle("/members/me/sharing")).content("{\"enabled\":false}")),
                        call("DELETE membership", () -> delete(circle("/members/me"))),
                        call("GET places", () -> get(circle("/places"))),
                        call("POST place", () -> post(circle("/places")).content("""
                                {"name":"Spy spot","lat":40.0,"lng":-73.0,"radiusM":100}""")),
                        call("DELETE place", () -> delete(circle("/places/" + placeId))),
                        call("GET open SOS", () -> get(circle("/sos"))),
                        call("POST resolve SOS", () -> post("/api/sos/" + sosId + "/resolve")))
                .map(c -> DynamicTest.dynamicTest(c.name(), () ->
                        mvc.perform(as(eve, c.request().get())).andExpect(status().isNotFound())));
    }

    @TestFactory
    Stream<DynamicTest> everyProtectedEndpointNeedsAToken() {
        return Stream.of(
                        call("GET me", () -> get("/api/auth/me")),
                        call("GET my circles", () -> get("/api/families")),
                        call("POST circle", () -> post("/api/families").content("{\"name\":\"X\"}")),
                        call("POST join", () -> post("/api/families/join").content("{\"inviteCode\":\"ABCD-EFGH\"}")),
                        call("GET circle", () -> get(circle(""))),
                        call("PUT sharing", () -> put(circle("/members/me/sharing")).content("{\"enabled\":true}")),
                        call("GET places", () -> get(circle("/places"))),
                        call("POST location", () -> post("/api/locations").content("{\"lat\":1,\"lng\":1}")),
                        call("POST SOS", () -> post("/api/sos").content("{}")),
                        call("GET open SOS", () -> get(circle("/sos"))),
                        call("DELETE my history", () -> delete("/api/me/locations")),
                        call("PUT push token", () -> put("/api/me/push-token").content("""
                                {"token":"ExponentPushToken[abc]","platform":"ios"}""")))
                .map(c -> DynamicTest.dynamicTest(c.name(), () ->
                        mvc.perform(c.request().get().contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isUnauthorized())));
    }

    @Test
    void membersReadTheCircleButOnlyTheCreatorOrAnAdminDeletesAPlace() throws Exception {
        for (String path : List.of("", "/members", "/places", "/sos")) {
            mvc.perform(as(ben, get(circle(path)))).andExpect(status().isOk());
        }
        mvc.perform(as(ben, delete(circle("/places/" + placeId)))).andExpect(status().isForbidden());
        mvc.perform(as(ana, delete(circle("/places/" + placeId)))).andExpect(status().isNoContent());
    }

    @Test
    void pushTokensAreValidatedAndCanBeRemoved() throws Exception {
        String token = """
                {"token":"ExponentPushToken[abc123]","platform":"android"}""";
        mvc.perform(as(ben, put("/api/me/push-token")).content(token)).andExpect(status().isNoContent());
        // Registering the same device again is harmless.
        mvc.perform(as(ben, put("/api/me/push-token")).content(token)).andExpect(status().isNoContent());

        mvc.perform(as(ben, put("/api/me/push-token")).content("""
                {"token":"not-a-push-token","platform":"android"}""")).andExpect(status().isBadRequest());
        mvc.perform(as(ben, put("/api/me/push-token")).content("""
                {"token":"ExponentPushToken[abc123]","platform":"windows"}""")).andExpect(status().isBadRequest());

        mvc.perform(as(ben, delete("/api/me/push-token")).content(token)).andExpect(status().isNoContent());
    }

    // ---- helpers ----

    private record Call(String name, Supplier<MockHttpServletRequestBuilder> request) {}

    private static Call call(String name, Supplier<MockHttpServletRequestBuilder> request) {
        return new Call(name, request);
    }

    private String circle(String path) {
        return "/api/families/" + familyId + path;
    }

    private String register(String name) throws Exception {
        String email = name.toLowerCase() + "-" + System.nanoTime() + "@example.com";
        return body(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"%s","email":"%s","password":"correct-horse-battery"}""".formatted(name, email)))
                .andExpect(status().isCreated()))
                .get("accessToken").asText();
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder req) {
        return req.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
