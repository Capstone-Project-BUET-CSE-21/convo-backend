package com.convo.backend.config;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

// Shared by every /api/backend/internal/** controller so the constant-time
// comparison (a naive .equals() would leak the key one byte at a time
// through response-timing differences) lives in exactly one place rather
// than being copy-pasted per controller.
@Component
public class InternalServiceAuth {

    public static final String SERVICE_KEY_HEADER = "X-Internal-Service-Key";

    private final InternalServiceProperties properties;

    public InternalServiceAuth(InternalServiceProperties properties) {
        this.properties = properties;
    }

    public void requireValidServiceKey(String providedKey) {
        String expectedKey = properties.getServiceKey();
        boolean valid = providedKey != null
                && MessageDigest.isEqual(
                        providedKey.getBytes(StandardCharsets.UTF_8),
                        expectedKey.getBytes(StandardCharsets.UTF_8));
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid or missing service credentials");
        }
    }
}
