package com.acme.sportplatform.results.infrastructure.jdbc;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;

@Repository
public class SqlPlacesRecalculator {

    private final JdbcTemplate jdbcTemplate;

    public SqlPlacesRecalculator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void execute(
            UUID unitId,
            boolean descending,
            OffsetDateTime updatedAt
    ) {
        lockEntries(unitId);
        lockResults(unitId);

        String direction = descending ? "DESC" : "ASC";

        String sql = """
                WITH parsed AS MATERIALIZED (
                    SELECT
                        r.id,
                        results_numeric_value(r.raw_value) AS value
                    FROM results r
                    JOIN competition_unit_entries e
                        ON e.id = r.competition_unit_entry_id
                    WHERE e.competition_unit_id = ?
                      AND r.status IN ('PENDING', 'VALID')
                ),
                invalid AS (
                    SELECT count(*) AS invalid_count
                    FROM parsed
                    WHERE value IS NULL
                ),
                ranked AS (
                    SELECT
                        id,
                        row_number() OVER (
                            ORDER BY
                                value %s,
                                id::text COLLATE "C" ASC
                        )::INTEGER AS place
                    FROM parsed
                    WHERE value IS NOT NULL
                ),
                updated AS (
                    UPDATE results r
                    SET
                        final_place = ranked.place,
                        status = 'VALID',
                        updated_at = ?,
                        version = r.version + 1
                    FROM ranked, invalid
                    WHERE r.id = ranked.id
                      AND invalid.invalid_count = 0
                    RETURNING r.id
                )
                SELECT
                    (SELECT invalid_count FROM invalid) AS invalid_count,
                    (SELECT count(*) FROM updated) AS updated_count
                """.formatted(direction);

        RankingOutcome outcome = jdbcTemplate.queryForObject(
                sql,
                (rs, rowNum) -> new RankingOutcome(
                        rs.getLong("invalid_count"),
                        rs.getLong("updated_count")
                ),
                unitId,
                updatedAt
        );

        if (outcome == null) {
            throw new IllegalStateException(
                    "SQL recalculation did not return an outcome"
            );
        }

        if (outcome.invalidCount() > 0) {
            throw new BusinessException(
                    "results.invalid_raw_value",
                    "Нельзя пересчитать места: один или несколько "
                            + "результатов имеют неверный формат."
            );
        }
    }

    private void lockEntries(UUID unitId) {
        jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM (
                    SELECT id
                    FROM competition_unit_entries
                    WHERE competition_unit_id = ?
                    ORDER BY id
                    FOR UPDATE
                ) locked_entries
                """,
                Long.class,
                unitId
        );
    }

    private void lockResults(UUID unitId) {
        jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM (
                    SELECT r.id
                    FROM results r
                    JOIN competition_unit_entries e
                        ON e.id = r.competition_unit_entry_id
                    WHERE e.competition_unit_id = ?
                    ORDER BY r.id
                    FOR UPDATE OF r
                ) locked_results
                """,
                Long.class,
                unitId
        );
    }

    private record RankingOutcome(
            long invalidCount,
            long updatedCount
    ) {
    }
}