package com.acme.sportplatform.registrations.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.competition.DisciplineDisplayLookup;
import com.acme.sportplatform.events.EventDisplayLookup;
import com.acme.sportplatform.registrations.api.RegistrationResponse;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository;

@Service
@Transactional(readOnly = true)
public class ListMyRegistrationsUseCase {
    private final RegistrationRepository registrationRepository;
    private final RegistrationMapper registrationMapper;
    private final EventDisplayLookup events;
    private final DisciplineDisplayLookup disciplines;

    public ListMyRegistrationsUseCase(
            RegistrationRepository registrationRepository,
            RegistrationMapper registrationMapper,
            EventDisplayLookup events,
            DisciplineDisplayLookup disciplines
    ) {
        this.registrationRepository = registrationRepository;
        this.registrationMapper = registrationMapper;
        this.events = events;
        this.disciplines = disciplines;
    }

    public List<RegistrationResponse> execute(UUID participantUserId) {
        Objects.requireNonNull(participantUserId, "Participant ID is required");
        var own = registrationRepository.findByParticipantUserId(participantUserId);
        if (own.isEmpty()) return List.of();

        var eventIds = own.stream().map(item -> item.getEventId())
                .collect(Collectors.toUnmodifiableSet());
        var disciplineIds = own.stream().map(item -> item.getEventDisciplineId())
                .collect(Collectors.toUnmodifiableSet());
        var titles = events.titles(eventIds);
        var labels = disciplines.labels(disciplineIds);

        return own.stream().map(item -> {
            var label = labels.get(item.getEventDisciplineId());
            String disciplineName = label != null
                    && Objects.equals(item.getEventId(), label.eventId())
                    ? label.name() : null;
            return registrationMapper.toResponse(
                    item, titles.get(item.getEventId()), disciplineName);
        }).toList();
    }
}
