package com.acme.sportplatform.competition.application;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineEntity;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineRepository;
import com.acme.sportplatform.events.EventOperationAccess;

class OperatorDisciplineQueryServiceTest {
    private final EventDisciplineRepository disciplines = mock(EventDisciplineRepository.class);
    private final EventOperationAccess access = mock(EventOperationAccess.class);
    private final OperatorDisciplineQueryService service = new OperatorDisciplineQueryService(disciplines, access);
    private final UUID eventId = UUID.randomUUID(), actor = UUID.randomUUID();
    private EventDisciplineEntity row(String name, String status) {
        var row = mock(EventDisciplineEntity.class);when(row.getId()).thenReturn(UUID.randomUUID());
        when(row.getEventId()).thenReturn(eventId);when(row.getName()).thenReturn(name);when(row.getStatus()).thenReturn(status);
        when(row.getCompetitionFormat()).thenReturn("INDIVIDUAL");when(row.getUnitType()).thenReturn("HEAT");when(row.getResultType()).thenReturn("TIME");return row;
    }
    @Test void missingUserIsDeniedBeforeAnyRead() {
        assertThrows(AccessDeniedException.class, () -> service.list(eventId, null));verifyNoInteractions(disciplines, access);
    }
    @Test void missingEventIsDeniedBeforeAnyRead() {
        assertThrows(AccessDeniedException.class, () -> service.list(null, actor));verifyNoInteractions(disciplines, access);
    }
    @Test void foreignOrRevokedAssignmentDoesNotExposeDisciplines() {
        when(access.isAssignedActiveOperator(eventId, actor)).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> service.list(eventId, actor));verifyNoInteractions(disciplines);
    }
    @Test void authorizedEventCanHaveNoDisciplines() {
        when(access.isAssignedActiveOperator(eventId, actor)).thenReturn(true);
        when(disciplines.findByEventId(eventId)).thenReturn(List.of());assertTrue(service.list(eventId, actor).isEmpty());
    }
    @Test void onlySelectedEventIsRequestedAndUnpublishedDisciplineIsVisible() {
        var row = row("100 m", "DRAFT");
        when(access.isAssignedActiveOperator(eventId, actor)).thenReturn(true);
        when(disciplines.findByEventId(eventId)).thenReturn(List.of(row));
        var result = service.list(eventId, actor);assertEquals("DRAFT", result.get(0).status());assertEquals(eventId, result.get(0).eventId());
        verify(disciplines).findByEventId(eventId);verify(disciplines, never()).findAll();
    }
    @Test void listOrderIsDeterministicByName() {
        var b = row("B", "PUBLISHED");var a = row("A", "PUBLISHED");
        when(access.isAssignedActiveOperator(eventId, actor)).thenReturn(true);
        when(disciplines.findByEventId(eventId)).thenReturn(List.of(b, a));
        assertEquals(List.of("A", "B"), service.list(eventId, actor).stream().map(item -> item.name()).toList());
    }
    @Test void accessIsCheckedOnEveryRead() {
        when(access.isAssignedActiveOperator(eventId, actor)).thenReturn(true, false);
        when(disciplines.findByEventId(eventId)).thenReturn(List.of());assertTrue(service.list(eventId, actor).isEmpty());
        assertThrows(AccessDeniedException.class, () -> service.list(eventId, actor));verify(disciplines, times(1)).findByEventId(eventId);
    }
}
