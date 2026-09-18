package com.convo.backend.config;

import jakarta.annotation.PostConstruct;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;

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

    // @NonNull + requireNonNull document and enforce a guarantee validate()
    // above establishes, not the field's type itself — see
    // convo-file-sharing's InternalServiceProperties for the full reasoning
    // (same class, same lifecycle guarantee, same fail-fast backstop).
    @NonNull
    public String getServiceKey() {
        return Objects.requireNonNull(serviceKey, "serviceKey read before validate() ran");
    }

    public void setServiceKey(String serviceKey) {
        this.serviceKey = serviceKey;
    }
}
