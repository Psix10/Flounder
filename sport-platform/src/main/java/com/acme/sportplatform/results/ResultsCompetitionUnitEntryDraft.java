package com.acme.sportplatform.results;

import java.util.UUID;

public record ResultsCompetitionUnitEntryDraft(
        UUID registrationId,
        Integer laneOrPosition
) {
}