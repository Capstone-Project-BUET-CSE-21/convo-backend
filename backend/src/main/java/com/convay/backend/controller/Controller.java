package com.convay.backend.controller;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.convay.backend.JSON;

@RestController
public class Controller {

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

    @GetMapping("/server-credentials")
    public String getServerCredentials() throws Exception {
        System.out.println("Received request for server credentials");
        
        Map<String, Object> data = Map.of("credentials", serverCredentials);
        return JSON.stringify(data);
    }
    

    // @PostMapping("/process")
    // public String processData(@RequestBody String body) throws Exception {
    //     Map<String, Object> data = JSON.parse(body);
    //     // Logic to process data goes here
    //     System.out.println("Processing data: " + data.get("message"));

    //     return "Data processed successfully";
    // }
}
