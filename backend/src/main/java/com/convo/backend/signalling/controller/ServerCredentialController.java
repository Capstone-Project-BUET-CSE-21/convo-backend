package com.convo.backend.signalling.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.convo.backend.signalling.dto.ServerCredentialsResponse;
import com.convo.backend.signalling.service.ServerCredentialService;

@RestController
@RequestMapping("/api/backend")
public class ServerCredentialController {
    
    @Autowired
    private ServerCredentialService credentialService;

    @GetMapping("/credentials")
    public ServerCredentialsResponse getServerCredentials() {
        return new ServerCredentialsResponse(credentialService.getServerCredentials());
    }
}
