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

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Full filter chain against the in-memory H2 test database: real signup,
// real BCrypt, real JWT issuance and validation. Each test uses its own
// random email so tests never collide on the unique-email constraint.
@SpringBootTest
@AutoConfigureMockMvc
class AuthAndSecurityTest {

    private static final Pattern TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");

    @Autowired
    private MockMvc mvc;

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private static String signupJson(String email, String password, String confirm) {
        return """
                {"firstName":"Test","lastName":"User","email":"%s","password":"%s","confirmPassword":"%s"}
                """.formatted(email, password, confirm);
    }

    private static String loginJson(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }

    private String signUp(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/backend/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(email, password, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Matcher m = TOKEN.matcher(body);
        assertTrue(m.find(), "signup response carries a token: " + body);
        return m.group(1);
    }

    // ---- signup / login ----------------------------------------------------

    @Test
    void signup_ReturnsBearerToken() throws Exception {
        mvc.perform(post("/api/backend/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(uniqueEmail(), "secret123", "secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void signup_PasswordsDontMatch_Is400() throws Exception {
        mvc.perform(post("/api/backend/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(uniqueEmail(), "secret123", "different1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signup_DuplicateEmail_Is409_CaseInsensitively() throws Exception {
        String email = uniqueEmail();
        signUp(email, "secret123");

        mvc.perform(post("/api/backend/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(email.toUpperCase(), "secret123", "secret123")))
                .andExpect(status().isConflict());
    }

    @Test
    void login_CorrectPassword_ReturnsToken() throws Exception {
        String email = uniqueEmail();
        signUp(email, "secret123");

        mvc.perform(post("/api/backend/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void login_WrongPassword_Is401() throws Exception {
        String email = uniqueEmail();
        signUp(email, "secret123");

        mvc.perform(post("/api/backend/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "wrongpass")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_UnknownEmail_Is401_SameAsWrongPassword() throws Exception {
        mvc.perform(post("/api/backend/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(uniqueEmail(), "secret123")))
                .andExpect(status().isUnauthorized());
    }

    // ---- /me and the JWT filter --------------------------------------------

    @Test
    void me_WithToken_ReturnsOwnProfile() throws Exception {
        String email = uniqueEmail();
        String token = signUp(email, "secret123");

        mvc.perform(get("/api/backend/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void me_WithoutToken_Is401() throws Exception {
        mvc.perform(get("/api/backend/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void me_WithGarbageToken_Is401() throws Exception {
        mvc.perform(get("/api/backend/auth/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_WithoutToken_Is401() throws Exception {
        mvc.perform(get("/api/backend/credentials")).andExpect(status().isUnauthorized());
    }

    @Test
    void credentials_WithToken_ServeConfiguredTurnCredentials() throws Exception {
        String token = signUp(uniqueEmail(), "secret123");

        mvc.perform(get("/api/backend/credentials").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("test-turn-username")))
                .andExpect(content().string(containsString("test-turn-credential")));
    }

    @Test
    void healthEndpoint_IsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
