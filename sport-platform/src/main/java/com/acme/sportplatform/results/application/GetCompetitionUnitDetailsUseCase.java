package com.acme.sportplatform.results.application;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.results.api.CompetitionUnitDetailsResponse;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntity;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntryEntity;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntryRepository;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitRepository;
import com.acme.sportplatform.results.infrastructure.jpa.ResultEntity;
import com.acme.sportplatform.results.infrastructure.jpa.ResultRepository;
@Service
public class GetCompetitionUnitDetailsUseCase {

        private final CompetitionUnitRepository competitionUnitRepository;
        private final CompetitionUnitEntryRepository entryRepository;
        private final ResultRepository resultRepository;

        public GetCompetitionUnitDetailsUseCase(
                CompetitionUnitRepository competitionUnitRepository,
                CompetitionUnitEntryRepository entryRepository,
                ResultRepository resultRepository
        ) {
                this.competitionUnitRepository = competitionUnitRepository;
                this.entryRepository = entryRepository;
                this.resultRepository = resultRepository;
        }

        @Transactional(readOnly = true)
        public CompetitionUnitDetailsResponse execute(UUID unitId) {
                CompetitionUnitEntity unit = competitionUnitRepository.findById(unitId)
                        .orElseThrow(() -> new BusinessException(
                                "results.unit_not_found",
                                "Competition unit not found: " + unitId));

                List<CompetitionUnitEntryEntity> entries = entryRepository.findByCompetitionUnitId(unitId);

                List<CompetitionUnitDetailsResponse.EntryView> entryViews = entries.stream()
                        .map(entry -> {
                        ResultEntity result = resultRepository
                                .findByCompetitionUnitEntryId(entry.getId())
                                .orElse(null);

                        return new CompetitionUnitDetailsResponse.EntryView(
                        entry.getId(),
                        entry.getRegistrationId(),
                        entry.getLaneOrPosition(),
                        result != null ? result.getRawValue() : null,
                        result != null ? result.getResultType() : null,
                        result != null ? result.getStatus() : null,
                        result != null ? result.getFinalPlace() : null
                );
                })
                .collect(Collectors.toList());

        return new CompetitionUnitDetailsResponse(
                unit.getId(),
                unit.getEventDisciplineId(),
                unit.getLabel(),
                unit.getStatus(),
                entryViews
        );
        }
}