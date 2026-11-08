package com.acme.sportplatform.results.application;

import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.competition.EventDisciplineSummary;
import com.acme.sportplatform.results.api.PublicDisciplineStandingsResponse;
import com.acme.sportplatform.results.infrastructure.jdbc.SqlDisciplineStandingsReader;

@Service
public class GetPublicDisciplineStandingsUseCase {
    private final EventDisciplineLookup eventDisciplineLookup;
    private final SqlDisciplineStandingsReader standingsReader;

    public GetPublicDisciplineStandingsUseCase(
            EventDisciplineLookup eventDisciplineLookup,
            SqlDisciplineStandingsReader standingsReader) {
        this.eventDisciplineLookup = eventDisciplineLookup;
        this.standingsReader = standingsReader;
    }

    @Transactional(readOnly = true)
    public PublicDisciplineStandingsResponse execute(UUID eventId, UUID eventDisciplineId) {
        EventDisciplineSummary discipline = eventDisciplineLookup
                .findById(eventDisciplineId).orElseThrow(this::publicNotFound);
        if (!eventId.equals(discipline.eventId())) throw publicNotFound();

        String resultType = normalize(discipline.resultType());
        String strategy = normalize(discipline.rankingStrategy());
        if (resultType.isBlank()) {
            throw new BusinessException("results.invalid_result_type", "Для дисциплины не задан resultType.");
        }
        if (!"ASC".equals(strategy) && !"DESC".equals(strategy)) {
            throw new BusinessException("results.invalid_ranking_strategy", "rankingStrategy must be ASC or DESC");
        }
        return new PublicDisciplineStandingsResponse(
                eventId, eventDisciplineId, resultType, strategy, "COMPETITION",
                standingsReader.execute(eventId, eventDisciplineId, resultType, strategy)
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private BusinessException publicNotFound() {
        return new BusinessException("results.public_not_found", "Published results were not found.");
    }
}
