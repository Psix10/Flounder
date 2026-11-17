package com.acme.sportplatform.competition.application;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.acme.sportplatform.competition.api.OperatorDisciplineResponse;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineEntity;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineRepository;
import com.acme.sportplatform.events.EventOperationAccess;

@Service
@Transactional(readOnly = true)
public class OperatorDisciplineQueryService {
    private final EventDisciplineRepository disciplines;
    private final EventOperationAccess access;
    public OperatorDisciplineQueryService(EventDisciplineRepository disciplines, EventOperationAccess access) {
        this.disciplines = disciplines; this.access = access;
    }
    public List<OperatorDisciplineResponse> list(UUID eventId, UUID actorId) {
        if (eventId == null || actorId == null || !access.isAssignedActiveOperator(eventId, actorId)) {
            throw new AccessDeniedException("Active assignment to this event is required");
        }
        return disciplines.findByEventId(eventId).stream()
                .sorted(Comparator.comparing(EventDisciplineEntity::getName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)).thenComparing(EventDisciplineEntity::getId))
                .map(item -> new OperatorDisciplineResponse(item.getId(), item.getEventId(), item.getName(),
                        item.getStatus(), item.getCompetitionFormat(), item.getUnitType(), item.getResultType()))
                .toList();
    }
}
