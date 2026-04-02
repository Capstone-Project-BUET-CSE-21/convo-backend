package com.convo.backend.websocket;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private SignalingHandler signalingHandler;

    @Value("${MONA_FRONTEND_URL:}")
    private String monaFrontendUrl;

    @Value("${FARU_FRONTEND_URL:}")
    private String faruFrontendUrl;

    @Value("${DEBO_FRONTEND_URL:}")
    private String deboFrontendUrl;

    @Value("${TABA_FRONTEND_URL:}")
    private String tabaFrontendUrl;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(signalingHandler, "/ws")
                .setAllowedOrigins(buildAllowedOrigins());
    }

    private String[] buildAllowedOrigins() {
        List<String> origins = new ArrayList<>();
        addIfPresent(origins, monaFrontendUrl);
        addIfPresent(origins, faruFrontendUrl);
        addIfPresent(origins, deboFrontendUrl);
        addIfPresent(origins, tabaFrontendUrl);
        origins.add("http://localhost:5173");
        return origins.toArray(new String[0]);
    }

    private void addIfPresent(List<String> origins, String value) {
        if (value != null && !value.isBlank()) {
            origins.add(value);
        }
    }
}
