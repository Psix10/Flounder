package com.acme.sportplatform.identity.application.bootstrap;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

final class AdminBootstrapCredentials {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final String email;
    private final String password;

    private AdminBootstrapCredentials(String email, String password) {
        this.email = email;
        this.password = password;
    }

    static AdminBootstrapCredentials validate(String suppliedEmail, String suppliedPassword) {
        String email = suppliedEmail == null ? "" : suppliedEmail.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 255 || !EMAIL.matcher(email).matches()
                || email.codePoints().anyMatch(c -> Character.isISOControl(c) || Character.isSpaceChar(c))) {
            throw new IllegalStateException("Bootstrap admin requires a valid app.bootstrap.admin.email.");
        }
        if (suppliedPassword == null || suppliedPassword.isBlank()
                || suppliedPassword.codePointCount(0, suppliedPassword.length()) < 12
                || suppliedPassword.getBytes(StandardCharsets.UTF_8).length > 72
                || suppliedPassword.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalStateException("Bootstrap admin password must contain at least 12 characters, "
                    + "at most 72 UTF-8 bytes and no control characters.");
        }
        return new AdminBootstrapCredentials(email, suppliedPassword);
    }

    String email() { return email; }
    String password() { return password; }
    @Override public String toString() { return "AdminBootstrapCredentials[REDACTED]"; }
}
