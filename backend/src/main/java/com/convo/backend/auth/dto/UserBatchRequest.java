package com.convo.backend.auth.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record UserBatchRequest(
        @NotEmpty
        @Size(max = 200, message = "Cannot resolve more than 200 user ids in one request")
        List<UUID> ids
) {
}