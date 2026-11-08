package com.acme.sportplatform.results.infrastructure.jdbc;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.acme.sportplatform.results.api.FinalStandingsResponse;
import com.acme.sportplatform.common.exception.BusinessException;

@Repository
public class FinalStandingsStore {
    public record Stored(UUID requestId, FinalStandingsResponse response) { }
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public FinalStandingsStore(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc=jdbc;this.json=json; }
    @Transactional(propagation=Propagation.MANDATORY)
    public void lock(UUID discipline) {
        jdbc.query("SELECT results_finalization_lock(?)", ps->ps.setObject(1,discipline), rs->{ });
    }
    public Optional<Stored> byRequest(UUID discipline, UUID request) {
        return jdbc.query("SELECT * FROM final_standings_snapshots WHERE event_discipline_id=? AND request_id=?",
                this::row,discipline,request).stream().findFirst();
    }
    public Optional<Stored> active(UUID discipline) {
        return jdbc.query("SELECT * FROM final_standings_snapshots WHERE event_discipline_id=? AND status='CONFIRMED'",
                this::row,discipline).stream().findFirst();
    }
    public Optional<Stored> byId(UUID discipline, UUID id) {
        return jdbc.query("SELECT * FROM final_standings_snapshots WHERE event_discipline_id=? AND id=?",
                this::row,discipline,id).stream().findFirst();
    }
    public List<FinalStandingsResponse> history(UUID discipline) {
        return jdbc.query("SELECT * FROM final_standings_snapshots WHERE event_discipline_id=? ORDER BY revision_no",
                this::row,discipline).stream().map(Stored::response).toList();
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void requireReady(UUID discipline) {
        var statuses=jdbc.query("SELECT status FROM competition_units WHERE event_discipline_id=? ORDER BY id",
                (rs,n)->rs.getString(1),discipline);
        com.acme.sportplatform.results.domain.FinalizationPolicy.requirePublished(statuses);
        Boolean invalid=jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM competition_units u WHERE u.event_discipline_id=?
                    AND NOT EXISTS (SELECT 1 FROM competition_unit_entries e WHERE e.competition_unit_id=u.id)
                ) OR EXISTS (
                    SELECT 1 FROM competition_unit_entries e JOIN competition_units u ON u.id=e.competition_unit_id
                    JOIN registrations reg ON reg.id=e.registration_id
                    LEFT JOIN results r ON r.competition_unit_entry_id=e.id
                    WHERE u.event_discipline_id=? AND
                        (reg.status <> 'CONFIRMED' OR reg.event_discipline_id <> u.event_discipline_id OR r.id IS NULL
                        OR r.status NOT IN ('VALID','DID_NOT_START','DID_NOT_FINISH','DISQUALIFIED')
                        OR (r.status='VALID' AND r.final_place IS NULL))
                ) OR EXISTS (
                    SELECT reg.id FROM registrations reg WHERE reg.event_discipline_id=? AND reg.status='CONFIRMED'
                    AND (SELECT count(*) FROM competition_unit_entries e
                         JOIN competition_units u ON u.id=e.competition_unit_id
                         WHERE e.registration_id=reg.id AND u.event_discipline_id=?) <> 1
                )
                """,Boolean.class,discipline,discipline,discipline,discipline);
        if (!Boolean.FALSE.equals(invalid)) throw new BusinessException("results.final_not_ready",
                "Every confirmed registration must be assigned exactly once; heats and results must be complete");
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public FinalStandingsResponse insert(UUID event,UUID discipline,UUID request,UUID actor,String note,JsonNode payload) {
        UUID id=UUID.randomUUID();
        jdbc.update("""
                INSERT INTO final_standings_snapshots
                (id,event_id,event_discipline_id,request_id,revision_no,status,confirmed_by,confirmed_at,confirmation_note,standings_json,source_json)
                VALUES (?,?,?,?,(SELECT COALESCE(MAX(revision_no),0)+1 FROM final_standings_snapshots WHERE event_discipline_id=?),
                'CONFIRMED',?,clock_timestamp(),?,CAST(? AS jsonb),results_finalization_source(?))
                """,id,event,discipline,request,discipline,actor,note,payload.toString(),discipline);
        return byId(discipline,id).orElseThrow().response();
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public FinalStandingsResponse revoke(UUID discipline,UUID id,UUID actor,String reason) {
        jdbc.update("""
                UPDATE final_standings_snapshots SET status='REVOKED',revoked_by=?,revoked_at=clock_timestamp(),revocation_reason=?
                WHERE id=? AND event_discipline_id=? AND status='CONFIRMED'
                """,actor,reason,id,discipline);
        return byId(discipline,id).orElseThrow().response();
    }
    private Stored row(ResultSet rs,int index) throws SQLException {
        JsonNode payload;
        try {payload=json.readTree(rs.getString("standings_json"));}
        catch(java.io.IOException ex) {throw new IllegalStateException("Corrupt final standings snapshot",ex);}
        return new Stored(rs.getObject("request_id",UUID.class),new FinalStandingsResponse(
                rs.getObject("id",UUID.class),rs.getObject("event_id",UUID.class),rs.getObject("event_discipline_id",UUID.class),
                rs.getInt("revision_no"),rs.getString("status"),rs.getObject("confirmed_at",OffsetDateTime.class),
                rs.getObject("confirmed_by",UUID.class),rs.getString("confirmation_note"),payload,
                rs.getObject("revoked_at",OffsetDateTime.class),rs.getObject("revoked_by",UUID.class),rs.getString("revocation_reason")));
    }
}
