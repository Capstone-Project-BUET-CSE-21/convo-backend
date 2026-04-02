package com.convo.backend.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebAndSecurityConfig {

    @Value("${MONA_FRONTEND_URL:}")
    private String monaFrontendUrl;

    @Value("${FARU_FRONTEND_URL:}")
    private String faruFrontendUrl;

    @Value("${DEBO_FRONTEND_URL:}")
    private String deboFrontendUrl;

    @Value("${TABA_FRONTEND_URL:}")
    private String tabaFrontendUrl;

    @Value("${ANIS_FRONTEND_URL:}")
    private String anisFrontendUrl;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // disable CSRF (needed for POST requests from Postman / JS clients)
                .csrf(csrf -> csrf.disable())

                // allow all requests without authentication
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll());

        return http.build();
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**") // all endpoints
                        .allowedOrigins(buildAllowedOrigins()) // allow this origin
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }

    private String[] buildAllowedOrigins() {
        List<String> origins = new ArrayList<>();
        addIfPresent(origins, monaFrontendUrl);
        addIfPresent(origins, faruFrontendUrl);
        addIfPresent(origins, deboFrontendUrl);
        addIfPresent(origins, tabaFrontendUrl);
        addIfPresent(origins, anisFrontendUrl);
        origins.add("http://localhost:5173");
        return origins.toArray(new String[0]);
    }

    private void addIfPresent(List<String> origins, String value) {
        if (value != null && !value.isBlank()) {
            origins.add(value);
        }
    }
}
