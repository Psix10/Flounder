package com.acme.sportplatform.events;
import java.util.UUID;
public interface DemoEventAccess {
    void lockDraft(UUID eventId);
}
