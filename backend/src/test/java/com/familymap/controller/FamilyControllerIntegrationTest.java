package com.familymap.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Two (or three) real users create, join, read and leave circles through the full stack.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FamilyControllerIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void twoUsersShareACircle() throws Exception {
        String ana = register("Ana");
        String ben = register("Ben");

        JsonNode created = body(mvc.perform(as(ana, post("/api/families"))
                        .content("""
                                {"name":"The Smiths"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.myRole").value("ADMIN"))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.inviteCode").isNotEmpty()));
        String familyId = created.get("id").asText();
        String code = created.get("inviteCode").asText();

        // Ben joins, typing the code in lower case without the dash.
        mvc.perform(as(ben, post("/api/families/join"))
                        .content("""
                                {"inviteCode":"%s"}""".formatted(code.toLowerCase().replace("-", ""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(familyId))
                .andExpect(jsonPath("$.myRole").value("MEMBER"))
                .andExpect(jsonPath("$.memberCount").value(2));

        // Joining again is harmless.
        mvc.perform(as(ben, post("/api/families/join")).content("""
                        {"inviteCode":"%s"}""".formatted(code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(2));

        // Both see the circle and its roster.
        mvc.perform(as(ana, get("/api/families")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].memberCount").value(2));

        mvc.perform(as(ben, get("/api/families/" + familyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("MEMBER"))
                .andExpect(jsonPath("$.members", hasSize(2)))
                .andExpect(jsonPath("$.members[0].name").value("Ana"))
                .andExpect(jsonPath("$.members[0].role").value("ADMIN"))
                .andExpect(jsonPath("$.members[0].sharingEnabled").value(true))
                .andExpect(jsonPath("$.members[0].email").doesNotExist());

        mvc.perform(as(ana, get("/api/families/" + familyId + "/members")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void outsidersGet404() throws Exception {
        String ana = register("Ana");
        String eve = register("Eve");
        String familyId = createFamily(ana, "Private");

        mvc.perform(as(eve, get("/api/families/" + familyId))).andExpect(status().isNotFound());
        mvc.perform(as(eve, get("/api/families/" + familyId + "/members"))).andExpect(status().isNotFound());
        mvc.perform(as(eve, delete("/api/families/" + familyId + "/members/me"))).andExpect(status().isNotFound());
        mvc.perform(as(eve, get("/api/families"))).andExpect(jsonPath("$", hasSize(0)));
        // A random id looks exactly the same as an existing circle you're not in.
        mvc.perform(as(eve, get("/api/families/" + UUID.randomUUID()))).andExpect(status().isNotFound());
    }

    @Test
    void wrongInviteCodeIs404AndUnauthenticatedIs401() throws Exception {
        String ben = register("Ben");
        mvc.perform(as(ben, post("/api/families/join")).content("""
                        {"inviteCode":"ZZZZ-ZZZZ"}"""))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/families")).andExpect(status().isUnauthorized());
    }

    @Test
    void blankNameIs400() throws Exception {
        String ana = register("Ana");
        mvc.perform(as(ana, post("/api/families")).content("""
                        {"name":"  "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void adminLeavingHandsOverAndLastLeaverDeletesCircle() throws Exception {
        String ana = register("Ana");
        String ben = register("Ben");
        String cat = register("Cat");
        String familyId = createFamily(ana, "Handover");
        String code = inviteCode(ana, familyId);
        join(ben, code);
        join(cat, code);

        // Ana (only admin) leaves -> Ben, the longest-standing member, becomes admin.
        mvc.perform(as(ana, delete("/api/families/" + familyId + "/members/me")))
                .andExpect(status().isNoContent());
        mvc.perform(as(ana, get("/api/families/" + familyId))).andExpect(status().isNotFound());
        mvc.perform(as(ben, get("/api/families/" + familyId)))
                .andExpect(jsonPath("$.myRole").value("ADMIN"))
                .andExpect(jsonPath("$.members", hasSize(2)));

        mvc.perform(as(cat, delete("/api/families/" + familyId + "/members/me"))).andExpect(status().isNoContent());
        mvc.perform(as(ben, delete("/api/families/" + familyId + "/members/me"))).andExpect(status().isNoContent());

        // Circle is gone: its invite code no longer works.
        mvc.perform(as(cat, post("/api/families/join")).content("""
                        {"inviteCode":"%s"}""".formatted(code)))
                .andExpect(status().isNotFound());
    }

    // ---- helpers ----

    private String register(String name) throws Exception {
        String email = name.toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        return body(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","email":"%s","password":"s3cret-pass"}""".formatted(name, email)))
                .andExpect(status().isCreated()))
                .get("accessToken").asText();
    }

    private String createFamily(String token, String name) throws Exception {
        return body(mvc.perform(as(token, post("/api/families")).content("""
                        {"name":"%s"}""".formatted(name)))
                .andExpect(status().isCreated()))
                .get("id").asText();
    }

    private String inviteCode(String token, String familyId) throws Exception {
        return body(mvc.perform(as(token, get("/api/families/" + familyId))))
                .get("inviteCode").asText();
    }

    private void join(String token, String code) throws Exception {
        mvc.perform(as(token, post("/api/families/join")).content("""
                        {"inviteCode":"%s"}""".formatted(code)))
                .andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder req) {
        return req.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode body(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
