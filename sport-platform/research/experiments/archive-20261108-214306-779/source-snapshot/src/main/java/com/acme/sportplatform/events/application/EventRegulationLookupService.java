package com.acme.sportplatform.events.application;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.acme.sportplatform.events.EventRegulationLookup;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
@Service
@Transactional(readOnly = true)
public class EventRegulationLookupService implements EventRegulationLookup {
    private final EventRepository events;
    public EventRegulationLookupService(EventRepository events) { this.events = events; }
    @Override public UUID versionId(UUID eventId) {
        return events.findById(eventId).orElseThrow(() -> new IllegalArgumentException("Event not found")).getRegulationVersionId();
    }
}
