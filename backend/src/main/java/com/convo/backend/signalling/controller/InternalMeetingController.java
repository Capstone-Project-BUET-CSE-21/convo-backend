package com.convo.backend.signalling.controller;

import com.convo.backend.config.InternalServiceAuth;
import com.convo.backend.signalling.dto.MeetingParticipantDto;
import com.convo.backend.signalling.service.MeetingLifecycleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Server-to-server only — not for browser clients. Authenticated by a
// shared X-Internal-Service-Key header (app.internal.service-key /
// INTERNAL_SERVICE_KEY, the same value the calling service is configured
// with), never a user JWT: there is no logged-in user on this call, just
// one Convo service asking another for data it owns rather than keeping
// its own copy or reaching into its tables directly. See
// WebAndSecurityConfig — /api/backend/internal/** is permitAll at the Spring
// Security layer specifically so this header check is what gates it,
// instead of the normal per-user JWT filter.
@RestController
@RequestMapping("/api/backend/internal")
public class InternalMeetingController {

    private final MeetingLifecycleService meetingLifecycleService;
    private final InternalServiceAuth internalServiceAuth;

    public InternalMeetingController(
            MeetingLifecycleService meetingLifecycleService,
            InternalServiceAuth internalServiceAuth) {
        this.meetingLifecycleService = meetingLifecycleService;
        this.internalServiceAuth = internalServiceAuth;
    }

    @GetMapping("/meetings/{meetingCode}/participants")
    public List<MeetingParticipantDto> listParticipants(
            @PathVariable String meetingCode,
            @RequestHeader(name = InternalServiceAuth.SERVICE_KEY_HEADER, required = false) String providedKey) {
        internalServiceAuth.requireValidServiceKey(providedKey);
        return meetingLifecycleService.listParticipants(meetingCode);
    }
}
