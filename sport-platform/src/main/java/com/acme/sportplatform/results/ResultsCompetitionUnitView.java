package com.acme.sportplatform.results;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
public record ResultsCompetitionUnitView(UUID id, String label, Integer sequenceNumber, String status,
        List<ResultsCompetitionUnitEntryView> entries, OffsetDateTime scheduledAt) {
    public ResultsCompetitionUnitView(UUID id, String label, Integer sequenceNumber, String status,
            List<ResultsCompetitionUnitEntryView> entries) {
        this(id, label, sequenceNumber, status, entries, null);
    }
}
