package com.acme.sportplatform.results.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.competition.EventDisciplineSummary;
import com.acme.sportplatform.results.api.PublicDisciplineStandingsResponse;
import com.acme.sportplatform.results.infrastructure.jdbc.SqlDisciplineStandingsReader;

class GetPublicDisciplineStandingsUseCaseTest {
    private EventDisciplineLookup lookup;
    private SqlDisciplineStandingsReader reader;
    private GetPublicDisciplineStandingsUseCase useCase;
    private final UUID eventId = UUID.randomUUID();
    private final UUID disciplineId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lookup = mock(EventDisciplineLookup.class);
        reader = mock(SqlDisciplineStandingsReader.class);
        useCase = new GetPublicDisciplineStandingsUseCase(lookup, reader);
    }

    private void discipline(String type, String strategy) {
        EventDisciplineSummary d = mock(EventDisciplineSummary.class);
        when(d.eventId()).thenReturn(eventId);
        when(d.resultType()).thenReturn(type);
        when(d.rankingStrategy()).thenReturn(strategy);
        when(lookup.findById(disciplineId)).thenReturn(Optional.of(d));
    }

    @Test
    void shouldReturnPlacesCalculatedByDatabaseWithoutRenumbering() {
        discipline("TIME", "ASC");
        List<PublicDisciplineStandingsResponse.EntryView> entries = List.of(
                new PublicDisciplineStandingsResponse.EntryView(
                        2, UUID.randomUUID(), "Heat 1", 1,
                        "First", "Club", "58.42", "TIME", "VALID", "A", "G", 1),
                new PublicDisciplineStandingsResponse.EntryView(
                        2, UUID.randomUUID(), "Heat 2", 1,
                        "Second", "Club", "00:58.42", "TIME", "VALID", "A", "G", 1)
        );
        when(reader.execute(eventId, disciplineId, "TIME", "ASC")).thenReturn(entries);
        var result = useCase.execute(eventId, disciplineId);
        assertEquals(eventId, result.eventId());
        assertEquals(disciplineId, result.eventDisciplineId());
        assertEquals("COMPETITION", result.tiePolicy());
        assertEquals(entries, result.entries());
        verify(reader).execute(eventId, disciplineId, "TIME", "ASC");
    }

    @Test
    void shouldNormalizePolicyBeforeCallingDatabase() {
        discipline(" points ", " desc ");
        when(reader.execute(eventId, disciplineId, "POINTS", "DESC")).thenReturn(List.of());
        var result = useCase.execute(eventId, disciplineId);
        assertEquals("POINTS", result.resultType());
        assertEquals("DESC", result.rankingStrategy());
        verify(reader).execute(eventId, disciplineId, "POINTS", "DESC");
    }

    @Test
    void shouldReturnEmptyStandings() {
        discipline("TIME", "ASC");
        when(reader.execute(eventId, disciplineId, "TIME", "ASC")).thenReturn(List.of());
        assertTrue(useCase.execute(eventId, disciplineId).entries().isEmpty());
    }

    @Test
    void shouldRejectWrongEventBeforeCallingDatabase() {
        discipline("TIME", "ASC");
        assertThrows(BusinessException.class, () -> useCase.execute(UUID.randomUUID(), disciplineId));
        verifyNoInteractions(reader);
    }

    @Test
    void shouldRejectMissingDisciplineBeforeCallingDatabase() {
        when(lookup.findById(disciplineId)).thenReturn(Optional.empty());
        var error = assertThrows(BusinessException.class, () -> useCase.execute(eventId, disciplineId));
        assertEquals("results.public_not_found", error.getCode());
        verifyNoInteractions(reader);
    }

    @Test
    void shouldRejectBlankResultTypeBeforeCallingDatabase() {
        discipline(" ", "ASC");
        var error = assertThrows(BusinessException.class, () -> useCase.execute(eventId, disciplineId));
        assertEquals("results.invalid_result_type", error.getCode());
        verifyNoInteractions(reader);
    }

    @Test
    void shouldRejectInvalidStrategyBeforeCallingDatabase() {
        discipline("TIME", "SIDEWAYS");
        var error = assertThrows(BusinessException.class, () -> useCase.execute(eventId, disciplineId));
        assertEquals("results.invalid_ranking_strategy", error.getCode());
        verifyNoInteractions(reader);
    }
}
