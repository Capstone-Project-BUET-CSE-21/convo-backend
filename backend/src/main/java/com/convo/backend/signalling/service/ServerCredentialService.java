package com.convo.backend.signalling.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class ServerCredentialService {
    private final List<Map<String, String>> serverCredentials = List.of(
        Map.of("urls", "stun:stun.relay.metered.ca:80"),
        Map.of("urls", "turn:global.relay.metered.ca:80",
               "username", "fcfe038eacf54e38527549ff",
               "credential", "yCmA10XtUM7JKMZp"),
        Map.of("urls", "turn:global.relay.metered.ca:80?transport=tcp",
                "username", "fcfe038eacf54e38527549ff",
                "credential", "yCmA10XtUM7JKMZp"),
        Map.of("urls", "turn:global.relay.metered.ca:443",
               "username", "fcfe038eacf54e38527549ff",
               "credential", "yCmA10XtUM7JKMZp"),
        Map.of("urls", "turns:global.relay.metered.ca:443?transport=tcp",
               "username", "fcfe038eacf54e38527549ff",
               "credential", "yCmA10XtUM7JKMZp")

    );

    public List<Map<String, String>> getServerCredentials() {
        return serverCredentials;
    }
}
