package com.convo.backend.signalling.service;

import com.convo.backend.config.TurnCredentialProperties;
import com.convo.backend.signalling.dto.ServerCredentialDto;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class ServerCredentialService {

    private final List<ServerCredentialDto> serverCredentials;

    public ServerCredentialService(TurnCredentialProperties turnCredentialProperties) {
        String username = turnCredentialProperties.getUsername();
        String credential = turnCredentialProperties.getCredential();
        this.serverCredentials = List.of(
                new ServerCredentialDto("stun:stun.relay.metered.ca:80", null, null),
                new ServerCredentialDto("turn:global.relay.metered.ca:80", username, credential),
                new ServerCredentialDto("turn:global.relay.metered.ca:80?transport=tcp", username, credential),
                new ServerCredentialDto("turn:global.relay.metered.ca:443", username, credential),
                new ServerCredentialDto("turns:global.relay.metered.ca:443?transport=tcp", username, credential));
    }

    public List<ServerCredentialDto> getServerCredentials() {
        return serverCredentials;
    }
}
