package com.convo.backend.auth.dto;

import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String userName,
        String email,
        String displayName
) {
}
