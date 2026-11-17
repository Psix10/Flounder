package com.acme.sportplatform.common;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.acme.sportplatform.common.demo.DemoDataAccess;

class DemoDataModeContractTest {
    private final UUID allowed = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private final UUID foreign = UUID.fromString("22222222-2222-4222-8222-222222222222");
    @Test void existingModeImplementsTheNarrowContract() {
        DemoDataAccess access = new DemoDataMode(true, allowed.toString());
        assertDoesNotThrow(() -> access.requireAllowed(allowed));
    }
    @Test void disabledModeStillRejectsAnAllowlistedEvent() {
        DemoDataAccess access = new DemoDataMode(false, allowed.toString());
        assertThrows(RuntimeException.class, () -> access.requireAllowed(allowed));
    }
    @Test void enabledModeStillRejectsForeignEvents() {
        DemoDataAccess access = new DemoDataMode(true, allowed.toString());
        assertThrows(RuntimeException.class, () -> access.requireAllowed(foreign));
    }
    @Test void nullEventIsStillRejected() {
        DemoDataAccess access = new DemoDataMode(true, allowed.toString());
        assertThrows(RuntimeException.class, () -> access.requireAllowed(null));
    }
    @Test void emptyAllowlistDoesNotEnableAllEvents() {
        DemoDataAccess access = new DemoDataMode(true, "");
        assertThrows(RuntimeException.class, () -> access.requireAllowed(allowed));
    }
    @Test void whitespaceInAllowlistRetainsExistingBehavior() {
        DemoDataAccess access = new DemoDataMode(true, " " + allowed + ", " + foreign + " ");
        assertDoesNotThrow(() -> access.requireAllowed(allowed));
        assertDoesNotThrow(() -> access.requireAllowed(foreign));
    }
}
