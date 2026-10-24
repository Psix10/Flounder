package com.acme.sportplatform.protocol.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.protocol.CompetitionUnitProtocolAccess;
import com.acme.sportplatform.results.ResultsCompetitionUnitDraft;
import com.acme.sportplatform.results.ResultsCompetitionUnitEntryDraft;
import com.acme.sportplatform.results.ResultsCompetitionUnitEntryView;
import com.acme.sportplatform.results.ResultsCompetitionUnitView;
import com.acme.sportplatform.results.ResultsProtocolGateway;

@Service
@Transactional(readOnly = true)
public class CompetitionUnitProtocolAccessService implements CompetitionUnitProtocolAccess {

    private final ResultsProtocolGateway resultsProtocolGateway;

    public CompetitionUnitProtocolAccessService(ResultsProtocolGateway resultsProtocolGateway) {
        this.resultsProtocolGateway = resultsProtocolGateway;
    }

    @Override
    public boolean hasCompetitionUnits(UUID eventDisciplineId) {
        return resultsProtocolGateway.hasCompetitionUnits(eventDisciplineId);
    }

    @Override
    @Transactional
    public List<SavedCompetitionUnitView> createDraftStartProtocol(
            UUID eventDisciplineId,
            List<CompetitionUnitDraftView> units
    ) {
        List<ResultsCompetitionUnitDraft> drafts = units.stream()
                .map(unit -> new ResultsCompetitionUnitDraft(
                        unit.label(),
                        unit.sequenceNumber(),
                        unit.status(),
                        unit.assignments().stream()
                                .map(assignment -> new ResultsCompetitionUnitEntryDraft(
                                        assignment.registrationId(),
                                        assignment.laneOrPosition()
                                ))
                                .toList()
                ))
                .toList();

        return resultsProtocolGateway.createDraftStartProtocol(eventDisciplineId, drafts).stream()
                .map(this::toSavedUnitView)
                .toList();
    }

    @Override
    public Optional<CompetitionUnitProtocolView> findStartProtocol(UUID eventDisciplineId) {
        List<ResultsCompetitionUnitView> units =
                resultsProtocolGateway.findCompetitionUnitsByEventDisciplineId(eventDisciplineId);

        if (units.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new CompetitionUnitProtocolView(
                eventDisciplineId,
                units.stream().map(this::toUnitView).toList()
        ));
    }

    private SavedCompetitionUnitView toSavedUnitView(ResultsCompetitionUnitView unit) {
        return new SavedCompetitionUnitView(
                unit.id(),
                unit.label(),
                unit.sequenceNumber(),
                unit.status(),
                unit.entries().stream().map(this::toAssignmentView).toList()
        );
    }

    private CompetitionUnitView toUnitView(ResultsCompetitionUnitView unit) {
        return new CompetitionUnitView(
                unit.id(),
                unit.label(),
                unit.sequenceNumber(),
                unit.status(),
                unit.entries().stream().map(this::toAssignmentView).toList()
        );
    }

    private AssignmentView toAssignmentView(ResultsCompetitionUnitEntryView entry) {
        return new AssignmentView(
                entry.registrationId(),
                entry.laneOrPosition()
        );
    }
}