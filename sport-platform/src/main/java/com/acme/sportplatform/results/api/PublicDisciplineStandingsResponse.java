package com.acme.sportplatform.results.api;

import java.util.List;
import java.util.UUID;

public record PublicDisciplineStandingsResponse(
        UUID eventId,
        UUID eventDisciplineId,
        String resultType,
        String rankingStrategy,
        String tiePolicy,
        List<EntryView> entries
) {
    public record EntryView(
            Integer overallPlace,
            UUID competitionUnitId,
            String competitionUnitLabel,
            Integer unitPlace,
            String participantName,
            String clubName,
            String rawValue,
            String resultType,
            String status,
            String categoryCode,
            String seedingGroupCode,
            Integer categoryPlace
    ) {
        public EntryView(Integer overallPlace, UUID competitionUnitId, String competitionUnitLabel,
                Integer unitPlace, String participantName, String clubName,
                String rawValue, String resultType, String status) {
            this(overallPlace, competitionUnitId, competitionUnitLabel, unitPlace, participantName,
                    clubName, rawValue, resultType, status, null, null, null);
        }
    }
}
