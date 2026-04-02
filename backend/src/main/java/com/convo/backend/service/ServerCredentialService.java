package com.convo.backend.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class ServerCredentialService {
    private final List<Map<String, String>> serverCredentials = List.of(
        Map.of("urls", "stun:stun.relay.metered.ca:80"),
        Map.of("urls", "turn:global.relay.metered.ca:80",
               "username", "587fae9b9e261459032795cc",
               "credential", "V1AMbjxp0ByH3JVr"),
        Map.of("urls", "turn:global.relay.metered.ca:80?transport=tcp",
                "username", "587fae9b9e261459032795cc",
                "credential", "V1AMbjxp0ByH3JVr"),
        Map.of("urls", "turn:global.relay.metered.ca:443",
               "username", "587fae9b9e261459032795cc",
               "credential", "V1AMbjxp0ByH3JVr"),
        Map.of("urls", "turns:global.relay.metered.ca:443?transport=tcp",
               "username", "587fae9b9e261459032795cc",
               "credential", "V1AMbjxp0ByH3JVr")

    );

    public List<Map<String, String>> getServerCredentials() {
        return serverCredentials;
    }
}
