package com.convo.backend.signalling.service;

import com.convo.backend.signalling.dto.ServerCredentialDto;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class ServerCredentialService {
    private final List<ServerCredentialDto> serverCredentials = List.of(
    new ServerCredentialDto("stun:stun.relay.metered.ca:80", null, null),
    new ServerCredentialDto(
        "turn:global.relay.metered.ca:80",
        "fcfe038eacf54e38527549ff",
        "yCmA10XtUM7JKMZp"),
    new ServerCredentialDto(
        "turn:global.relay.metered.ca:80?transport=tcp",
        "fcfe038eacf54e38527549ff",
        "yCmA10XtUM7JKMZp"),
    new ServerCredentialDto(
        "turn:global.relay.metered.ca:443",
        "fcfe038eacf54e38527549ff",
        "yCmA10XtUM7JKMZp"),
    new ServerCredentialDto(
        "turns:global.relay.metered.ca:443?transport=tcp",
        "fcfe038eacf54e38527549ff",
        "yCmA10XtUM7JKMZp")

    );

    public List<ServerCredentialDto> getServerCredentials() {
        return serverCredentials;
    }
}
