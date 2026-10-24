package com.acme.sportplatform.results.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import com.acme.sportplatform.results.api.PublicCompetitionUnitResultsResponse;
import com.acme.sportplatform.results.api.PublicDisciplineResultsResponse;
import com.acme.sportplatform.results.api.PublishCompetitionUnitRequest;
import com.acme.sportplatform.results.api.RecordResultRequest;
import com.acme.sportplatform.results.api.ResultResponse;
import com.acme.sportplatform.results.application.AssignRegistrationToUnitUseCase;
import com.acme.sportplatform.results.application.CreateCompetitionUnitUseCase;
import com.acme.sportplatform.results.application.GetCompetitionUnitDetailsUseCase;
import com.acme.sportplatform.results.application.GetPublicCompetitionUnitResultsUseCase;
import com.acme.sportplatform.results.application.GetPublicDisciplineResultsUseCase;
import com.acme.sportplatform.results.application.PublishCompetitionUnitUseCase;
import com.acme.sportplatform.results.application.RecalculatePlacesUseCase;
import com.acme.sportplatform.results.application.RecordResultAndRecalculateUseCase;
import com.acme.sportplatform.results.application.RecordResultUseCase;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitRepository;
import com.acme.sportplatform.results.api.PublicDisciplineStandingsResponse;
import com.acme.sportplatform.results.application.GetPublicDisciplineStandingsUseCase;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class ResultsController {

    private final CreateCompetitionUnitUseCase createCompetitionUnitUseCase;
    private final AssignRegistrationToUnitUseCase assignRegistrationToUnitUseCase;
    private final RecordResultUseCase recordResultUseCase;
    private final RecordResultAndRecalculateUseCase recordResultAndRecalculateUseCase;
    private final RecalculatePlacesUseCase recalculatePlacesUseCase;
    private final GetCompetitionUnitDetailsUseCase getCompetitionUnitDetailsUseCase;
    private final PublishCompetitionUnitUseCase publishCompetitionUnitUseCase;
    private final CompetitionUnitRepository competitionUnitRepository;
    private final GetPublicCompetitionUnitResultsUseCase getPublicCompetitionUnitResultsUseCase;
    private final GetPublicDisciplineResultsUseCase getPublicDisciplineResultsUseCase;

    public ResultsController(
            CreateCompetitionUnitUseCase createCompetitionUnitUseCase,
            AssignRegistrationToUnitUseCase assignRegistrationToUnitUseCase,
            RecordResultUseCase recordResultUseCase,
            RecordResultAndRecalculateUseCase recordResultAndRecalculateUseCase,
            RecalculatePlacesUseCase recalculatePlacesUseCase,
            GetCompetitionUnitDetailsUseCase getCompetitionUnitDetailsUseCase,
            PublishCompetitionUnitUseCase publishCompetitionUnitUseCase,
            CompetitionUnitRepository competitionUnitRepository,
            GetPublicCompetitionUnitResultsUseCase getPublicCompetitionUnitResultsUseCase,
            GetPublicDisciplineResultsUseCase getPublicDisciplineResultsUseCase
    ) {
        this.createCompetitionUnitUseCase = createCompetitionUnitUseCase;
        this.assignRegistrationToUnitUseCase = assignRegistrationToUnitUseCase;
        this.recordResultUseCase = recordResultUseCase;
        this.recordResultAndRecalculateUseCase = recordResultAndRecalculateUseCase;
        this.recalculatePlacesUseCase = recalculatePlacesUseCase;
        this.getCompetitionUnitDetailsUseCase = getCompetitionUnitDetailsUseCase;
        this.publishCompetitionUnitUseCase = publishCompetitionUnitUseCase;
        this.competitionUnitRepository = competitionUnitRepository;
        this.getPublicCompetitionUnitResultsUseCase = getPublicCompetitionUnitResultsUseCase;
        this.getPublicDisciplineResultsUseCase = getPublicDisciplineResultsUseCase;
    }

    /**
     * Список заплывов, матчей, групп или финалов одной дисциплины.
     */
    @GetMapping("/event-disciplines/{eventDisciplineId}/competition-units")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER', 'OPERATOR')")
    public List<CompetitionUnitResponse> listCompetitionUnits(
            @PathVariable UUID eventDisciplineId
    ) {
        return competitionUnitRepository
                .findByEventDisciplineIdOrderBySequenceNumberAsc(eventDisciplineId)
                .stream()
                .map(unit -> new CompetitionUnitResponse(
                        unit.getId(),
                        unit.getEventDisciplineId(),
                        unit.getLabel(),
                        unit.getSequenceNumber(),
                        unit.getStatus(),
                        unit.getScheduledAt()
                ))
                .toList();
    }

    /**
     * Внутренний просмотр unit с участниками и черновыми результатами.
     */
    @GetMapping("/competition-units/{unitId}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER', 'OPERATOR')")
    public CompetitionUnitDetailsResponse getUnitDetails(
            @PathVariable UUID unitId
    ) {
        return getCompetitionUnitDetailsUseCase.execute(unitId);
    }

    /**
     * Публичные результаты опубликованного unit.
     */
    @GetMapping("/public/competition-units/{unitId}/results")
    public PublicCompetitionUnitResultsResponse getPublicResults(
            @PathVariable UUID unitId
    ) {
        return getPublicCompetitionUnitResultsUseCase.execute(unitId);
    }

    /**
     * Публичные результаты опубликованных unit дисциплины.
     * Принадлежность дисциплины событию проверяется внутри use case.
     */
    @GetMapping("/public/events/{eventId}/disciplines/{eventDisciplineId}/results")
    public PublicDisciplineResultsResponse getPublicDisciplineResults(
            @PathVariable UUID eventId,
            @PathVariable UUID eventDisciplineId
    ) {
        return getPublicDisciplineResultsUseCase.execute(
                eventId,
                eventDisciplineId
        );
    }

    /**
     * Создание заплыва, матча, группы, финала или другого unit.
     */
    @PostMapping("/competition-units")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER')")
    public ResponseEntity<CompetitionUnitResponse> createCompetitionUnit(
            @Valid @RequestBody CreateCompetitionUnitRequest request
    ) {
        CompetitionUnitResponse response =
                createCompetitionUnitUseCase.execute(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * Назначение подтверждённой заявки в unit.
     */
    @PostMapping("/competition-units/{unitId}/entries")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER')")
    public ResponseEntity<UUID> assignRegistration(
            @PathVariable UUID unitId,
            @Valid @RequestBody AssignRegistrationRequest request
    ) {
        UUID entryId = assignRegistrationToUnitUseCase.execute(
                unitId,
                request.registrationId(),
                request.laneOrPosition()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(entryId);
    }

    /**
     * Запись или обновление результата без автоматического пересчёта мест.
     */
    @PostMapping("/results")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'OPERATOR')")
    public ResultResponse recordResult(
            @AuthenticationPrincipal(expression = "userId")
            UUID recordedByUserId,
            @Valid @RequestBody RecordResultRequest request,
            @RequestParam String resultType
    ) {
        return recordResultUseCase.execute(
                request,
                resultType,
                recordedByUserId
        );
    }

    /**
     * Запись результата и пересчёт мест через единый транзакционный use case.
     */
    @PostMapping("/competition-units/{unitId}/results")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'OPERATOR')")
    public CompetitionUnitDetailsResponse recordResultAndRecalculate(
            @PathVariable UUID unitId,
            @AuthenticationPrincipal(expression = "userId")
            UUID recordedByUserId,
            @Valid @RequestBody RecordResultRequest request,
            @RequestParam String resultType,
            @RequestParam(defaultValue = "ASC") String rankingStrategy
    ) {
        return recordResultAndRecalculateUseCase.execute(
                unitId,
                request,
                resultType,
                rankingStrategy,
                recordedByUserId
        );
    }

    /**
     * Ручной пересчёт мест после внесения нескольких результатов.
     */
    @PostMapping("/competition-units/{unitId}/recalculate")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'OPERATOR')")
    public CompetitionUnitDetailsResponse recalculate(
            @PathVariable UUID unitId,
            @RequestParam String rankingStrategy
    ) {
        recalculatePlacesUseCase.execute(
                unitId,
                rankingStrategy
        );

        return getCompetitionUnitDetailsUseCase.execute(unitId);
    }

    /**
     * Публикация результатов или возврат unit в статус DRAFT.
     */
    @PostMapping("/competition-units/{unitId}/publication")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'OPERATOR', 'ORGANIZER', 'JUDGE')")
    public ResponseEntity<Void> changePublication(
            @PathVariable UUID unitId,
            @RequestBody PublishCompetitionUnitRequest request
    ) {
        publishCompetitionUnitUseCase.execute(
                unitId,
                request.published()
        );

        return ResponseEntity.noContent().build();
    }

        
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
}


