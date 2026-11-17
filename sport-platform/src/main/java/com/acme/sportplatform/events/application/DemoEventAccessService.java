package com.acme.sportplatform.events.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.acme.sportplatform.common.DemoDataException;
import com.acme.sportplatform.common.DemoDataMode;
import com.acme.sportplatform.events.DemoEventAccess;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

@Service
public class DemoEventAccessService implements DemoEventAccess {
    private final EntityManager entities;
    private final DemoDataMode mode;
    public DemoEventAccessService(EntityManager entities, DemoDataMode mode) {
        this.entities = entities; this.mode = mode;
    }
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockDraft(UUID eventId) {
        mode.requireAllowed(eventId);
        var event = entities.find(EventEntity.class, eventId, LockModeType.PESSIMISTIC_WRITE);
        if (event == null) throw new DemoDataException(404, "demo.event_not_found", "Событие не найдено.");
        // Refresh also replaces an entity that might already be present in the persistence context.
        entities.refresh(event, LockModeType.PESSIMISTIC_WRITE);
        if (!"DRAFT".equals(event.getStatus())) throw new DemoDataException(409,
                "demo.event_not_draft", "Наполнение разрешено только в черновике события, до публикации.");
    }
}
