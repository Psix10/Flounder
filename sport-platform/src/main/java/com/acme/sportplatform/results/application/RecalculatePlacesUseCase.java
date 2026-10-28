package com.acme.sportplatform.results.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.results.infrastructure.jdbc.SqlPlacesRecalculator;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class RecalculatePlacesUseCase {

    private final CompetitionUnitWriteLock competitionUnitWriteLock;
    private final DisciplineResultPolicy disciplineResultPolicy;
    private final SqlPlacesRecalculator sqlPlacesRecalculator;

    @PersistenceContext
    private EntityManager entityManager;

    public RecalculatePlacesUseCase(
            CompetitionUnitWriteLock competitionUnitWriteLock,
            DisciplineResultPolicy disciplineResultPolicy,
            SqlPlacesRecalculator sqlPlacesRecalculator
    ) {
        this.competitionUnitWriteLock = competitionUnitWriteLock;
        this.disciplineResultPolicy = disciplineResultPolicy;
        this.sqlPlacesRecalculator = sqlPlacesRecalculator;
    }

    @Transactional
    public void execute(
            UUID competitionUnitId,
            String rankingStrategy
    ) {
        boolean descending = parseRankingStrategy(rankingStrategy);

        CompetitionUnitEntity unit =
                competitionUnitWriteLock.acquire(competitionUnitId);

        if ("PUBLISHED".equals(unit.getStatus())) {
            throw new BusinessException(
                    "results.unit_published",
                    "Нельзя пересчитать места: заплыв опубликован. "
                            + "Сначала снимите его с публикации."
            );
        }

        disciplineResultPolicy.requireRankingStrategy(
                unit.getEventDisciplineId(),
                rankingStrategy
        );

        entityManager.flush();

        sqlPlacesRecalculator.execute(
                competitionUnitId,
                descending,
                OffsetDateTime.now(ZoneOffset.UTC)
                        .truncatedTo(ChronoUnit.MICROS)
        );

        entityManager.clear();
    }

    private boolean parseRankingStrategy(String rankingStrategy) {
        if ("ASC".equalsIgnoreCase(rankingStrategy)) {
            return false;
        }

        if ("DESC".equalsIgnoreCase(rankingStrategy)) {
            return true;
        }

        throw new BusinessException(
                "results.invalid_ranking_strategy",
                "rankingStrategy must be ASC or DESC"
        );
    }
}