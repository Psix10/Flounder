package com.acme.sportplatform.results;
import java.time.OffsetDateTime;
import java.util.List;
public record ResultsCompetitionUnitDraft(String label, Integer sequenceNumber, String status,
        List<ResultsCompetitionUnitEntryDraft> entries, OffsetDateTime scheduledAt) {
    public ResultsCompetitionUnitDraft(String label, Integer sequenceNumber, String status,
            List<ResultsCompetitionUnitEntryDraft> entries) {
        this(label, sequenceNumber, status, entries, null);
    }
}
