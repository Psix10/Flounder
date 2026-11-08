package com.acme.sportplatform.events.application;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.acme.sportplatform.events.ProtocolEventLookup;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import com.acme.sportplatform.common.exception.BusinessException;
@Service
public class ProtocolEventLookupService implements ProtocolEventLookup {
    private final EventRepository events;
    public ProtocolEventLookupService(EventRepository events) { this.events = events; }
    @Override @Transactional(readOnly = true)
    public View getById(UUID id) {
        var event = events.findById(id).orElseThrow(() ->
                new BusinessException("events.event_not_found", "Event not found"));
        return new View(event.getSportId(), event.getOrganizationId());
    }
}
