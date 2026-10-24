package com.acme.sportplatform.protocol.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.protocol.CompetitionUnitProtocolAccess;
import com.acme.sportplatform.protocol.api.GeneratedAssignmentResponse;
import com.acme.sportplatform.protocol.api.GeneratedCompetitionUnitResponse;
import com.acme.sportplatform.protocol.api.StartProtocolResponse;

@Service
@Transactional(readOnly = true)
public class GetStartProtocolUseCase {

    private final CompetitionUnitProtocolAccess competitionUnitProtocolAccess;

    public GetStartProtocolUseCase(CompetitionUnitProtocolAccess competitionUnitProtocolAccess) {
        this.competitionUnitProtocolAccess = competitionUnitProtocolAccess;
    }

    public StartProtocolResponse execute(UUID eventDisciplineId) {
        CompetitionUnitProtocolAccess.CompetitionUnitProtocolView protocol =
                competitionUnitProtocolAccess.findStartProtocol(eventDisciplineId)
                        .orElseThrow(() -> new BusinessException(
                                "protocol.start_protocol_not_found",
                                "Start protocol not found for this discipline"
                        ));

        List<GeneratedCompetitionUnitResponse> units = protocol.units().stream()
                .map(unit -> new GeneratedCompetitionUnitResponse(
                        unit.competitionUnitId(),
                        unit.label(),
                        unit.sequenceNumber(),
                        unit.assignments().stream()
                                .map(assignment -> new GeneratedAssignmentResponse(
                                        assignment.registrationId(),
                                        assignment.laneOrPosition(),
                                        null
                                ))
                                .toList()
                ))
                .toList();

        int assignmentCount = units.stream()
                .mapToInt(unit -> unit.assignments().size())
                .sum();

        return new StartProtocolResponse(
                protocol.eventDisciplineId(),
                units.size(),
                assignmentCount,
                units
        );
    }
}