package com.acme.sportplatform.results;

import java.util.List;
import java.util.UUID;

public interface ResultsProtocolGateway {

    boolean hasCompetitionUnits(UUID eventDisciplineId);

    List<ResultsCompetitionUnitView> findCompetitionUnitsByEventDisciplineId(UUID eventDisciplineId);

    List<ResultsCompetitionUnitView> createDraftStartProtocol(
            UUID eventDisciplineId,
            List<ResultsCompetitionUnitDraft> units
    );
}