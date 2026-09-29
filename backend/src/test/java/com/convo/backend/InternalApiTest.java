package com.convo.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// /api/backend/internal/** is permitAll at the Spring Security layer and
// gated only by the X-Internal-Service-Key check in InternalServiceAuth —
// so these tests are the only thing proving that gate actually holds.
@SpringBootTest
@AutoConfigureMockMvc
class InternalApiTest {

    // Must match app.internal.service-key in src/test/resources/application.properties.
    private static final String KEY = "test-internal-service-key";
    private static final String HEADER = "X-Internal-Service-Key";

    private static final Pattern TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern USER_ID = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-f-]{36})\"");

    @Autowired
    private MockMvc mvc;

    private record SignedUp(String token, String userId) {}

    private SignedUp signUp(String firstName) throws Exception {
        String email = "internal-" + UUID.randomUUID() + "@example.com";
        String body = mvc.perform(post("/api/backend/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"%s","lastName":"Tester","email":"%s","password":"secret123","confirmPassword":"secret123"}
                                """.formatted(firstName, email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Matcher t = TOKEN.matcher(body);
        Matcher id = USER_ID.matcher(body);
        assertTrue(t.find() && id.find(), "signup response carries token and user id: " + body);
        return new SignedUp(t.group(1), id.group(1));
    }

    private static String batchBody(String... ids) {
        return "{\"ids\":[" + String.join(",", java.util.Arrays.stream(ids).map(i -> "\"" + i + "\"").toList()) + "]}";
    }

    // ---- meetings/{code}/participants ---------------------------------------

    @Test
    void participants_NoKey_Is403() throws Exception {
        mvc.perform(get("/api/backend/internal/meetings/any/participants")).andExpect(status().isForbidden());
    }

    @Test
    void participants_WrongKey_Is403() throws Exception {
        mvc.perform(get("/api/backend/internal/meetings/any/participants").header(HEADER, KEY + "x"))
                .andExpect(status().isForbidden());
    }

    @Test
    void participants_UserJwtInsteadOfKey_Is403() throws Exception {
        SignedUp user = signUp("Jwt");
        mvc.perform(get("/api/backend/internal/meetings/any/participants")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void participants_UnknownMeeting_Is404() throws Exception {
        mvc.perform(get("/api/backend/internal/meetings/" + UUID.randomUUID() + "/participants").header(HEADER, KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    void participants_AfterStartingMeeting_ListsHostWithDisplayName() throws Exception {
        SignedUp host = signUp("Hosty");
        String code = "room-" + UUID.randomUUID().toString().substring(0, 8);

        mvc.perform(post("/api/backend/meeting-entry")
                        .header("Authorization", "Bearer " + host.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"command\":\"start\",\"roomId\":\"" + code + "\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/backend/internal/meetings/" + code + "/participants").header(HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId").value(host.userId()))
                .andExpect(jsonPath("$[0].displayName").value("Hosty Tester"));
    }

    // ---- users/batch ---------------------------------------------------------

    @Test
    void usersBatch_NoKey_Is403() throws Exception {
        mvc.perform(post("/api/backend/internal/users/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchBody(UUID.randomUUID().toString())))
                .andExpect(status().isForbidden());
    }

    @Test
    void usersBatch_WithKey_ResolvesKnownIdsAndSilentlyDropsUnknown() throws Exception {
        SignedUp a = signUp("Alpha");
        SignedUp b = signUp("Beta");

        mvc.perform(post("/api/backend/internal/users/batch")
                        .header(HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchBody(a.userId(), b.userId(), UUID.randomUUID().toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.id == '" + a.userId() + "')].displayName").value("Alpha Tester"))
                .andExpect(jsonPath("$[?(@.id == '" + b.userId() + "')].displayName").value("Beta Tester"));
    }

    @Test
    void usersBatch_ResponseNeverExposesEmail() throws Exception {
        SignedUp a = signUp("Private");

        mvc.perform(post("/api/backend/internal/users/batch")
                        .header(HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchBody(a.userId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").doesNotExist());
    }

    @Test
    void publicUsersBatch_StillRequiresUserJwt() throws Exception {
        mvc.perform(post("/api/backend/users/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchBody(UUID.randomUUID().toString())))
                .andExpect(status().isUnauthorized());
    }
}
