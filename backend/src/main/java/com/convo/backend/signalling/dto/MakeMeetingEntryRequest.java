package com.convo.backend.signalling.dto;

import jakarta.validation.constraints.NotBlank;

public record MakeMeetingEntryRequest(
        @NotBlank String command,
        @NotBlank String roomId) {
}
