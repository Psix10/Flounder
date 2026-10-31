package com.acme.sportplatform.competition.application;
import java.util.UUID;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.acme.sportplatform.competition.ProtocolDisciplineLookup;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineEntity;
import com.acme.sportplatform.events.ProtocolEventLookup;
import com.acme.sportplatform.common.exception.BusinessException;
@Service
public class ProtocolDisciplineLookupService implements ProtocolDisciplineLookup {
    @PersistenceContext private EntityManager entityManager;
    private final ProtocolEventLookup events;
    public ProtocolDisciplineLookupService(ProtocolEventLookup events) { this.events = events; }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public View lock(UUID id) {
        EventDisciplineEntity discipline = entityManager.find(EventDisciplineEntity.class, id);
        if (discipline == null) throw new BusinessException(
                "protocol.discipline_not_found", "Event discipline not found");
        entityManager.refresh(discipline, LockModeType.PESSIMISTIC_WRITE);
        var event = events.getById(discipline.getEventId());
        return new View(id, event.sportId(), event.organizationId(),
                discipline.getCompetitionFormat(), discipline.getUnitType());
    }
}
