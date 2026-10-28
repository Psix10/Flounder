package com.acme.sportplatform.results.application;

import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.competition.EventDisciplineSummary;

@Service
@Transactional(readOnly = true)
public class DisciplineResultPolicy {

    private final EventDisciplineLookup eventDisciplineLookup;

    public DisciplineResultPolicy(
            EventDisciplineLookup eventDisciplineLookup
    ) {
        this.eventDisciplineLookup = eventDisciplineLookup;
    }

    public String requireResultType(
            UUID eventDisciplineId,
            String requestedResultType
    ) {
        EventDisciplineSummary discipline = getDiscipline(eventDisciplineId);

        String configured = normalize(discipline.resultType());
        String requested = normalize(requestedResultType);

        if (configured.isBlank()) {
            throw new BusinessException(
                    "results.invalid_discipline_settings",
                    "Для дисциплины не задан resultType."
            );
        }

        if (!configured.equals(requested)) {
            throw new BusinessException(
                    "results.result_type_mismatch",
                    "Тип результата должен соответствовать "
                            + "настройкам дисциплины: " + configured
            );
        }

        return configured;
    }

    public String requireRankingStrategy(
            UUID eventDisciplineId,
            String requestedRankingStrategy
    ) {
        String requested = normalize(requestedRankingStrategy);

        if (!isRankingStrategy(requested)) {
            throw new BusinessException(
                    "results.invalid_ranking_strategy",
                    "rankingStrategy must be ASC or DESC"
            );
        }

        EventDisciplineSummary discipline = getDiscipline(eventDisciplineId);

        String configured = normalize(discipline.rankingStrategy());

        if (!isRankingStrategy(configured)) {
            throw new BusinessException(
                    "results.invalid_discipline_settings",
                    "Для дисциплины задан недопустимый rankingStrategy."
            );
        }

        if (!configured.equals(requested)) {
            throw new BusinessException(
                    "results.ranking_strategy_mismatch",
                    "Порядок ранжирования должен соответствовать "
                            + "настройкам дисциплины: " + configured
            );
        }

        return configured;
    }

    private EventDisciplineSummary getDiscipline(UUID eventDisciplineId) {
        return eventDisciplineLookup.findById(eventDisciplineId)
                .orElseThrow(() -> new BusinessException(
                        "results.event_discipline_not_found",
                        "Event discipline not found"
                ));
    }

    private boolean isRankingStrategy(String value) {
        return "ASC".equals(value) || "DESC".equals(value);
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toUpperCase(Locale.ROOT);
    }
}