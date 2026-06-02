package com.convo.backend.signalling.dto;

public record ServerCredentialDto(
        String urls,
        String username,
        String credential) {
}