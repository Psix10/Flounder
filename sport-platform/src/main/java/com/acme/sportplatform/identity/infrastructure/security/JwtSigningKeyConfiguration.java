package com.acme.sportplatform.identity.infrastructure.security;

import java.util.Base64;
import javax.crypto.SecretKey;
import io.jsonwebtoken.security.Keys;

/** Validates the configured signing key without including it in diagnostics. */
final class JwtSigningKeyConfiguration {
    private JwtSigningKeyConfiguration() { }

    static SecretKey decode(String configuredSecret) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            throw invalid();
        }
        final byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(configuredSecret);
        } catch (IllegalArgumentException ignored) {
            throw invalid();
        }
        if (decoded.length < 32) {
            throw invalid();
        }
        return Keys.hmacShaKeyFor(decoded);
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException(
                "Invalid app.security.jwt.secret: configure a standard Base64-encoded "
                + "signing key of at least 32 decoded bytes. Generate a new random key "
                + "for this environment; do not reuse repository examples.");
    }
}
