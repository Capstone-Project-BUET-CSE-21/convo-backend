package com.convo.backend.signalling.controller;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;

import com.convo.backend.signalling.service.ServerCredentialService;
import com.convo.backend.signalling.utilities.JSONUtils;

@RestController
@RequestMapping("/api/backend")
public class ServerCredentialController {
    
    @Autowired
    private ServerCredentialService credentialService;

    @GetMapping("/credentials")
    public String getServerCredentials() throws Exception {
        List<Map<String, String>> serverCredentials = credentialService.getServerCredentials();
    
        Map<String, Object> data = Map.of("credentials", serverCredentials);
        return JSONUtils.stringify(data);
    }
    

    // @PostMapping("/process")
    // public String processData(@RequestBody String body) throws Exception {
    //     Map<String, Object> data = JSON.parse(body);
    //     // Logic to process data goes here
    //     System.out.println("Processing data: " + data.get("message"));

    //     return "Data processed successfully";
    // }
}
