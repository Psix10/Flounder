package com.acme.sportplatform.results.infrastructure.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.results.api.PublicDisciplineStandingsResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
public class SqlDisciplineStandingsReader {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public SqlDisciplineStandingsReader(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public List<PublicDisciplineStandingsResponse.EntryView> execute(
            UUID eventId, UUID disciplineId, String resultType, String strategy) {
        if (!"ASC".equals(strategy) && !"DESC".equals(strategy)) {
            throw error("results.invalid_ranking_strategy", "rankingStrategy must be ASC or DESC");
        }
        String sql = """
                WITH source AS MATERIALIZED (
                    SELECT e.id AS entry_id, u.id AS unit_id,
                           u.label AS unit_label, u.sequence_number,
                           e.category_code, e.seeding_group_code,
                           r.final_place AS unit_place, r.raw_value,
                           r.result_type, COALESCE(r.status, 'PENDING') AS status,
                           reg.participant_snapshot::text AS participant_snapshot
                    FROM competition_units u
                    JOIN event_disciplines d ON d.id = u.event_discipline_id
                    JOIN competition_unit_entries e ON e.competition_unit_id = u.id
                    LEFT JOIN results r ON r.competition_unit_entry_id = e.id
                    LEFT JOIN registrations reg ON reg.id = e.registration_id
                    WHERE d.event_id = ? AND d.id = ? AND u.status = 'PUBLISHED'
                ), parsed AS MATERIALIZED (
                    SELECT s.*,
                           CASE WHEN status = 'VALID'
                                THEN results_standings_exact_value(raw_value, ?)
                                ELSE NULL END AS numeric_value,
                           ((category_code IS NULL) <> (seeding_group_code IS NULL)
                            OR (category_code IS NOT NULL AND (
                                category_code !~ '^[A-Za-z0-9][A-Za-z0-9_.-]{0,79}$'
                                OR seeding_group_code !~ '^[A-Za-z0-9][A-Za-z0-9_.-]{0,79}$'
                            ))) AS invalid_category
                    FROM source s
                ), checks AS (
                    SELECT COALESCE(bool_or(invalid_category), false) AS invalid_category,
                           COALESCE(bool_or(category_code IS NOT NULL), false)
                               AND COALESCE(bool_or(category_code IS NULL), false) AS mixed_categories,
                           COALESCE(bool_or(status NOT IN (
                               'VALID', 'DID_NOT_START', 'DID_NOT_FINISH', 'DISQUALIFIED'
                           )), false) AS invalid_status,
                           COALESCE(bool_or(status = 'VALID' AND
                               COALESCE(upper(btrim(result_type)), '') <> ?), false) AS invalid_type,
                           COALESCE(bool_or(status = 'VALID' AND numeric_value IS NULL), false) AS invalid_value
                    FROM parsed
                ), ranked AS (
                    SELECT entry_id,
                           rank() OVER (ORDER BY numeric_value %s)::integer AS overall_place,
                           CASE WHEN category_code IS NOT NULL THEN
                               rank() OVER (
                                   PARTITION BY category_code ORDER BY numeric_value %s
                               )::integer
                           ELSE NULL END AS category_place
                    FROM parsed
                    WHERE status = 'VALID' AND numeric_value IS NOT NULL
                )
                SELECT c.*, p.entry_id, p.unit_id, p.unit_label, p.unit_place,
                       p.raw_value, p.result_type, p.status,
                       p.category_code, p.seeding_group_code, p.participant_snapshot,
                       r.overall_place, r.category_place
                FROM checks c
                LEFT JOIN parsed p ON true
                LEFT JOIN ranked r ON r.entry_id = p.entry_id
                ORDER BY r.overall_place ASC NULLS LAST,
                         p.sequence_number ASC NULLS LAST,
                         p.unit_id::text COLLATE "C" ASC,
                         p.entry_id::text COLLATE "C" ASC
                """.formatted(strategy, strategy);

        return jdbc.query(sql, rs -> {
            List<PublicDisciplineStandingsResponse.EntryView> entries = new ArrayList<>();
            while (rs.next()) {
                validateChecks(rs);
                if (rs.getObject("entry_id") == null) {
                    continue;
                }
                JsonNode snapshot = snapshot(rs.getString("participant_snapshot"));
                entries.add(new PublicDisciplineStandingsResponse.EntryView(
                        nullableInt(rs, "overall_place"),
                        rs.getObject("unit_id", UUID.class),
                        rs.getString("unit_label"),
                        nullableInt(rs, "unit_place"),
                        participantName(snapshot), clubName(snapshot),
                        rs.getString("raw_value"), rs.getString("result_type"),
                        rs.getString("status"), rs.getString("category_code"),
                        rs.getString("seeding_group_code"),
                        nullableInt(rs, "category_place")
                ));
            }
            return List.copyOf(entries);
        }, eventId, disciplineId, resultType, resultType);
    }

    private void validateChecks(ResultSet rs) throws SQLException {
        if (rs.getBoolean("invalid_category")) {
            throw error("results.invalid_category_assignment", "Invalid category/group pair in published results");
        }
        if (rs.getBoolean("mixed_categories")) {
            throw error("results.mixed_category_assignments", "Published results cannot mix categorized and legacy assignments");
        }
        if (rs.getBoolean("invalid_status")) {
            throw error("results.invalid_standings_status", "Invalid result status in published unit");
        }
        if (rs.getBoolean("invalid_type")) {
            throw error("results.result_type_mismatch", "Result type differs from discipline result type");
        }
        if (rs.getBoolean("invalid_value")) {
            throw error("results.invalid_raw_value", "Invalid numeric result in published standings");
        }
    }

    private Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private JsonNode snapshot(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return json.readTree(raw); }
        catch (Exception ignored) { return null; }
    }

    private String participantName(JsonNode snapshot) {
        if (snapshot == null) return "Участник";
        List<String> parts = new ArrayList<>();
        for (String field : List.of("lastName", "firstName", "middleName")) {
            String value = snapshot.path(field).asText("").trim();
            if (!value.isBlank()) parts.add(value);
        }
        return parts.isEmpty() ? "Участник" : String.join(" ", parts);
    }

    private String clubName(JsonNode snapshot) {
        if (snapshot == null) return null;
        String value = snapshot.path("clubName").asText("").trim();
        return value.isBlank() ? null : value;
    }

    private BusinessException error(String code, String message) {
        return new BusinessException(code, message);
    }
}
