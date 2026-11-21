package com.acme.sportplatform.events.application;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.events.EventDisplayLookup;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;

@Service
@Transactional(readOnly = true)
public class EventDisplayLookupService implements EventDisplayLookup {
    private final EventRepository events;

    public EventDisplayLookupService(EventRepository events) {
        this.events = events;
    }

    @Override
    public Map<UUID, String> titles(Set<UUID> eventIds) {
        if (eventIds.isEmpty()) return Map.of();
        Map<UUID, String> result = new HashMap<>();
        events.findAllById(eventIds).forEach(event -> {
            if (eventIds.contains(event.getId()) && event.getTitle() != null) {
                result.put(event.getId(), event.getTitle());
            }
        });
        return Map.copyOf(result);
    }
}
