package com.acme.sportplatform.events.application;

import java.util.List;

import org.springframework.stereotype.Component;

import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.competition.EventDisciplineSummary;
import com.acme.sportplatform.events.api.EventResponse;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;

@Component
public class EventMapper {

    private final EventDisciplineLookup eventDisciplineLookup;

    public EventMapper(EventDisciplineLookup eventDisciplineLookup) {
        this.eventDisciplineLookup = eventDisciplineLookup;
    }

    public EventResponse toResponse(EventEntity entity) {
        List<EventResponse.DisciplineResponse> disciplines =
                eventDisciplineLookup.findByEventId(entity.getId())
                        .stream()
                        .map(this::toDisciplineResponse)
                        .toList();

        return new EventResponse(
                entity.getId(),
                entity.getOrganizationId(),
                entity.getVenueId(),
                entity.getSportId(),
                entity.getRegulationVersionId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getRegistrationOpenAt(),
                entity.getRegistrationCloseAt(),
                entity.getEventStartAt(),
                entity.getEventEndAt(),
                entity.getStatus(),
                entity.getPublicSlug(),
                entity.getSettingsJson(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                disciplines
        );
    }

    private EventResponse.DisciplineResponse toDisciplineResponse(
            EventDisciplineSummary discipline
    ) {
        return new EventResponse.DisciplineResponse(
                discipline.id(),
                discipline.code(),
                discipline.name(),
                discipline.competitionFormat(),
                discipline.unitType(),
                discipline.resultType(),
                discipline.rankingStrategy(),
                discipline.participantLimit(),
                discipline.entryFeeAmount(),
                discipline.entryFeeCurrency(),
                discipline.settingsJson()
        );
    }
}