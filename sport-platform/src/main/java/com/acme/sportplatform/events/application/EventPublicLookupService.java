package com.acme.sportplatform.events.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.events.EventPublicLookup;
import com.acme.sportplatform.events.EventPublicLookupResult;
import com.acme.sportplatform.events.domain.EventStatus;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import com.acme.sportplatform.organizations.VenueTimezoneLookup;

@Service
@Transactional(readOnly = true)
public class EventPublicLookupService implements EventPublicLookup {
    private static final Set<String> PUBLIC_EVENT_STATUSES = Set.of(
            EventStatus.PUBLISHED.name(),
            EventStatus.REGISTRATION_OPEN.name(),
            EventStatus.REGISTRATION_CLOSED.name(),
            EventStatus.LIVE.name(),
            EventStatus.COMPLETED.name()
    );

    private final EventRepository eventRepository;
    private final VenueTimezoneLookup venues;

    public EventPublicLookupService(
            EventRepository eventRepository,
            VenueTimezoneLookup venues
    ) {
        this.eventRepository = eventRepository;
        this.venues = venues;
    }

    @Override
    public List<EventPublicLookupResult> listPublicEvents() {
        var events = eventRepository.findByStatusInOrderByEventStartAtAsc(
                PUBLIC_EVENT_STATUSES);
        if (events.isEmpty()) return List.of();

        var zones = loadTimezones(events);
        return events.stream().map(event -> toResult(
                event, event.getVenueId() == null ? null : zones.get(event.getVenueId())
        )).toList();
    }

    @Override
    public EventPublicLookupResult getPublicBySlug(String publicSlug) {
        EventEntity event = eventRepository.findByPublicSlugAndStatusIn(
                publicSlug, PUBLIC_EVENT_STATUSES
        ).orElseThrow(() -> new BusinessException(
                "events.public_not_found", "Public event not found"));

        var zones = loadTimezones(List.of(event));
        return toResult(
                event, event.getVenueId() == null ? null : zones.get(event.getVenueId()));
    }

    private Map<UUID, String> loadTimezones(List<EventEntity> events) {
        var ids = events.stream().map(EventEntity::getVenueId)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
        return ids.isEmpty() ? Map.of() : venues.timezones(ids);
    }

    private EventPublicLookupResult toResult(EventEntity event, String venueTimezone) {
        return new EventPublicLookupResult(
                event.getId(),
                event.getSportId(),
                event.getTitle(),
                event.getDescription(),
                event.getRegistrationOpenAt(),
                event.getRegistrationCloseAt(),
                event.getEventStartAt(),
                event.getEventEndAt(),
                event.getStatus(),
                event.getPublicSlug(),
                venueTimezone
        );
    }
}
