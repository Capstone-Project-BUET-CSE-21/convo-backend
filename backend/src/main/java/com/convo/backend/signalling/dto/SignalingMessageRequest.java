package com.convo.backend.signalling.dto;

import tools.jackson.databind.JsonNode;

public record SignalingMessageRequest(
        String type,
        String roomId,
        String to,
        JsonNode payload) {
}