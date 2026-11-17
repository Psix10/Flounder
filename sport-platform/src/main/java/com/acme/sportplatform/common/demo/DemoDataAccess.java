package com.acme.sportplatform.common.demo;

import java.util.UUID;

/** Narrow contract for the existing demo-mode gate. */
public interface DemoDataAccess {
    void requireAllowed(UUID eventId);
}
