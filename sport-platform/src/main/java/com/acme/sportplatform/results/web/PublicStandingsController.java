package com.acme.sportplatform.results.web;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acme.sportplatform.results.api.PublicDisciplineStandingsResponse;
import com.acme.sportplatform.results.application.GetPublicDisciplineStandingsUseCase;

@RestController
@RequestMapping("/api/v1/public/events")
public class PublicStandingsController {

    private final GetPublicDisciplineStandingsUseCase
            getPublicDisciplineStandingsUseCase;

    public PublicStandingsController(
            GetPublicDisciplineStandingsUseCase getPublicDisciplineStandingsUseCase
    ) {
        this.getPublicDisciplineStandingsUseCase =
                getPublicDisciplineStandingsUseCase;
    }

    @GetMapping("/{eventId}/disciplines/{eventDisciplineId}/standings")
    public PublicDisciplineStandingsResponse getStandings(
            @PathVariable UUID eventId,
            @PathVariable UUID eventDisciplineId
    ) {
        return getPublicDisciplineStandingsUseCase.execute(
                eventId,
                eventDisciplineId
        );
    }
}