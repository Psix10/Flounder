package com.acme.sportplatform.protocol.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.competition.EventDisciplineLookupResult;
import com.acme.sportplatform.protocol.EventProtocolAccess;

@Service
@Transactional(readOnly = true)
public class EventProtocolAccessService implements EventProtocolAccess {

    private final EventDisciplineLookup eventDisciplineLookup;

    public EventProtocolAccessService(EventDisciplineLookup eventDisciplineLookup) {
        this.eventDisciplineLookup = eventDisciplineLookup;
    }

    @Override
    public PublishedEventDisciplineView getPublishedEventDiscipline(UUID eventDisciplineId) {
        EventDisciplineLookupResult result;
        try {
            result = eventDisciplineLookup.getPublishedById(eventDisciplineId);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    "protocol.event_discipline_not_found",
                    "Published event discipline not found"
            );
        }

        return new PublishedEventDisciplineView(
                result.id(),
                result.settingsJson()
        );
    }
}