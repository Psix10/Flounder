package com.acme.sportplatform.events.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import com.acme.sportplatform.organizations.VenueTimezoneLookup;

class PublicEventTimezoneTest {
    private final EventRepository events = mock(EventRepository.class);
    private final VenueTimezoneLookup venues = mock(VenueTimezoneLookup.class);
    private final EventPublicLookupService service =
            new EventPublicLookupService(events, venues);

    private EventEntity event(UUID venueId) {
        var event = new EventEntity();
        event.setId(UUID.randomUUID());
        event.setVenueId(venueId);
        event.setTitle("Кубок");
        event.setStatus("PUBLISHED");
        event.setPublicSlug("cup-" + event.getId());
        return event;
    }

    @Test
    void listLoadsDistinctVenuesInOneLookupAndPreservesOrder() {
        UUID firstVenue = UUID.randomUUID();
        UUID secondVenue = UUID.randomUUID();
        var first = event(firstVenue);
        var second = event(secondVenue);
        var third = event(firstVenue);
        when(events.findByStatusInOrderByEventStartAtAsc(anyCollection()))
                .thenReturn(List.of(first, second, third));
        when(venues.timezones(Set.of(firstVenue, secondVenue)))
                .thenReturn(Map.of(
                        firstVenue, "Asia/Vladivostok",
                        secondVenue, "Europe/Moscow"));

        var result = service.listPublicEvents();

        assertEquals(List.of(first.getId(), second.getId(), third.getId()),
                result.stream().map(item -> item.id()).toList());
        assertEquals(List.of("Asia/Vladivostok", "Europe/Moscow", "Asia/Vladivostok"),
                result.stream().map(item -> item.venueTimezone()).toList());
        verify(venues).timezones(Set.of(firstVenue, secondVenue));
        verifyNoMoreInteractions(venues);
    }

    @Test
    void emptyListDoesNotLoadVenues() {
        when(events.findByStatusInOrderByEventStartAtAsc(anyCollection()))
                .thenReturn(List.of());
        assertTrue(service.listPublicEvents().isEmpty());
        verifyNoInteractions(venues);
    }

    @Test
    void detailsCarryStoredTimezoneWithoutChangingDates() {
        UUID venueId = UUID.randomUUID();
        var event = event(venueId);
        var start = java.time.OffsetDateTime.parse("2026-11-21T23:30:00Z");
        event.setEventStartAt(start);
        when(events.findByPublicSlugAndStatusIn(eq(event.getPublicSlug()), anyCollection()))
                .thenReturn(Optional.of(event));
        when(venues.timezones(Set.of(venueId)))
                .thenReturn(Map.of(venueId, "Asia/Vladivostok"));

        var result = service.getPublicBySlug(event.getPublicSlug());

        assertEquals("Asia/Vladivostok", result.venueTimezone());
        assertEquals(start, result.eventStartAt());
        assertEquals(start, event.getEventStartAt());
    }

    @Test
    void missingVenueDoesNotInventTimezoneOrHidePublicEvent() {
        var event = event(UUID.randomUUID());
        when(events.findByPublicSlugAndStatusIn(eq(event.getPublicSlug()), anyCollection()))
                .thenReturn(Optional.of(event));
        when(venues.timezones(anySet())).thenReturn(Map.of());

        assertNull(service.getPublicBySlug(event.getPublicSlug()).venueTimezone());
    }

    @Test
    void unavailableEventDoesNotTriggerVenueLookup() {
        when(events.findByPublicSlugAndStatusIn(eq("hidden"), anyCollection()))
                .thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.getPublicBySlug("hidden"));
        verifyNoInteractions(venues);
    }
}
