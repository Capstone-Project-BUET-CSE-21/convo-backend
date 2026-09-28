package com.convo.backend.config;

import jakarta.annotation.PostConstruct;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;

// TURN credentials are handed to every authenticated client as-is (ICE
// servers must be visible to the browser to work at all), so this isn't
// about keeping them secret from clients — it's about not committing a
// third-party credential to source, so it can be rotated without a code
// change. Same empty-default-on-purpose pattern as InternalServiceProperties:
// with no default at all, an unset var binds as the literal text
// "${VAR}", which would sail through the blank check below.
@ConfigurationProperties(prefix = "app.turn")
public class TurnCredentialProperties {

    private String username;
    private String credential;

    @PostConstruct
    void validate() {
        if (username == null || username.isBlank()) {
            throw new IllegalStateException(
                    "app.turn.username (TURN_USERNAME) must be set — it authenticates the TURN relay handed to clients.");
        }
        if (credential == null || credential.isBlank()) {
            throw new IllegalStateException(
                    "app.turn.credential (TURN_CREDENTIAL) must be set — it authenticates the TURN relay handed to clients.");
        }
    }

    @NonNull
    public String getUsername() {
        return Objects.requireNonNull(username, "username read before validate() ran");
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @NonNull
    public String getCredential() {
        return Objects.requireNonNull(credential, "credential read before validate() ran");
    }

    public void setCredential(String credential) {
        this.credential = credential;
    }
}
