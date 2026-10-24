package com.acme.sportplatform.results.application;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.results.ResultsCompetitionUnitDraft;
import com.acme.sportplatform.results.ResultsCompetitionUnitEntryDraft;
import com.acme.sportplatform.results.ResultsCompetitionUnitEntryView;
import com.acme.sportplatform.results.ResultsCompetitionUnitView;
import com.acme.sportplatform.results.ResultsProtocolGateway;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntity;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntryEntity;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntryRepository;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitRepository;

@Service
@Transactional
public class ResultsProtocolGatewayService implements ResultsProtocolGateway {

    private static final String DRAFT = "DRAFT";

    private final CompetitionUnitRepository competitionUnitRepository;
    private final CompetitionUnitEntryRepository competitionUnitEntryRepository;

    public ResultsProtocolGatewayService(
            CompetitionUnitRepository competitionUnitRepository,
            CompetitionUnitEntryRepository competitionUnitEntryRepository
    ) {
        this.competitionUnitRepository = competitionUnitRepository;
        this.competitionUnitEntryRepository = competitionUnitEntryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasCompetitionUnits(UUID eventDisciplineId) {
        return !competitionUnitRepository
                .findByEventDisciplineIdOrderBySequenceNumberAsc(eventDisciplineId)
                .isEmpty();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResultsCompetitionUnitView> findCompetitionUnitsByEventDisciplineId(UUID eventDisciplineId) {
        List<CompetitionUnitEntity> units =
                competitionUnitRepository.findByEventDisciplineIdOrderBySequenceNumberAsc(eventDisciplineId);

        List<ResultsCompetitionUnitView> result = new ArrayList<>();

        for (CompetitionUnitEntity unit : units) {
            List<ResultsCompetitionUnitEntryView> entries =
                    competitionUnitEntryRepository.findByCompetitionUnitId(unit.getId())
                            .stream()
                            .map(entry -> new ResultsCompetitionUnitEntryView(
                                    entry.getId(),
                                    entry.getRegistrationId(),
                                    entry.getLaneOrPosition()
                            ))
                            .toList();

            result.add(new ResultsCompetitionUnitView(
                    unit.getId(),
                    unit.getLabel(),
                    unit.getSequenceNumber(),
                    unit.getStatus(),
                    entries
            ));
        }

        return result;
    }

    @Override
    public List<ResultsCompetitionUnitView> createDraftStartProtocol(
            UUID eventDisciplineId,
            List<ResultsCompetitionUnitDraft> units
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        List<ResultsCompetitionUnitView> result = new ArrayList<>();

        for (ResultsCompetitionUnitDraft draft : units) {
            CompetitionUnitEntity unit = new CompetitionUnitEntity();
            unit.setId(UUID.randomUUID());
            unit.setEventDisciplineId(eventDisciplineId);
            unit.setLabel(draft.label());
            unit.setSequenceNumber(draft.sequenceNumber());
            unit.setStatus(draft.status() == null || draft.status().isBlank() ? DRAFT : draft.status());
            unit.setCreatedAt(now);
            unit.setUpdatedAt(now);

            CompetitionUnitEntity savedUnit = competitionUnitRepository.save(unit);

            List<ResultsCompetitionUnitEntryView> savedEntries = new ArrayList<>();

            for (ResultsCompetitionUnitEntryDraft draftEntry : draft.entries()) {
                CompetitionUnitEntryEntity entry = new CompetitionUnitEntryEntity();
                entry.setId(UUID.randomUUID());
                entry.setCompetitionUnitId(savedUnit.getId());
                entry.setRegistrationId(draftEntry.registrationId());
                entry.setLaneOrPosition(draftEntry.laneOrPosition());
                entry.setCreatedAt(now);

                CompetitionUnitEntryEntity savedEntry = competitionUnitEntryRepository.save(entry);

                savedEntries.add(new ResultsCompetitionUnitEntryView(
                        savedEntry.getId(),
                        savedEntry.getRegistrationId(),
                        savedEntry.getLaneOrPosition()
                ));
            }

            result.add(new ResultsCompetitionUnitView(
                    savedUnit.getId(),
                    savedUnit.getLabel(),
                    savedUnit.getSequenceNumber(),
                    savedUnit.getStatus(),
                    savedEntries
            ));
        }

        return result;
    }
}