package com.acme.sportplatform.results.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.competition.EventDisciplineSummary;
import com.acme.sportplatform.results.api.PublicDisciplineResultsResponse;
import com.acme.sportplatform.results.api.PublicDisciplineStandingsResponse;

class GetPublicDisciplineStandingsUseCaseTest {

    private EventDisciplineLookup disciplineLookup;
    private GetPublicDisciplineResultsUseCase publicResults;
    private GetPublicDisciplineStandingsUseCase useCase;

    private UUID eventId;
    private UUID disciplineId;

    @BeforeEach
    void setUp() {
        disciplineLookup = mock(EventDisciplineLookup.class);
        publicResults = mock(GetPublicDisciplineResultsUseCase.class);

        useCase = new GetPublicDisciplineStandingsUseCase(
                disciplineLookup,
                publicResults
        );

        eventId = UUID.randomUUID();
        disciplineId = UUID.randomUUID();
    }

    @Test
    void shouldCombineHeatsAndPreserveUnitPlaces() {
        givenDiscipline("TIME", "ASC");

        givenUnits(
                unit(
                        1,
                        entry(1, "Ivan", "30.00", "TIME", "VALID")
                ),
                unit(
                        2,
                        entry(1, "Anna", "28.50", "TIME", "VALID")
                )
        );

        PublicDisciplineStandingsResponse response =
                useCase.execute(eventId, disciplineId);

        assertEquals("Anna", response.entries().get(0).participantName());
        assertEquals(1, response.entries().get(0).overallPlace());
        assertEquals(1, response.entries().get(0).unitPlace());

        assertEquals("Ivan", response.entries().get(1).participantName());
        assertEquals(2, response.entries().get(1).overallPlace());
        assertEquals(1, response.entries().get(1).unitPlace());
    }

    @Test
    void shouldAssignSharedPlacesForEquivalentTimes() {
        givenDiscipline("TIME", "ASC");

        givenUnits(
                unit(
                        1,
                        entry(1, "Ivan", "1:08.54", "TIME", "VALID"),
                        entry(2, "Oleg", "70.00", "TIME", "VALID")
                ),
                unit(
                        2,
                        entry(1, "Anna", "68.540", "TIME", "VALID")
                )
        );

        PublicDisciplineStandingsResponse response =
                useCase.execute(eventId, disciplineId);

        assertEquals("COMPETITION", response.tiePolicy());

        assertEquals(
                List.of(1, 1, 3),
                response.entries().stream()
                        .map(PublicDisciplineStandingsResponse.EntryView::overallPlace)
                        .toList()
        );
    }

    @Test
    void shouldRankDescendingNumericResults() {
        givenDiscipline("POINTS", "DESC");

        givenUnits(
                unit(
                        1,
                        entry(1, "Ivan", "10", "POINTS", "VALID")
                ),
                unit(
                        2,
                        entry(1, "Anna", "15", "POINTS", "VALID")
                )
        );

        PublicDisciplineStandingsResponse response =
                useCase.execute(eventId, disciplineId);

        assertEquals("Anna", response.entries().get(0).participantName());
        assertEquals(1, response.entries().get(0).overallPlace());

        assertEquals("Ivan", response.entries().get(1).participantName());
        assertEquals(2, response.entries().get(1).overallPlace());
    }

    @Test
    void shouldPutSpecialStatusesAfterRankedResultsWithoutPlaces() {
        givenDiscipline("TIME", "ASC");

        givenUnits(
                unit(
                        1,
                        entry(null, "DNS participant", null, "TIME", "DID_NOT_START"),
                        entry(1, "Finisher", "28.11", "TIME", "VALID"),
                        entry(null, "DSQ participant", null, "TIME", "DISQUALIFIED"),
                        entry(null, "DNF participant", null, "TIME", "DID_NOT_FINISH")
                )
        );

        PublicDisciplineStandingsResponse response =
                useCase.execute(eventId, disciplineId);

        assertEquals(4, response.entries().size());
        assertEquals("Finisher", response.entries().get(0).participantName());
        assertEquals(1, response.entries().get(0).overallPlace());

        response.entries().stream()
                .skip(1)
                .forEach(entry -> assertNull(entry.overallPlace()));
    }

    @Test
    void shouldReturnEmptyStandingsWhenNoPublishedUnitsExist() {
        givenDiscipline("TIME", "ASC");
        givenUnits();

        PublicDisciplineStandingsResponse response =
                useCase.execute(eventId, disciplineId);

        assertEquals(eventId, response.eventId());
        assertEquals(disciplineId, response.eventDisciplineId());
        assertEquals(List.of(), response.entries());
    }

    @Test
    void shouldRejectWrongEventIdBeforeLoadingResults() {
        givenDiscipline("TIME", "ASC");

        assertThrows(
                BusinessException.class,
                () -> useCase.execute(UUID.randomUUID(), disciplineId)
        );

        verifyNoInteractions(publicResults);
    }

    @Test
    void shouldRejectInvalidTime() {
        givenDiscipline("TIME", "ASC");

        givenUnits(
                unit(
                        1,
                        entry(1, "Ivan", "1:75.00", "TIME", "VALID")
                )
        );

        assertThrows(
                BusinessException.class,
                () -> useCase.execute(eventId, disciplineId)
        );
    }

    private void givenDiscipline(String resultType, String rankingStrategy) {
        EventDisciplineSummary discipline = new EventDisciplineSummary(
                disciplineId,
                eventId,
                "test-discipline",
                "Test discipline",
                "INDIVIDUAL",
                "HEAT",
                resultType,
                rankingStrategy,
                40,
                BigDecimal.ZERO,
                "RUB",
                "{}"
        );

        when(disciplineLookup.findById(disciplineId))
                .thenReturn(Optional.of(discipline));
    }

    private void givenUnits(
            PublicDisciplineResultsResponse.UnitView... units
    ) {
        when(publicResults.execute(eventId, disciplineId))
                .thenReturn(new PublicDisciplineResultsResponse(
                        eventId,
                        disciplineId,
                        List.of(units)
                ));
    }

    private PublicDisciplineResultsResponse.UnitView unit(
            int sequenceNumber,
            PublicDisciplineResultsResponse.EntryView... entries
    ) {
        return new PublicDisciplineResultsResponse.UnitView(
                UUID.randomUUID(),
                "Heat " + sequenceNumber,
                sequenceNumber,
                List.of(entries)
        );
    }

    private PublicDisciplineResultsResponse.EntryView entry(
            Integer place,
            String participantName,
            String rawValue,
            String resultType,
            String status
    ) {
        return new PublicDisciplineResultsResponse.EntryView(
                place,
                participantName,
                "Aqua Team",
                rawValue,
                resultType,
                status
        );
    }
}