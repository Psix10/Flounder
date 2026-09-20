package com.acme.sportplatform.events.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acme.sportplatform.events.api.CreateEventRequest;
import com.acme.sportplatform.events.api.EventResponse;
import com.acme.sportplatform.events.application.CloseEventRegistrationUseCase;
import com.acme.sportplatform.events.application.CompleteEventUseCase;
import com.acme.sportplatform.events.application.CreateEventUseCase;
import com.acme.sportplatform.events.application.EventMapper;
import com.acme.sportplatform.events.application.OpenEventRegistrationUseCase;
import com.acme.sportplatform.events.application.PublishEventUseCase;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class EventController {

    private final CreateEventUseCase createEventUseCase;
    private final PublishEventUseCase publishEventUseCase;
    private final OpenEventRegistrationUseCase openEventRegistrationUseCase;
    private final CloseEventRegistrationUseCase closeEventRegistrationUseCase;
    private final CompleteEventUseCase completeEventUseCase;
    private final EventRepository eventRepository;
    private final EventMapper eventMapper;

    public EventController(
            CreateEventUseCase createEventUseCase,
            PublishEventUseCase publishEventUseCase,
            OpenEventRegistrationUseCase openEventRegistrationUseCase,
            CloseEventRegistrationUseCase closeEventRegistrationUseCase,
            CompleteEventUseCase completeEventUseCase,
            EventRepository eventRepository,
            EventMapper eventMapper
    ) {
        this.createEventUseCase = createEventUseCase;
        this.publishEventUseCase = publishEventUseCase;
        this.openEventRegistrationUseCase = openEventRegistrationUseCase;
        this.closeEventRegistrationUseCase = closeEventRegistrationUseCase;
        this.completeEventUseCase = completeEventUseCase;
        this.eventRepository = eventRepository;
        this.eventMapper = eventMapper;
    }

    @PostMapping("/events")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasRole('ORGANIZER')")
    public ResponseEntity<EventResponse> create(@RequestBody @Valid CreateEventRequest request) {
        return ResponseEntity.ok(createEventUseCase.execute(request));
    }

    @GetMapping("/events/{id}")
    public ResponseEntity<EventResponse> get(@PathVariable UUID id) {
        EventEntity entity = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found")); // можно заменить на BusinessException
        return ResponseEntity.ok(eventMapper.toResponse(entity));
    }

    @GetMapping("/events")
    public ResponseEntity<List<EventResponse>> list() {
        List<EventResponse> events = eventRepository.findAll().stream()
                .map(eventMapper::toResponse)
                .toList();
        return ResponseEntity.ok(events);
    }

    @PostMapping("/events/{id}/publish")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasRole('ORGANIZER')")
    public ResponseEntity<EventResponse> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(publishEventUseCase.execute(id));
    }

    @PostMapping("/events/{id}/open-registration")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasRole('ORGANIZER')")
    public ResponseEntity<EventResponse> openRegistration(@PathVariable UUID id) {
        return ResponseEntity.ok(openEventRegistrationUseCase.execute(id));
    }

    @PostMapping("/events/{id}/close-registration")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasRole('ORGANIZER')")
    public ResponseEntity<EventResponse> closeRegistration(@PathVariable UUID id) {
        return ResponseEntity.ok(closeEventRegistrationUseCase.execute(id));
    }

    @PostMapping("/events/{id}/complete")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasRole('ORGANIZER')")
    public ResponseEntity<EventResponse> complete(@PathVariable UUID id) {
        return ResponseEntity.ok(completeEventUseCase.execute(id));
    }
}