package com.acme.sportplatform.results.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.competition.EventDisciplineSummary;
import com.acme.sportplatform.results.api.PublicDisciplineResultsResponse;
import com.acme.sportplatform.results.api.PublicDisciplineStandingsResponse;

@Service
public class GetPublicDisciplineStandingsUseCase {

    private static final String TIE_POLICY = "COMPETITION";

    private static final Set<String> NON_RANKED_STATUSES = Set.of(
            "DID_NOT_START",
            "DID_NOT_FINISH",
            "DISQUALIFIED"
    );

    private final EventDisciplineLookup eventDisciplineLookup;
    private final GetPublicDisciplineResultsUseCase getPublicDisciplineResultsUseCase;

    public GetPublicDisciplineStandingsUseCase(
            EventDisciplineLookup eventDisciplineLookup,
            GetPublicDisciplineResultsUseCase getPublicDisciplineResultsUseCase
    ) {
        this.eventDisciplineLookup = eventDisciplineLookup;
        this.getPublicDisciplineResultsUseCase = getPublicDisciplineResultsUseCase;
    }

    @Transactional(readOnly = true)
    public PublicDisciplineStandingsResponse execute(
            UUID eventId,
            UUID eventDisciplineId
    ) {
        EventDisciplineSummary discipline = eventDisciplineLookup
                .findById(eventDisciplineId)
                .orElseThrow(this::publicNotFound);

        if (!eventId.equals(discipline.eventId())) {
            throw publicNotFound();
        }

        String resultType = normalize(discipline.resultType());
        String rankingStrategy = normalize(discipline.rankingStrategy());

        if (resultType.isBlank()) {
            throw new BusinessException(
                    "results.invalid_result_type",
                    "Для дисциплины не задан resultType."
            );
        }

        if (!"ASC".equals(rankingStrategy)
                && !"DESC".equals(rankingStrategy)) {
            throw new BusinessException(
                    "results.invalid_ranking_strategy",
                    "rankingStrategy must be ASC or DESC"
            );
        }

        PublicDisciplineResultsResponse publishedResults =
                getPublicDisciplineResultsUseCase.execute(
                        eventId,
                        eventDisciplineId
                );

        List<Candidate> ranked = new ArrayList<>();
        List<Candidate> nonRanked = new ArrayList<>();

        for (PublicDisciplineResultsResponse.UnitView unit
                : publishedResults.units()) {
            for (PublicDisciplineResultsResponse.EntryView entry
                    : unit.entries()) {

                String status = entry.status();

                if ("VALID".equals(status)) {
                    if (!resultType.equals(normalize(entry.resultType()))) {
                        throw new BusinessException(
                                "results.result_type_mismatch",
                                "Тип результата не совпадает с типом дисциплины."
                        );
                    }

                    ranked.add(new Candidate(
                            unit,
                            entry,
                            parseValue(entry.rawValue(), resultType)
                    ));
                } else if (status != null
                        && NON_RANKED_STATUSES.contains(status)) {
                    nonRanked.add(new Candidate(unit, entry, null));
                } else {
                    throw new BusinessException(
                            "results.invalid_standings_status",
                            "В опубликованном заплыве найден "
                                    + "недопустимый статус результата."
                    );
                }
            }
        }

        Comparator<Candidate> byValue =
                Comparator.comparing(Candidate::value);

        if ("DESC".equals(rankingStrategy)) {
            byValue = byValue.reversed();
        }

        ranked.sort(byValue.thenComparing(displayOrder()));
        nonRanked.sort(displayOrder());

        List<PublicDisciplineStandingsResponse.EntryView> entries =
                new ArrayList<>();

        BigDecimal previousValue = null;
        int currentPlace = 0;

        for (int i = 0; i < ranked.size(); i++) {
            Candidate candidate = ranked.get(i);

            if (previousValue == null
                    || candidate.value().compareTo(previousValue) != 0) {
                currentPlace = i + 1;
            }

            entries.add(toEntryView(candidate, currentPlace));
            previousValue = candidate.value();
        }

        for (Candidate candidate : nonRanked) {
            entries.add(toEntryView(candidate, null));
        }

        return new PublicDisciplineStandingsResponse(
                eventId,
                eventDisciplineId,
                resultType,
                rankingStrategy,
                TIE_POLICY,
                entries
        );
    }

    private Comparator<Candidate> displayOrder() {
        return Comparator
                .comparing(
                        (Candidate candidate) ->
                                candidate.unit().sequenceNumber(),
                        Comparator.nullsLast(Comparator.naturalOrder())
                )
                .thenComparing(
                        candidate -> candidate.unit().id().toString()
                )
                .thenComparing(
                        candidate -> candidate.entry().participantName(),
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
                )
                .thenComparing(
                        candidate -> candidate.entry().clubName(),
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
                );
    }

    private PublicDisciplineStandingsResponse.EntryView toEntryView(
            Candidate candidate,
            Integer overallPlace
    ) {
        PublicDisciplineResultsResponse.UnitView unit = candidate.unit();
        PublicDisciplineResultsResponse.EntryView entry = candidate.entry();

        return new PublicDisciplineStandingsResponse.EntryView(
                overallPlace,
                unit.id(),
                unit.label(),
                entry.place(),
                entry.participantName(),
                entry.clubName(),
                entry.rawValue(),
                entry.resultType(),
                entry.status()
        );
    }

    private BigDecimal parseValue(String rawValue, String resultType) {
        if (rawValue == null || rawValue.isBlank()) {
            throw invalidRawValue();
        }

        String value = rawValue.trim();

        try {
            if (!"TIME".equals(resultType)) {
                return new BigDecimal(value);
            }

            if (!value.contains(":")) {
                BigDecimal seconds = new BigDecimal(value);

                if (seconds.signum() < 0) {
                    throw invalidRawValue();
                }

                return seconds;
            }

            String[] parts = value.split(":", -1);

            if (parts.length != 2 || !parts[0].matches("[0-9]+")) {
                throw invalidRawValue();
            }

            BigDecimal minutes = new BigDecimal(parts[0]);
            BigDecimal seconds = new BigDecimal(parts[1]);

            if (seconds.signum() < 0
                    || seconds.compareTo(BigDecimal.valueOf(60)) >= 0) {
                throw invalidRawValue();
            }

            return minutes.multiply(BigDecimal.valueOf(60))
                    .add(seconds);
        } catch (NumberFormatException exception) {
            throw invalidRawValue();
        }
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toUpperCase(Locale.ROOT);
    }

    private BusinessException invalidRawValue() {
        return new BusinessException(
                "results.invalid_raw_value",
                "Невозможно построить общий зачёт: "
                        + "результат имеет неверный формат."
        );
    }

    private BusinessException publicNotFound() {
        return new BusinessException(
                "results.public_not_found",
                "Published results were not found."
        );
    }

    private record Candidate(
            PublicDisciplineResultsResponse.UnitView unit,
            PublicDisciplineResultsResponse.EntryView entry,
            BigDecimal value
    ) {
    }
}