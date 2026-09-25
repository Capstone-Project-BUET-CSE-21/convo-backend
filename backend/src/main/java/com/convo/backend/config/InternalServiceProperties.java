package com.convo.backend.config;

import jakarta.annotation.PostConstruct;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;

// app.internal.service-key must be set (INTERNAL_SERVICE_KEY env var) —
// deliberately no fallback default, so startup fails loudly if it's ever
// left unset. That relies on its placeholder in application.properties
// having an EMPTY default (${INTERNAL_SERVICE_KEY:}): with none at all,
// Spring binds an unset variable as the literal text
// "${INTERNAL_SERVICE_KEY}", which would sail through the blank check
// below. app.jwt.secret follows the same rule (see JwtService).
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
