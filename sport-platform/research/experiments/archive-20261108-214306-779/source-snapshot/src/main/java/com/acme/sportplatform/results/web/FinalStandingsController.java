package com.acme.sportplatform.results.web;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.acme.sportplatform.results.api.*;
import com.acme.sportplatform.results.application.FinalStandingsService;
@RestController
public class FinalStandingsController {
    private final FinalStandingsService service;
    public FinalStandingsController(FinalStandingsService service){this.service=service;}
    @PostMapping("/api/v1/events/{eventId}/disciplines/{eventDisciplineId}/finalizations")
    @PreAuthorize("@resultsAccess.canManageDiscipline(#eventDisciplineId, authentication)")
    public FinalStandingsResponse confirm(@PathVariable UUID eventId,@PathVariable UUID eventDisciplineId,
            @RequestBody @Valid ConfirmFinalStandingsRequest request) {
        return service.confirm(eventId,eventDisciplineId,request);
    }
    @PostMapping("/api/v1/events/{eventId}/disciplines/{eventDisciplineId}/finalizations/{id}/revoke")
    @PreAuthorize("@resultsAccess.canManageDiscipline(#eventDisciplineId, authentication)")
    public FinalStandingsResponse revoke(@PathVariable UUID eventId,@PathVariable UUID eventDisciplineId,
            @PathVariable UUID id,@RequestBody @Valid RevokeFinalStandingsRequest request) {
        return service.revoke(eventId,eventDisciplineId,id,request.reason());
    }
    @GetMapping("/api/v1/events/{eventId}/disciplines/{eventDisciplineId}/finalizations")
    @PreAuthorize("@resultsAccess.canManageDiscipline(#eventDisciplineId, authentication)")
    public List<FinalStandingsResponse> history(@PathVariable UUID eventId,@PathVariable UUID eventDisciplineId) {
        return service.history(eventId,eventDisciplineId);
    }
    @GetMapping("/api/v1/public/events/{eventId}/disciplines/{eventDisciplineId}/final-standings")
    public FinalStandingsResponse current(@PathVariable UUID eventId,@PathVariable UUID eventDisciplineId) {
        return service.current(eventId,eventDisciplineId);
    }
}
