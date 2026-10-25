package com.acme.sportplatform.events.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.events.EventOperationAccess;
import com.acme.sportplatform.events.api.EventOperatorAssignmentResponse;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventOperatorAssignmentEntity;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventOperatorAssignmentRepository;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import com.acme.sportplatform.identity.OperatorEligibilityLookup;
import com.acme.sportplatform.organizations.OrganizationManagementAccess;

@Service
@Transactional(readOnly = true)
public class EventOperatorService implements EventOperationAccess {

    private final EventRepository eventRepository;
    private final EventOperatorAssignmentRepository assignmentRepository;
    private final OrganizationManagementAccess organizationManagementAccess;
    private final OperatorEligibilityLookup operatorEligibilityLookup;

    public EventOperatorService(
            EventRepository eventRepository,
            EventOperatorAssignmentRepository assignmentRepository,
            OrganizationManagementAccess organizationManagementAccess,
            OperatorEligibilityLookup operatorEligibilityLookup
    ) {
        this.eventRepository = eventRepository;
        this.assignmentRepository = assignmentRepository;
        this.organizationManagementAccess = organizationManagementAccess;
        this.operatorEligibilityLookup = operatorEligibilityLookup;
    }

    public List<EventOperatorAssignmentResponse> list(
            UUID eventId,
            UUID currentUserId,
            boolean platformAdmin
    ) {
        requireManagementAccess(
                eventId,
                currentUserId,
                platformAdmin
        );

        return assignmentRepository
                .findByEventIdOrderByCreatedAtAscIdAsc(eventId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void assign(
            UUID eventId,
            UUID operatorUserId,
            UUID currentUserId,
            boolean platformAdmin
    ) {
        requireManagementAccess(
                eventId,
                currentUserId,
                platformAdmin
        );

        if (!operatorEligibilityLookup.isActiveOperator(operatorUserId)) {
            throw new BusinessException(
                    "events.operator_not_eligible",
                    "Назначить можно только активного пользователя "
                            + "с ролью OPERATOR."
            );
        }

        assignmentRepository.insertIfAbsent(
                UUID.randomUUID(),
                eventId,
                operatorUserId,
                currentUserId,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    @Transactional
    public void remove(
            UUID eventId,
            UUID operatorUserId,
            UUID currentUserId,
            boolean platformAdmin
    ) {
        requireManagementAccess(
                eventId,
                currentUserId,
                platformAdmin
        );

        assignmentRepository.deleteAssignment(
                eventId,
                operatorUserId
        );
    }

    @Override
    public boolean canManageEvent(UUID eventId, UUID userId) {
        if (eventId == null || userId == null) {
            return false;
        }

        return eventRepository.findById(eventId)
                .map(event -> organizationManagementAccess
                        .canManageOrganization(
                                event.getOrganizationId(),
                                userId
                        ))
                .orElse(false);
    }

    @Override
    public boolean isAssignedActiveOperator(
            UUID eventId,
            UUID userId
    ) {
        if (eventId == null || userId == null) {
            return false;
        }

        return assignmentRepository.existsByEventIdAndOperatorUserId(
                eventId,
                userId
        ) && operatorEligibilityLookup.isActiveOperator(userId);
    }

    private void requireManagementAccess(
            UUID eventId,
            UUID currentUserId,
            boolean platformAdmin
    ) {
        if (currentUserId == null) {
            throw new AccessDeniedException(
                    "Authenticated user ID is required"
            );
        }

        EventEntity event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(
                        "events.event_not_found",
                        "Event not found"
                ));

        if (platformAdmin) {
            return;
        }

        if (!organizationManagementAccess.canManageOrganization(
                event.getOrganizationId(),
                currentUserId
        )) {
            throw new AccessDeniedException(
                    "You cannot manage operators for this event"
            );
        }
    }

    private EventOperatorAssignmentResponse toResponse(
            EventOperatorAssignmentEntity assignment
    ) {
        return new EventOperatorAssignmentResponse(
                assignment.getId(),
                assignment.getEventId(),
                assignment.getOperatorUserId(),
                assignment.getAssignedByUserId(),
                assignment.getCreatedAt()
        );
    }
}