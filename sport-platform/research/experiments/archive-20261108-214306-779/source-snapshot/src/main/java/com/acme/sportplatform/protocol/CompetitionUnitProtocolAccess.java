package com.acme.sportplatform.protocol;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface CompetitionUnitProtocolAccess {
    boolean hasCompetitionUnits(UUID eventDisciplineId);
    List<SavedCompetitionUnitView> createDraftStartProtocol(UUID eventDisciplineId, List<CompetitionUnitDraftView> units);
    List<SavedCompetitionUnitView> replaceDraftStartProtocol(UUID eventDisciplineId, List<CompetitionUnitDraftView> units);
    Optional<CompetitionUnitProtocolView> findStartProtocol(UUID eventDisciplineId);
    record CompetitionUnitDraftView(String label, Integer sequenceNumber, String status,
            List<AssignmentDraftView> assignments, OffsetDateTime scheduledAt) {
        public CompetitionUnitDraftView(String label, Integer sequenceNumber, String status,List<AssignmentDraftView> assignments) {
            this(label, sequenceNumber, status, assignments, null);
        }
    }
    record AssignmentDraftView(UUID registrationId, Integer laneOrPosition, String categoryCode, String seedingGroupCode) {
        public AssignmentDraftView(UUID registrationId, Integer laneOrPosition) { this(registrationId, laneOrPosition, null, null); }
    }
    record SavedCompetitionUnitView(UUID competitionUnitId, String label, Integer sequenceNumber,
            String status, List<AssignmentView> assignments, OffsetDateTime scheduledAt) {
        public SavedCompetitionUnitView(UUID competitionUnitId, String label, Integer sequenceNumber,
                String status, List<AssignmentView> assignments) {
            this(competitionUnitId, label, sequenceNumber, status, assignments, null);
        }
    }
    record CompetitionUnitProtocolView(UUID eventDisciplineId, List<CompetitionUnitView> units) { }
    record CompetitionUnitView(UUID competitionUnitId, String label, Integer sequenceNumber,
            String status, List<AssignmentView> assignments, OffsetDateTime scheduledAt) {
        public CompetitionUnitView(UUID competitionUnitId, String label, Integer sequenceNumber,
                String status, List<AssignmentView> assignments) {
            this(competitionUnitId, label, sequenceNumber, status, assignments, null);
        }
    }
    record AssignmentView(UUID registrationId, Integer laneOrPosition, String categoryCode, String seedingGroupCode) {
        public AssignmentView(UUID registrationId, Integer laneOrPosition) { this(registrationId, laneOrPosition, null, null); }
    }
}
