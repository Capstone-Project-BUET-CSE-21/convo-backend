package com.convo.backend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

// app.internal.service-key must be set (INTERNAL_SERVICE_KEY env var) —
// deliberately no fallback default here. app.jwt.secret's placeholder
// default (see JwtProperties/application.properties) is a known hardening
// gap: a deployment that forgets to set it fails silently into a
// guessable, working signing key instead of failing to start. This
// property fails startup loudly instead if it's ever left unset.
@ConfigurationProperties(prefix = "app.internal")
public class InternalServiceProperties {

    private String serviceKey;

    @PostConstruct
    void validate() {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new IllegalStateException(
                    "app.internal.service-key (INTERNAL_SERVICE_KEY) must be set — "
                            + "it authenticates server-to-server calls from other Convo services.");
        }
    }

    public String getServiceKey() {
        return serviceKey;
    }

    public void setServiceKey(String serviceKey) {
        this.serviceKey = serviceKey;
    }
}
