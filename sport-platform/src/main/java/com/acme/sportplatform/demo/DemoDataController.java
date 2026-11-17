package com.acme.sportplatform.demo;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/events/{eventId}/disciplines/{disciplineId}/demo-registrations")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER')")
public class DemoDataController {
    private final DemoDataService service;
    public DemoDataController(DemoDataService service) { this.service = service; }
    @GetMapping
    public DemoDataService.Plan preview(@PathVariable UUID eventId, @PathVariable UUID disciplineId) {
        return service.preview(eventId, disciplineId);
    }
    @PostMapping
    public DemoDataService.Receipt create(@PathVariable UUID eventId, @PathVariable UUID disciplineId,
            @RequestBody @Valid DemoDataService.Request request) {
        return service.create(eventId, disciplineId, request);
    }
}
