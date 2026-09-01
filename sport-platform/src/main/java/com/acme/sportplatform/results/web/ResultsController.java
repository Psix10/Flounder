package com.acme.sportplatform.results.web;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.acme.sportplatform.results.api.AssignRegistrationRequest;
import com.acme.sportplatform.results.api.CompetitionUnitDetailsResponse;
import com.acme.sportplatform.results.api.CompetitionUnitResponse;
import com.acme.sportplatform.results.api.CreateCompetitionUnitRequest;
import com.acme.sportplatform.results.api.RecordResultRequest;
import com.acme.sportplatform.results.api.ResultResponse;
import com.acme.sportplatform.results.application.AssignRegistrationToUnitUseCase;
import com.acme.sportplatform.results.application.CreateCompetitionUnitUseCase;
import com.acme.sportplatform.results.application.GetCompetitionUnitDetailsUseCase;
import com.acme.sportplatform.results.application.RecalculatePlacesUseCase;
import com.acme.sportplatform.results.application.RecordResultUseCase;

@RestController
@RequestMapping("/api/v1")
public class ResultsController {

    private final CreateCompetitionUnitUseCase createCompetitionUnitUseCase;
    private final AssignRegistrationToUnitUseCase assignRegistrationToUnitUseCase;
    private final RecordResultUseCase recordResultUseCase;
    private final RecalculatePlacesUseCase recalculatePlacesUseCase;
    private final GetCompetitionUnitDetailsUseCase getCompetitionUnitDetailsUseCase;

    public ResultsController(
            CreateCompetitionUnitUseCase createCompetitionUnitUseCase,
            AssignRegistrationToUnitUseCase assignRegistrationToUnitUseCase,
            RecordResultUseCase recordResultUseCase,
            RecalculatePlacesUseCase recalculatePlacesUseCase,
            GetCompetitionUnitDetailsUseCase getCompetitionUnitDetailsUseCase
        ) {
        this.createCompetitionUnitUseCase = createCompetitionUnitUseCase;
        this.assignRegistrationToUnitUseCase = assignRegistrationToUnitUseCase;
        this.recordResultUseCase = recordResultUseCase;
        this.recalculatePlacesUseCase = recalculatePlacesUseCase;
        this.getCompetitionUnitDetailsUseCase = getCompetitionUnitDetailsUseCase;
    }

    @GetMapping("/competition-units/{unitId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasRole('ORGANIZER') or hasRole('OPERATOR')")
    public CompetitionUnitDetailsResponse getUnitDetails(@PathVariable UUID unitId) {
        return getCompetitionUnitDetailsUseCase.execute(unitId);
    }

    @PostMapping("/competition-units")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasRole('ORGANIZER')")
    public CompetitionUnitResponse createCompetitionUnit(@RequestBody CreateCompetitionUnitRequest request) {
        return createCompetitionUnitUseCase.execute(request);
    }

    @PostMapping("/competition-units/{unitId}/entries")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasRole('ORGANIZER')")
    public UUID assignRegistration(
            @PathVariable UUID unitId,
            @RequestBody AssignRegistrationRequest request
    ) {
        return assignRegistrationToUnitUseCase.execute(unitId, request.registrationId(), request.laneOrPosition());
    }

    @PostMapping("/results")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasRole('OPERATOR')")
    public ResultResponse recordResult(
            @AuthenticationPrincipal(expression = "userId")
            UUID recordedByUserId,
            @RequestBody RecordResultRequest request,
            @RequestParam String resultType
    ) {
        // recordedByUserId нужно достать из SecurityContext (см. существующий паттерн в других контроллерах)
        return recordResultUseCase.execute(request, resultType, null);
    }

    @PostMapping("/competition-units/{unitId}/recalculate")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasRole('OPERATOR')")
    public void recalculate(
            @PathVariable UUID unitId,
            @RequestParam String rankingStrategy
    ) {
        recalculatePlacesUseCase.execute(unitId, rankingStrategy);
    }
}