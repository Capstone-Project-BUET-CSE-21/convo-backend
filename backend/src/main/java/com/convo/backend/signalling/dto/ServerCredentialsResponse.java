package com.convo.backend.signalling.dto;

import java.util.List;

public record ServerCredentialsResponse(
        List<ServerCredentialDto> credentials) {
}