package com.acme.sportplatform.events.application;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.events.api.EventOperatorAssignmentResponse;
import com.acme.sportplatform.identity.OperatorDirectoryLookup;

class EventOperatorDirectoryServiceTest {
    private final EventOperatorService assignments = mock(EventOperatorService.class);
    private final OperatorDirectoryLookup directory = mock(OperatorDirectoryLookup.class);
    private final EventOperatorDirectoryService service = new EventOperatorDirectoryService(assignments, directory);
    private final UUID event = UUID.randomUUID(), actor = UUID.randomUUID(), operator = UUID.randomUUID();
    private EventOperatorAssignmentResponse assignment() {
        return new EventOperatorAssignmentResponse(UUID.randomUUID(), event, operator, actor, OffsetDateTime.now());
    }
    @Test void cannotSearchUsersBeforeEventManagementCheck() {
        when(assignments.list(event, actor, false)).thenThrow(new AccessDeniedException("denied"));
        assertThrows(AccessDeniedException.class, () -> service.candidates(event, "operator@example.com", actor, false));
        verifyNoInteractions(directory);
    }
    @Test void cannotEnrichMembersBeforeEventManagementCheck() {
        when(assignments.list(event, actor, false)).thenThrow(new AccessDeniedException("denied"));
        assertThrows(AccessDeniedException.class, () -> service.members(event, actor, false));
        verifyNoInteractions(directory);
    }
    @Test void rejectsInvalidEmailWithoutQueryingIdentity() {
        when(assignments.list(event, actor, false)).thenReturn(List.of());
        for (String email : List.of("", "operator", "a@@example.com", "a b@example.com", "a".repeat(255)+"@b")) {
            assertThrows(BusinessException.class, () -> service.candidates(event, email, actor, false));
        }
        verifyNoInteractions(directory);
    }
    @Test void searchesExactTrimmedEmailAndMarksAlreadyAssigned() {
        when(assignments.list(event, actor, false)).thenReturn(List.of(assignment()));
        when(directory.findActiveByExactEmail("Operator@example.com")).thenReturn(Optional.of(
                new OperatorDirectoryLookup.OperatorView(operator, "Operator@example.com", "Иван Оператор", true)));
        var result = service.candidates(event, " Operator@example.com ", actor, false);
        assertEquals(1, result.size()); assertTrue(result.get(0).assigned());
        assertEquals(operator, result.get(0).userId());
    }
    @Test void missingAndIneligibleCandidatesAreEmpty() {
        when(assignments.list(event, actor, false)).thenReturn(List.of());
        when(directory.findActiveByExactEmail("missing@example.com")).thenReturn(Optional.empty());
        assertTrue(service.candidates(event, "missing@example.com", actor, false).isEmpty());
    }
    @Test void enrichesOnlyAssignedUsersAndPreservesAssignmentMetadata() {
        var saved = assignment();
        when(assignments.list(event, actor, true)).thenReturn(List.of(saved));
        when(directory.findByIds(List.of(operator))).thenReturn(Map.of(operator,
                new OperatorDirectoryLookup.OperatorView(operator, "operator@example.com", "Иван Оператор", false)));
        var row = service.members(event, actor, true).get(0);
        assertEquals(saved.id(), row.id());assertEquals(saved.createdAt(), row.createdAt());
        assertEquals(actor, row.assignedByUserId());assertEquals("Иван Оператор", row.displayName());assertFalse(row.eligible());
        verify(directory).findByIds(List.of(operator));
    }
    @Test void deletedUserDoesNotHideTheAssignment() {
        when(assignments.list(event, actor, false)).thenReturn(List.of(assignment()));
        when(directory.findByIds(List.of(operator))).thenReturn(Map.of());
        var row = service.members(event, actor, false).get(0);
        assertEquals(operator, row.operatorUserId());assertNull(row.email());assertFalse(row.eligible());
    }
}
