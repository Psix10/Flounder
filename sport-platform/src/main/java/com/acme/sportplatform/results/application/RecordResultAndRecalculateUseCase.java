package com.acme.sportplatform.results.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.results.api.CompetitionUnitDetailsResponse;
import com.acme.sportplatform.results.api.RecordResultRequest;

@Service
public class RecordResultAndRecalculateUseCase {

    private final RecordResultUseCase recordResultUseCase;
    private final RecalculatePlacesUseCase recalculatePlacesUseCase;
    private final GetCompetitionUnitDetailsUseCase getCompetitionUnitDetailsUseCase;

    public RecordResultAndRecalculateUseCase(
            RecordResultUseCase recordResultUseCase,
            RecalculatePlacesUseCase recalculatePlacesUseCase,
            GetCompetitionUnitDetailsUseCase getCompetitionUnitDetailsUseCase
    ) {
        this.recordResultUseCase = recordResultUseCase;
        this.recalculatePlacesUseCase = recalculatePlacesUseCase;
        this.getCompetitionUnitDetailsUseCase = getCompetitionUnitDetailsUseCase;
    }

    @Transactional
    public CompetitionUnitDetailsResponse execute(
            UUID unitId,
            RecordResultRequest request,
            String resultType,
            String rankingStrategy,
            UUID recordedByUserId
    ) {
        recordResultUseCase.execute(
                request,
                resultType,
                recordedByUserId,
                unitId
        );

        recalculatePlacesUseCase.execute(
                unitId,
                rankingStrategy
        );

        return getCompetitionUnitDetailsUseCase.execute(unitId);
    }
}