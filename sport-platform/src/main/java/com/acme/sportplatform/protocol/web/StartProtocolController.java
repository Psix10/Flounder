package com.acme.sportplatform.protocol.web;

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

import com.acme.sportplatform.protocol.api.GenerateStartProtocolRequest;
import com.acme.sportplatform.protocol.api.GeneratedAssignmentResponse;
import com.acme.sportplatform.protocol.api.GeneratedCompetitionUnitResponse;
import com.acme.sportplatform.protocol.api.StartProtocolResponse;
import com.acme.sportplatform.protocol.application.GenerateStartProtocolUseCase;
import com.acme.sportplatform.protocol.application.GetStartProtocolUseCase;
import com.acme.sportplatform.protocol.application.ProtocolGenerationResult;

@RestController
@RequestMapping("/api/v1/event-disciplines/{eventDisciplineId}/start-protocol")
public class StartProtocolController {

    private final GenerateStartProtocolUseCase generateStartProtocolUseCase;
    private final GetStartProtocolUseCase getStartProtocolUseCase;

    public StartProtocolController(
            GenerateStartProtocolUseCase generateStartProtocolUseCase,
            GetStartProtocolUseCase getStartProtocolUseCase
    ) {
        this.generateStartProtocolUseCase = generateStartProtocolUseCase;
        this.getStartProtocolUseCase = getStartProtocolUseCase;
    }

    @PostMapping("/generate")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasRole('ORGANIZER')")
    public ResponseEntity<StartProtocolResponse> generate(
            @PathVariable UUID eventDisciplineId,
            @RequestBody(required = false) GenerateStartProtocolRequest request
    ) {
        ProtocolGenerationResult result =
                generateStartProtocolUseCase.execute(eventDisciplineId, request);

        List<GeneratedCompetitionUnitResponse> units = result.units().stream()
                .map(unit -> new GeneratedCompetitionUnitResponse(
                        unit.competitionUnitId(),
                        unit.label(),
                        unit.sequenceNumber(),
                        unit.assignments().stream()
                                .map(assignment -> new GeneratedAssignmentResponse(
                                        assignment.registrationId(),
                                        assignment.laneOrPosition(),
                                        assignment.seedMetric()
                                ))
                                .toList()
                ))
                .toList();

        return ResponseEntity.ok(
                new StartProtocolResponse(
                        result.eventDisciplineId(),
                        result.unitCount(),
                        result.assignmentCount(),
                        units
                )
        );
    }

    @GetMapping
    public StartProtocolResponse getByEventDisciplineId(
            @PathVariable UUID eventDisciplineId
    ) {
        return getStartProtocolUseCase.execute(eventDisciplineId);
    }
}