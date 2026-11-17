package com.acme.sportplatform.demo;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.acme.sportplatform.common.DemoDataMode;
import com.acme.sportplatform.common.DemoDataException;
class DemoDataModeTest {
    private final UUID allowed = UUID.randomUUID();
    @Test void disabledByDefaultPolicyRejectsEvenListedEvent() {
        assertThrows(DemoDataException.class,()->new DemoDataMode(false,allowed.toString()).requireAllowed(allowed));
    }
    @Test void enabledWithoutExplicitAllowListRejects() {
        assertThrows(DemoDataException.class,()->new DemoDataMode(true,"").requireAllowed(allowed));
    }
    @Test void allowsOnlyExactListedEvent() {
        var policy = new DemoDataMode(true," " + allowed + " ");
        assertDoesNotThrow(()->policy.requireAllowed(allowed));
        assertThrows(DemoDataException.class,()->policy.requireAllowed(UUID.randomUUID()));
    }
}
