package com.acme.sportplatform.registrations.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.acme.sportplatform.competition.DisciplineDisplayLookup;
import com.acme.sportplatform.events.EventDisplayLookup;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationEntity;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

class ListMyRegistrationLabelsTest {
    private final RegistrationRepository registrations = mock(RegistrationRepository.class);
    private final EventDisplayLookup events = mock(EventDisplayLookup.class);
    private final DisciplineDisplayLookup disciplines = mock(DisciplineDisplayLookup.class);
    private final RegistrationMapper mapper = new RegistrationMapper(new ObjectMapper());
    private final ListMyRegistrationsUseCase useCase =
            new ListMyRegistrationsUseCase(registrations, mapper, events, disciplines);

    private RegistrationEntity registration(UUID eventId, UUID disciplineId) {
        var entity = new RegistrationEntity();
        entity.setId(UUID.randomUUID());
        entity.setEventId(eventId);
        entity.setEventDisciplineId(disciplineId);
        entity.setParticipantSnapshot("{\"lastName\":\"Иванов\"}");
        entity.setRegistrationMeta("{}");
        return entity;
    }

    @Test
    void emptyOwnListDoesNotLoadLabels() {
        UUID participant = UUID.randomUUID();
        when(registrations.findByParticipantUserId(participant)).thenReturn(List.of());

        assertEquals(List.of(), useCase.execute(participant));
        verifyNoInteractions(events, disciplines);
    }

    @Test
    void loadsDistinctOwnScopesOnceAndPreservesOrderAndSnapshots() {
        UUID participant = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID disciplineId = UUID.randomUUID();
        var first = registration(eventId, disciplineId);
        var second = registration(eventId, disciplineId);
        when(registrations.findByParticipantUserId(participant))
                .thenReturn(List.of(first, second));
        when(events.titles(Set.of(eventId))).thenReturn(Map.of(eventId, "Кубок"));
        when(disciplines.labels(Set.of(disciplineId))).thenReturn(Map.of(
                disciplineId, new DisciplineDisplayLookup.Label(eventId, "50 м")));

        var result = useCase.execute(participant);

        assertEquals(List.of(first.getId(), second.getId()),
                result.stream().map(item -> item.id()).toList());
        assertEquals("Кубок", result.get(0).eventTitle());
        assertEquals("50 м", result.get(0).disciplineName());
        assertEquals("Иванов", result.get(0).participantSnapshot().path("lastName").asText());
        verify(events).titles(Set.of(eventId));
        verify(disciplines).labels(Set.of(disciplineId));
        verifyNoMoreInteractions(events, disciplines);
    }

    @Test
    void missingLabelsDoNotRemoveOwnRegistration() {
        UUID participant = UUID.randomUUID();
        var entity = registration(UUID.randomUUID(), UUID.randomUUID());
        when(registrations.findByParticipantUserId(participant)).thenReturn(List.of(entity));
        when(events.titles(anySet())).thenReturn(Map.of());
        when(disciplines.labels(anySet())).thenReturn(Map.of());

        var result = useCase.execute(participant);

        assertEquals(1, result.size());
        assertEquals(entity.getId(), result.get(0).id());
        org.junit.jupiter.api.Assertions.assertNull(result.get(0).eventTitle());
        org.junit.jupiter.api.Assertions.assertNull(result.get(0).disciplineName());
    }

    @Test
    void disciplineFromAnotherEventIsNotUsedAsDisplayLabel() {
        UUID participant = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID disciplineId = UUID.randomUUID();
        when(registrations.findByParticipantUserId(participant))
                .thenReturn(List.of(registration(eventId, disciplineId)));
        when(events.titles(anySet())).thenReturn(Map.of(eventId, "Кубок"));
        when(disciplines.labels(anySet())).thenReturn(Map.of(
                disciplineId,
                new DisciplineDisplayLookup.Label(UUID.randomUUID(), "Чужая дисциплина")));

        var result = useCase.execute(participant);

        assertEquals("Кубок", result.get(0).eventTitle());
        org.junit.jupiter.api.Assertions.assertNull(result.get(0).disciplineName());
    }

    @Test
    void ordinaryMapperDoesNotInventDisplayLabels() {
        var result = mapper.toResponse(registration(UUID.randomUUID(), UUID.randomUUID()));
        org.junit.jupiter.api.Assertions.assertNull(result.eventTitle());
        org.junit.jupiter.api.Assertions.assertNull(result.disciplineName());
    }
}
