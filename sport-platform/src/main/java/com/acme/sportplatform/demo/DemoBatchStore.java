package com.acme.sportplatform.demo;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DemoBatchStore {
    public record Stored(UUID requestId, UUID eventId, UUID disciplineId, UUID actorId,
            UUID ruleVersionId, int capacity, int count, String response) { }
    private final JdbcTemplate jdbc;
    public DemoBatchStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<Stored> byRequest(UUID id) { return find("request_id", id); }
    public Optional<Stored> byDiscipline(UUID id) { return find("event_discipline_id", id); }
    private Optional<Stored> find(String column, UUID id) {
        return jdbc.query("SELECT * FROM demo_registration_batches WHERE " + column + " = ?", (rs, row) ->
                new Stored(rs.getObject("request_id", UUID.class), rs.getObject("event_id", UUID.class),
                        rs.getObject("event_discipline_id", UUID.class), rs.getObject("actor_user_id", UUID.class),
                        rs.getObject("rule_version_id", UUID.class), rs.getInt("capacity"),
                        rs.getInt("participant_count"), rs.getString("response_json")), id).stream().findFirst();
    }
    public void reserve(UUID requestId, UUID eventId, UUID disciplineId, UUID actorId,
            UUID versionId, int capacity, int count) {
        jdbc.update("""
                INSERT INTO demo_registration_batches(request_id,event_id,event_discipline_id,actor_user_id,
                    rule_version_id,capacity,participant_count) VALUES (?,?,?,?,?,?,?)
                """, requestId, eventId, disciplineId, actorId, versionId, capacity, count);
    }
    public void finish(UUID requestId, String response) {
        int updated = jdbc.update("UPDATE demo_registration_batches SET response_json=?::jsonb WHERE request_id=?", response, requestId);
        if (updated != 1) throw new IllegalStateException("Demo receipt disappeared");
    }
}
