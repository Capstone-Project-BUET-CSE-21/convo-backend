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
        "587fae9b9e261459032795cc",
        "V1AMbjxp0ByH3JVr"),
    new ServerCredentialDto(
        "turn:global.relay.metered.ca:80?transport=tcp",
        "587fae9b9e261459032795cc",
        "V1AMbjxp0ByH3JVr"),
    new ServerCredentialDto(
        "turn:global.relay.metered.ca:443",
        "587fae9b9e261459032795cc",
        "V1AMbjxp0ByH3JVr"),
    new ServerCredentialDto(
        "turns:global.relay.metered.ca:443?transport=tcp",
        "587fae9b9e261459032795cc",
        "V1AMbjxp0ByH3JVr")

    );

    public List<ServerCredentialDto> getServerCredentials() {
        return serverCredentials;
    }
}
