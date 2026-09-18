package com.convo.backend.signalling.controller;

import com.convo.backend.config.InternalServiceProperties;
import com.convo.backend.signalling.dto.MeetingParticipantDto;
import com.convo.backend.signalling.service.MeetingLifecycleService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

// Server-to-server only — not for browser clients. Authenticated by a
// shared X-Internal-Service-Key header (app.internal.service-key /
// INTERNAL_SERVICE_KEY, the same value the calling service is configured
// with), never a user JWT: there is no logged-in user on this call, just
// one Convo service asking another for data it owns rather than keeping
// its own copy or reaching into its tables directly. See
// WebAndSecurityConfig — /api/internal/** is permitAll at the Spring
// Security layer specifically so this header check is what gates it,
// instead of the normal per-user JWT filter.
@RestController
@RequestMapping("/api/internal")
public class InternalMeetingController {

    private static final String SERVICE_KEY_HEADER = "X-Internal-Service-Key";

    private final MeetingLifecycleService meetingLifecycleService;
    private final InternalServiceProperties internalServiceProperties;

    public InternalMeetingController(
            MeetingLifecycleService meetingLifecycleService,
            InternalServiceProperties internalServiceProperties) {
        this.meetingLifecycleService = meetingLifecycleService;
        this.internalServiceProperties = internalServiceProperties;
    }

    @GetMapping("/meetings/{meetingCode}/participants")
    public List<MeetingParticipantDto> listParticipants(
            @PathVariable String meetingCode,
            @RequestHeader(name = SERVICE_KEY_HEADER, required = false) String providedKey) {
        requireValidServiceKey(providedKey);
        return meetingLifecycleService.listParticipants(meetingCode);
    }

    // Constant-time comparison — a naive .equals() here would leak the key
    // one byte at a time through response-timing differences.
    private void requireValidServiceKey(String providedKey) {
        String expectedKey = internalServiceProperties.getServiceKey();
        boolean valid = providedKey != null
                && MessageDigest.isEqual(
                        providedKey.getBytes(StandardCharsets.UTF_8),
                        expectedKey.getBytes(StandardCharsets.UTF_8));
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid or missing service credentials");
        }
    }
}
