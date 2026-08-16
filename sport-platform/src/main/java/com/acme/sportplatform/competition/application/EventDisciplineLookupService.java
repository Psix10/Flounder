package com.acme.sportplatform.competition.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.competition.EventDisciplineLookupResult;
import com.acme.sportplatform.competition.domain.EventDisciplineStatus;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineEntity;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineRepository;

@Service
@Transactional(readOnly = true)
public class EventDisciplineLookupService
        implements EventDisciplineLookup {

    private final EventDisciplineRepository eventDisciplineRepository;

    public EventDisciplineLookupService(
            EventDisciplineRepository eventDisciplineRepository
    ) {
        this.eventDisciplineRepository = eventDisciplineRepository;
    }

    @Override
    public EventDisciplineLookupResult getPublishedById(
            UUID eventDisciplineId
    ) {
        EventDisciplineEntity discipline = eventDisciplineRepository
                .findById(eventDisciplineId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Event discipline not found: " + eventDisciplineId
                ));

        if (!EventDisciplineStatus.PUBLISHED.name()
                .equals(discipline.getStatus())) {
            throw new IllegalArgumentException(
                    "Event discipline is not published: " + eventDisciplineId
            );
        }

        return new EventDisciplineLookupResult(
                discipline.getId(),
                discipline.getEventId(),
                discipline.getDisciplineTemplateId(),
                discipline.getCode(),
                discipline.getName(),
                discipline.getCompetitionFormat(),
                discipline.getUnitType(),
                discipline.getResultType(),
                discipline.getRankingStrategy(),
                discipline.getParticipantLimit(),
                discipline.getEntryFeeAmount(),
                discipline.getEntryFeeCurrency(),
                discipline.getSettingsJson()
        );
    }
}