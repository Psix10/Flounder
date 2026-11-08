package com.acme.sportplatform.protocol.application;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.acme.sportplatform.protocol.CompetitionUnitProtocolAccess;
import com.acme.sportplatform.results.*;
@Service
@Transactional(readOnly = true)
public class CompetitionUnitProtocolAccessService implements CompetitionUnitProtocolAccess {
    private final ResultsProtocolGateway gateway;
    public CompetitionUnitProtocolAccessService(ResultsProtocolGateway gateway) { this.gateway = gateway; }
    @Override public boolean hasCompetitionUnits(UUID id) { return gateway.hasCompetitionUnits(id); }
    @Override @Transactional
    public List<SavedCompetitionUnitView> createDraftStartProtocol(UUID id, List<CompetitionUnitDraftView> units) {
        return gateway.createDraftStartProtocol(id, drafts(units)).stream().map(this::saved).toList();
    }
    @Override @Transactional
    public List<SavedCompetitionUnitView> replaceDraftStartProtocol(UUID id, List<CompetitionUnitDraftView> units) {
        return gateway.replaceDraftStartProtocol(id, drafts(units)).stream().map(this::saved).toList();
    }
    @Override public Optional<CompetitionUnitProtocolView> findStartProtocol(UUID id) {
        var units = gateway.findCompetitionUnitsByEventDisciplineId(id);
        if (units.isEmpty()) return Optional.empty();
        return Optional.of(new CompetitionUnitProtocolView(id, units.stream().map(u ->
                new CompetitionUnitView(u.id(), u.label(), u.sequenceNumber(), u.status(),
                        u.entries().stream().map(this::assignment).toList(), u.scheduledAt())).toList()));
    }
    private List<ResultsCompetitionUnitDraft> drafts(List<CompetitionUnitDraftView> units) {
        return units.stream().map(u -> new ResultsCompetitionUnitDraft(u.label(), u.sequenceNumber(),
                u.status(), u.assignments().stream().map(a -> new ResultsCompetitionUnitEntryDraft(
                        a.registrationId(), a.laneOrPosition(), a.categoryCode(), a.seedingGroupCode())).toList(), u.scheduledAt())).toList();
    }
    private SavedCompetitionUnitView saved(ResultsCompetitionUnitView u) {
        return new SavedCompetitionUnitView(u.id(), u.label(), u.sequenceNumber(), u.status(),
                u.entries().stream().map(this::assignment).toList(), u.scheduledAt());
    }
    private AssignmentView assignment(ResultsCompetitionUnitEntryView a) {
        return new AssignmentView(a.registrationId(), a.laneOrPosition(), a.categoryCode(), a.seedingGroupCode());
    }
}
