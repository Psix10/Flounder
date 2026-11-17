package com.acme.sportplatform.events.application;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.events.api.OperatorEventResponse;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventOperatorAssignmentRepository;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import com.acme.sportplatform.identity.OperatorEligibilityLookup;

@Service
@Transactional(readOnly = true)
public class OperatorEventsService {
    private final EventRepository events;
    private final EventOperatorAssignmentRepository assignments;
    private final OperatorEligibilityLookup eligibility;
    public OperatorEventsService(EventRepository events, EventOperatorAssignmentRepository assignments,
            OperatorEligibilityLookup eligibility) {
        this.events = events; this.assignments = assignments; this.eligibility = eligibility;
    }
    public List<OperatorEventResponse> list(UUID actorId) {
        requireActive(actorId);
        var ids = assignments.findByOperatorUserIdOrderByCreatedAtAscIdAsc(actorId).stream()
                .map(assignment -> assignment.getEventId()).distinct().toList();
        if (ids.isEmpty()) return List.of();
        return events.findByIdInOrderByEventStartAtAscIdAsc(ids).stream().map(this::view).toList();
    }
    public OperatorEventResponse get(UUID eventId, UUID actorId) {
        requireActive(actorId);
        if (eventId == null || !assignments.existsByEventIdAndOperatorUserId(eventId, actorId)) {
            throw new AccessDeniedException("Active assignment to this event is required");
        }
        return events.findById(eventId).map(this::view).orElseThrow(() ->
                new BusinessException("operator.event_not_found", "Назначенное мероприятие не найдено."));
    }
    private void requireActive(UUID actorId) {
        if (actorId == null || !eligibility.isActiveOperator(actorId)) {
            throw new AccessDeniedException("Active global OPERATOR role is required");
        }
    }
    private OperatorEventResponse view(EventEntity event) {
        return new OperatorEventResponse(event.getId(), event.getTitle(), event.getStatus(),
                event.getEventStartAt(), event.getEventEndAt(), event.getPublicSlug());
    }
}
