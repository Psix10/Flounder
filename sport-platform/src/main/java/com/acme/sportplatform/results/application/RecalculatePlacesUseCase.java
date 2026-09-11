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
     * ASC  — меньше значение лучше: время, штрафные секунды.
     * DESC — больше значение лучше: очки, голы, баллы.
     */
    @Transactional
    public void execute(
            UUID competitionUnitId,
            String rankingStrategy
    ) {
        boolean descending =
                "DESC".equalsIgnoreCase(rankingStrategy);

        if (!descending
                && !"ASC".equalsIgnoreCase(rankingStrategy)) {
            throw new IllegalArgumentException(
                    "rankingStrategy must be ASC or DESC"
            );
        }

        List<ResultEntity> allResults =
                entryRepository.findByCompetitionUnitId(
                        competitionUnitId
                ).stream()
                        .map(entry -> resultRepository
                                .findByCompetitionUnitEntryId(
                                        entry.getId()
                                )
                                .orElse(null)
                        )
                        .filter(result -> result != null)
                        .toList();

        OffsetDateTime now = OffsetDateTime.now();

        for (ResultEntity result : allResults) {
            result.setFinalPlace(null);
            result.setUpdatedAt(now);
        }

        List<ResultEntity> rankedResults = allResults.stream()
                .filter(result ->
                        ResultStatus.PENDING.name().equals(
                                result.getStatus()
                        )
                        || ResultStatus.VALID.name().equals(
                                result.getStatus()
                        )
                )
                .filter(result ->
                        parseNumeric(result.getRawValue()) != null
                )
                .sorted(resultComparator(descending))
                .toList();

        int place = 1;

        for (ResultEntity result : rankedResults) {
            result.setFinalPlace(place++);
            result.setStatus(ResultStatus.VALID.name());
            result.setUpdatedAt(now);
        }

        resultRepository.saveAll(allResults);
    }

    private Comparator<ResultEntity> resultComparator(
            boolean descending
    ) {
        Comparator<ResultEntity> comparator =
                Comparator.comparing(
                        result -> parseNumeric(result.getRawValue())
                );

        return descending
                ? comparator.reversed()
                : comparator;
    }

    private Double parseNumeric(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        try {
            if (rawValue.contains(":")) {
                String[] parts = rawValue.split(":");

                if (parts.length != 2) {
                    return null;
                }

                double minutes = Double.parseDouble(parts[0]);
                double seconds = Double.parseDouble(parts[1]);

                return minutes * 60 + seconds;
            }

            return Double.parseDouble(rawValue);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}