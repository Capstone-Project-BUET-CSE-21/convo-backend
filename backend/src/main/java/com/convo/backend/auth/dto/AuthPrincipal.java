package com.convo.backend.auth.dto;

import java.util.UUID;

public record AuthPrincipal(
        UUID id,
        String email,
        String userName
) {
}
