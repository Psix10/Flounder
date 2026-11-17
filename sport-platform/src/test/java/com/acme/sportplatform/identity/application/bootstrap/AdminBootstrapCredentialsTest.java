package com.acme.sportplatform.identity.application.bootstrap;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AdminBootstrapCredentialsTest {
    private static final String GOOD = "Strong-Demo-Password-2026!";
    @Test void trimsAndCanonicalizesNewEmail() {
        assertEquals("admin@example.invalid", AdminBootstrapCredentials.validate(" Admin@Example.Invalid ", GOOD).email());
    }
    @Test void passwordIsNotTrimmed() {
        assertEquals(" " + GOOD + " ", AdminBootstrapCredentials.validate("admin@example.invalid", " " + GOOD + " ").password());
    }
    @Test void missingEmailIsRejected() {
        assertThrows(IllegalStateException.class, () -> AdminBootstrapCredentials.validate(null, GOOD));
    }
    @Test void malformedEmailIsRejected() {
        assertThrows(IllegalStateException.class, () -> AdminBootstrapCredentials.validate("not-an-email", GOOD));
    }
    @Test void whitespaceInEmailIsRejected() {
        assertThrows(IllegalStateException.class, () -> AdminBootstrapCredentials.validate("ad min@example.invalid", GOOD));
    }
    @Test void oversizedEmailIsRejected() {
        assertThrows(IllegalStateException.class, () -> AdminBootstrapCredentials.validate("a".repeat(256) + "@example.invalid", GOOD));
    }
    @Test void missingPasswordIsRejected() {
        assertThrows(IllegalStateException.class, () -> AdminBootstrapCredentials.validate("admin@example.invalid", null));
    }
    @Test void shortPasswordIsRejected() {
        assertThrows(IllegalStateException.class, () -> AdminBootstrapCredentials.validate("admin@example.invalid", "a".repeat(11)));
    }
    @Test void twelveCharactersAreAccepted() {
        assertDoesNotThrow(() -> AdminBootstrapCredentials.validate("admin@example.invalid", "a".repeat(12)));
    }
    @Test void seventyTwoAsciiBytesAreAccepted() {
        assertDoesNotThrow(() -> AdminBootstrapCredentials.validate("admin@example.invalid", "a".repeat(72)));
    }
    @Test void seventyThreeAsciiBytesAreRejected() {
        assertThrows(IllegalStateException.class, () -> AdminBootstrapCredentials.validate("admin@example.invalid", "a".repeat(73)));
    }
    @Test void utf8BytesRatherThanJavaLengthAreBounded() {
        assertThrows(IllegalStateException.class, () -> AdminBootstrapCredentials.validate("admin@example.invalid", "А".repeat(37)));
    }
    @Test void controlCharactersAreRejected() {
        assertThrows(IllegalStateException.class, () -> AdminBootstrapCredentials.validate("admin@example.invalid", GOOD + "\n"));
    }
    @Test void diagnosticTextDoesNotExposePassword() {
        var result = AdminBootstrapCredentials.validate("admin@example.invalid", GOOD);
        assertFalse(result.toString().contains(GOOD));
        var error = assertThrows(IllegalStateException.class,
                () -> AdminBootstrapCredentials.validate("bad", GOOD));
        assertFalse(error.getMessage().contains(GOOD));
    }
}
