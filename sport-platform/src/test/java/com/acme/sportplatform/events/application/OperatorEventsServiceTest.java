package com.acme.sportplatform.events.application;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventOperatorAssignmentEntity;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventOperatorAssignmentRepository;
import com.acme.sportplatform.identity.OperatorEligibilityLookup;

class OperatorEventsServiceTest {
    private final EventRepository events = mock(EventRepository.class);
    private final EventOperatorAssignmentRepository assignments = mock(EventOperatorAssignmentRepository.class);
    private final OperatorEligibilityLookup eligibility = mock(OperatorEligibilityLookup.class);
    private final OperatorEventsService service = new OperatorEventsService(events, assignments, eligibility);
    private final UUID actor = UUID.randomUUID(), eventId = UUID.randomUUID();
    private EventOperatorAssignmentEntity assignment(UUID id) {
        var row = mock(EventOperatorAssignmentEntity.class);when(row.getEventId()).thenReturn(id);return row;
    }
    private EventEntity event(String status) {
        var row = mock(EventEntity.class);when(row.getId()).thenReturn(eventId);
        when(row.getTitle()).thenReturn("Assigned event");when(row.getStatus()).thenReturn(status);return row;
    }
    @Test void missingPrincipalCannotListEvents() {
        assertThrows(AccessDeniedException.class, () -> service.list(null));verifyNoInteractions(events, assignments, eligibility);
    }
    @Test void inactiveOrRoleRevokedOperatorCannotListEvents() {
        when(eligibility.isActiveOperator(actor)).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> service.list(actor));verifyNoInteractions(events, assignments);
    }
    @Test void noAssignmentsDoesNotReadGlobalEventCatalog() {
        when(eligibility.isActiveOperator(actor)).thenReturn(true);
        when(assignments.findByOperatorUserIdOrderByCreatedAtAscIdAsc(actor)).thenReturn(List.of());
        assertTrue(service.list(actor).isEmpty());verifyNoInteractions(events);
    }
    @Test void listReadsOnlyAssignedEventIds() {
        var assignment = assignment(eventId);var event = event("PUBLISHED");
        when(eligibility.isActiveOperator(actor)).thenReturn(true);
        when(assignments.findByOperatorUserIdOrderByCreatedAtAscIdAsc(actor)).thenReturn(List.of(assignment));
        when(events.findByIdInOrderByEventStartAtAscIdAsc(List.of(eventId))).thenReturn(List.of(event));
        assertEquals(eventId, service.list(actor).get(0).id());
        verify(events).findByIdInOrderByEventStartAtAscIdAsc(List.of(eventId));verify(events, never()).findAll();
    }
    @Test void duplicateLegacyAssignmentsDoNotDuplicateQueryIds() {
        var first = assignment(eventId);var second = assignment(eventId);
        when(eligibility.isActiveOperator(actor)).thenReturn(true);
        when(assignments.findByOperatorUserIdOrderByCreatedAtAscIdAsc(actor)).thenReturn(List.of(first, second));
        when(events.findByIdInOrderByEventStartAtAscIdAsc(List.of(eventId))).thenReturn(List.of());
        assertTrue(service.list(actor).isEmpty());verify(events).findByIdInOrderByEventStartAtAscIdAsc(List.of(eventId));
    }
    @Test void unpublishedAssignedEventIsNotLostInPublicCatalogFiltering() {
        var assignment = assignment(eventId);var event = event("DRAFT");
        when(eligibility.isActiveOperator(actor)).thenReturn(true);
        when(assignments.findByOperatorUserIdOrderByCreatedAtAscIdAsc(actor)).thenReturn(List.of(assignment));
        when(events.findByIdInOrderByEventStartAtAscIdAsc(List.of(eventId))).thenReturn(List.of(event));
        assertEquals("DRAFT", service.list(actor).get(0).status());
    }
    @Test void revokedOrForeignEventIsRejectedBeforeReadingDetails() {
        when(eligibility.isActiveOperator(actor)).thenReturn(true);
        when(assignments.existsByEventIdAndOperatorUserId(eventId, actor)).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> service.get(eventId, actor));verifyNoInteractions(events);
    }
    @Test void currentAssignmentAllowsReadingDetails() {
        var event = event("COMPLETED");when(eligibility.isActiveOperator(actor)).thenReturn(true);
        when(assignments.existsByEventIdAndOperatorUserId(eventId, actor)).thenReturn(true);
        when(events.findById(eventId)).thenReturn(Optional.of(event));
        assertEquals("COMPLETED", service.get(eventId, actor).status());
    }
    @Test void missingAssignedEventIsExplicitFailure() {
        when(eligibility.isActiveOperator(actor)).thenReturn(true);
        when(assignments.existsByEventIdAndOperatorUserId(eventId, actor)).thenReturn(true);
        when(events.findById(eventId)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.get(eventId, actor));
    }
    @Test void roleRevocationIsCheckedAgainForDetails() {
        when(eligibility.isActiveOperator(actor)).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> service.get(eventId, actor));verifyNoInteractions(events, assignments);
    }
}
