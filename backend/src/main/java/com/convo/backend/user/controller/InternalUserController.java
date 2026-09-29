package com.convo.backend.user.controller;

import com.convo.backend.auth.dto.PublicUserResponse;
import com.convo.backend.auth.dto.UserBatchRequest;
import com.convo.backend.config.InternalServiceAuth;
import com.convo.backend.user.service.UserLookupService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Server-to-server only — see InternalMeetingController for the full
// rationale (shared X-Internal-Service-Key header, no user JWT involved).
// Backs convo-file-sharing embedding real display names directly in its
// chain-history/downloads responses, instead of the browser making a
// second, separate call to convo-backend for names after already fetching
// the provenance data from convo-file-sharing.
@RestController
@RequestMapping("/api/backend/internal")
public class InternalUserController {

    private final UserLookupService userLookupService;
    private final InternalServiceAuth internalServiceAuth;

    public InternalUserController(UserLookupService userLookupService, InternalServiceAuth internalServiceAuth) {
        this.userLookupService = userLookupService;
        this.internalServiceAuth = internalServiceAuth;
    }

    @PostMapping("/users/batch")
    public List<PublicUserResponse> getByIds(
            @Valid @RequestBody UserBatchRequest request,
            @RequestHeader(name = InternalServiceAuth.SERVICE_KEY_HEADER, required = false) String providedKey) {
        internalServiceAuth.requireValidServiceKey(providedKey);
        return userLookupService.getByIds(request.ids());
    }
}
