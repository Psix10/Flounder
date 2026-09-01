package com.acme.sportplatform.results.application;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.results.api.RecordResultRequest;
import com.acme.sportplatform.results.api.ResultResponse;
import com.acme.sportplatform.results.domain.ResultStatus;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntryEntity;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntryRepository;
import com.acme.sportplatform.results.infrastructure.jpa.ResultEntity;
import com.acme.sportplatform.results.infrastructure.jpa.ResultRepository;

@Service
public class RecordResultUseCase {

    private final ResultRepository resultRepository;
    private final CompetitionUnitEntryRepository entryRepository;

    public RecordResultUseCase(
            ResultRepository resultRepository,
            CompetitionUnitEntryRepository entryRepository
    ) {
        this.resultRepository = resultRepository;
        this.entryRepository = entryRepository;
    }

    @Transactional
    public ResultResponse execute(RecordResultRequest request, String resultType, UUID recordedByUserId) {
        CompetitionUnitEntryEntity entry = entryRepository.findById(request.competitionUnitEntryId())
                .orElseThrow(() -> new BusinessException(
                        "results.entry_not_found", "Участник заплыва не найден"));

        ResultEntity result = resultRepository.findByCompetitionUnitEntryId(entry.getId())
                .orElseGet(ResultEntity::new);

        if (result.getId() == null) {
            result.setId(UUID.randomUUID());
            result.setCreatedAt(OffsetDateTime.now());
        }

        result.setCompetitionUnitEntryId(entry.getId());
        result.setRawValue(request.rawValue());
        result.setResultType(resultType);
        result.setStatus(ResultStatus.PENDING.name());
        result.setRecordedByUserId(recordedByUserId);
        result.setUpdatedAt(OffsetDateTime.now());

        ResultEntity saved = resultRepository.save(result);

        return toResponse(saved);
    }

    private ResultResponse toResponse(ResultEntity entity) {
        return new ResultResponse(
                entity.getId(),
                entity.getCompetitionUnitEntryId(),
                entity.getRawValue(),
                entity.getResultType(),
                entity.getStatus(),
                entity.getFinalPlace()
        );
    }
}