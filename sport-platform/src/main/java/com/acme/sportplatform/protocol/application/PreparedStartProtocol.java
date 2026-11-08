package com.acme.sportplatform.protocol.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.acme.sportplatform.protocol.CompetitionUnitProtocolAccess;

/** Immutable in-memory preparation. Unit UUIDs are provisional, not persisted identifiers. */
public record PreparedStartProtocol(UUID eventDisciplineId, List<SeedParticipant> participants,
                                    List<GeneratedCompetitionUnit> units) {
    public PreparedStartProtocol {
        Objects.requireNonNull(eventDisciplineId);
        participants = List.copyOf(participants);
        units = units.stream().map(unit -> new GeneratedCompetitionUnit(
                unit.competitionUnitId(),unit.label(),unit.sequenceNumber(),List.copyOf(unit.assignments()))).toList();
    }
    public List<CompetitionUnitProtocolAccess.CompetitionUnitDraftView> toDrafts() {
        return units.stream().map(unit -> new CompetitionUnitProtocolAccess.CompetitionUnitDraftView(
                unit.label(), unit.sequenceNumber(), "DRAFT", unit.assignments().stream().map(assignment ->
                new CompetitionUnitProtocolAccess.AssignmentDraftView(assignment.registrationId(), assignment.laneOrPosition(), assignment.categoryCode(), assignment.seedingGroupCode()))
                .toList())).toList();
    }
}
