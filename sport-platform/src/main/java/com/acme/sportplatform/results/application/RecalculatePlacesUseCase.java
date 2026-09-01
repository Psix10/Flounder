package com.acme.sportplatform.results.application;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.results.domain.ResultStatus;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntryRepository;
import com.acme.sportplatform.results.infrastructure.jpa.ResultEntity;
import com.acme.sportplatform.results.infrastructure.jpa.ResultRepository;

@Service
public class RecalculatePlacesUseCase {

    private final CompetitionUnitEntryRepository entryRepository;
    private final ResultRepository resultRepository;

    public RecalculatePlacesUseCase(
            CompetitionUnitEntryRepository entryRepository,
            ResultRepository resultRepository
    ) {
        this.entryRepository = entryRepository;
        this.resultRepository = resultRepository;
    }

    /**
     * rankingStrategy: "ASC" — меньше значение лучше (время),
     *                  "DESC" — больше значение лучше (очки)
     */
    @Transactional
    public void execute(UUID competitionUnitId, String rankingStrategy) {
        List<ResultEntity> results = entryRepository.findByCompetitionUnitId(competitionUnitId).stream()
                .map(entry -> resultRepository.findByCompetitionUnitEntryId(entry.getId()).orElse(null))
                .filter(r -> r != null && ResultStatus.VALID.name().equals(r.getStatus()) || r != null && ResultStatus.PENDING.name().equals(r.getStatus()))
                .toList();

        Comparator<ResultEntity> comparator = Comparator.comparing(
                r -> parseNumeric(r.getRawValue())
        );

        if ("DESC".equalsIgnoreCase(rankingStrategy)) {
            comparator = comparator.reversed();
        }

        List<ResultEntity> sorted = results.stream()
                .filter(r -> parseNumeric(r.getRawValue()) != null)
                .sorted(comparator)
                .toList();

        int place = 1;
        for (ResultEntity result : sorted) {
            result.setFinalPlace(place++);
            result.setStatus(ResultStatus.VALID.name());
            result.setUpdatedAt(OffsetDateTime.now());
            resultRepository.save(result);
        }
    }

    private Double parseNumeric(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        try {
            // упрощённый парсинг: секунды как число, либо "mm:ss.SS"
            if (rawValue.contains(":")) {
                String[] parts = rawValue.split(":");
                double minutes = Double.parseDouble(parts[0]);
                double seconds = Double.parseDouble(parts[1]);
                return minutes * 60 + seconds;
            }
            return Double.parseDouble(rawValue);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}