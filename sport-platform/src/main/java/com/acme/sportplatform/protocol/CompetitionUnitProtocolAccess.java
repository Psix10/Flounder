package com.acme.sportplatform.protocol;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompetitionUnitProtocolAccess {

    boolean hasCompetitionUnits(UUID eventDisciplineId);

    List<SavedCompetitionUnitView> createDraftStartProtocol(
            UUID eventDisciplineId,
            List<CompetitionUnitDraftView> units
    );

    Optional<CompetitionUnitProtocolView> findStartProtocol(UUID eventDisciplineId);

    record CompetitionUnitDraftView(
            String label,
            Integer sequenceNumber,
            String status,
            List<AssignmentDraftView> assignments
    ) {
    }

    record AssignmentDraftView(
            UUID registrationId,
            Integer laneOrPosition
    ) {
    }

    record SavedCompetitionUnitView(
            UUID competitionUnitId,
            String label,
            Integer sequenceNumber,
            String status,
            List<AssignmentView> assignments
    ) {
    }

    record CompetitionUnitProtocolView(
            UUID eventDisciplineId,
            List<CompetitionUnitView> units
    ) {
    }

    record CompetitionUnitView(
            UUID competitionUnitId,
            String label,
            Integer sequenceNumber,
            String status,
            List<AssignmentView> assignments
    ) {
    }

    record AssignmentView(
            UUID registrationId,
            Integer laneOrPosition
    ) {
    }
}