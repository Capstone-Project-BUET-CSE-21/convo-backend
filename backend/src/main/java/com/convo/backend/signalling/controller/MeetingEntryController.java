package com.convo.backend.signalling.controller;

import com.convo.backend.auth.dto.AuthPrincipal;
import com.convo.backend.signalling.service.MeetingLifecycleService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/backend")
public class MeetingEntryController {

    private final MeetingLifecycleService meetingLifecycleService;

    public MeetingEntryController(MeetingLifecycleService meetingLifecycleService) {
        this.meetingLifecycleService = meetingLifecycleService;
    }

    @PostMapping("/meeting-entry")
    public ResponseEntity<Map<String, String>> makeMeetingEntry(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody MakeMeetingEntryRequest request) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        meetingLifecycleService.makeMeetingEntry(request.command(), request.roomId(), principal.id());
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    public record MakeMeetingEntryRequest(
            @NotBlank String command,
            @NotBlank String roomId) {
    }
}
