package com.acme.sportplatform.results.web;

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
import com.acme.sportplatform.results.api.RecordResultRequest;
import com.acme.sportplatform.results.api.ResultResponse;
import com.acme.sportplatform.results.application.AssignRegistrationToUnitUseCase;
import com.acme.sportplatform.results.application.CreateCompetitionUnitUseCase;
import com.acme.sportplatform.results.application.GetCompetitionUnitDetailsUseCase;
import com.acme.sportplatform.results.application.RecalculatePlacesUseCase;
import com.acme.sportplatform.results.application.RecordResultUseCase;

import jakarta.validation.Valid;

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

    /**
     * Внутренний просмотр заплыва/матча с участниками и черновыми результатами.
     */
    @GetMapping("/competition-units/{unitId}")
    @PreAuthorize(
            "hasRole('PLATFORM_ADMIN') or " +
            "hasRole('ORGANIZER') or " +
            "hasRole('OPERATOR')"
    )
    public CompetitionUnitDetailsResponse getUnitDetails(
            @PathVariable UUID unitId
    ) {
        return getCompetitionUnitDetailsUseCase.execute(unitId);
    }

    /**
     * Публичная итоговая таблица.
     *
     * Для первого MVP временно возвращает те же данные, что и внутренний read API.
     * До пользовательского тестирования лучше ограничить её только unit-ами
     * в статусе PUBLISHED.
     */
    @GetMapping("/public/competition-units/{unitId}/results")
    public CompetitionUnitDetailsResponse getPublicResults(
            @PathVariable UUID unitId
    ) {
        return getCompetitionUnitDetailsUseCase.execute(unitId);
    }

    /**
     * Организатор создаёт заплыв, матч, группу, финал или иной unit.
     */
    @PostMapping("/competition-units")
    @PreAuthorize(
            "hasRole('PLATFORM_ADMIN') or hasRole('ORGANIZER')"
    )
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
     * Организатор назначает подтверждённую заявку в unit.
     */
    @PostMapping("/competition-units/{unitId}/entries")
    @PreAuthorize(
            "hasRole('PLATFORM_ADMIN') or hasRole('ORGANIZER')"
    )
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
     * Оператор вносит или обновляет результат одной entry.
     * После записи место ещё может быть null, пока не вызван recalculate.
     */
    @PostMapping("/results")
    @PreAuthorize(
            "hasRole('PLATFORM_ADMIN') or hasRole('OPERATOR')"
    )
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
     * Удобная MVP-ручка:
     * сохраняет результат и сразу пересчитывает все места в unit.
     */
    @PostMapping("/competition-units/{unitId}/results")
    @PreAuthorize(
            "hasRole('PLATFORM_ADMIN') or hasRole('OPERATOR')"
    )
    public CompetitionUnitDetailsResponse recordResultAndRecalculate(
            @PathVariable UUID unitId,
            @AuthenticationPrincipal(expression = "userId")
            UUID recordedByUserId,
            @Valid @RequestBody RecordResultRequest request,
            @RequestParam String resultType,
            @RequestParam(defaultValue = "ASC") String rankingStrategy
    ) {
        ResultResponse result = recordResultUseCase.execute(
                request,
                resultType,
                recordedByUserId
        );

        recalculatePlacesUseCase.execute(
                unitId,
                rankingStrategy
        );

        return getCompetitionUnitDetailsUseCase.execute(unitId);
    }

    /**
     * Ручной пересчёт после внесения нескольких результатов.
     *
     * rankingStrategy=ASC  — меньше значение лучше, например время.
     * rankingStrategy=DESC — больше значение лучше, например очки.
     */
    @PostMapping("/competition-units/{unitId}/recalculate")
    @PreAuthorize(
            "hasRole('PLATFORM_ADMIN') or hasRole('OPERATOR')"
    )
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
}