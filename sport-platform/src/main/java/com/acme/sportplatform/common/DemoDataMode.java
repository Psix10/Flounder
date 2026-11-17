package com.acme.sportplatform.common;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DemoDataMode implements com.acme.sportplatform.common.demo.DemoDataAccess {
    private final boolean enabled;
    private final Set<UUID> allowedEvents;
    public DemoDataMode(@Value("${app.demo-data.enabled:false}") boolean enabled,
            @Value("${app.demo-data.allowed-event-ids:}") String allowed) {
        this.enabled = enabled;
        this.allowedEvents = Arrays.stream(allowed.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).map(UUID::fromString)
                .collect(Collectors.toUnmodifiableSet());
    }
    @Override
    public void requireAllowed(UUID eventId) {
        if (!enabled || eventId == null || !allowedEvents.contains(eventId)) {
            throw new DemoDataException(404, "demo.unavailable",
                    "Демонаполнение выключено или не разрешено для этого события.");
        }
    }
}
