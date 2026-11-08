package com.acme.sportplatform.protocol.infrastructure.jdbc;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.acme.sportplatform.protocol.SessionProtocolSnapshotAccess;
@Repository
@Transactional(propagation=Propagation.MANDATORY)
public class SessionProtocolSnapshotStore implements SessionProtocolSnapshotAccess {
    private final JdbcTemplate jdbc;
    public SessionProtocolSnapshotStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override public void lockEventBatch(UUID eventId) {
        long key=eventId.getMostSignificantBits() ^ eventId.getLeastSignificantBits() ^ 0x53455353494F4E4CL;
        jdbc.query("SELECT pg_advisory_xact_lock(?)",ps->ps.setLong(1,key),rs->{ });
    }
    @Override public int nextRevision(UUID eventId) {
        Integer revision=jdbc.queryForObject("SELECT COALESCE(MAX(revision_no),0)+1 FROM session_protocol_snapshots WHERE event_id=?",Integer.class,eventId);
        return java.util.Objects.requireNonNull(revision);
    }
    @Override public void save(UUID id,UUID eventId,int revision,JsonNode input,JsonNode protocol,UUID actorId,OffsetDateTime createdAt) {
        jdbc.update("""
                INSERT INTO session_protocol_snapshots
                (id,event_id,revision_no,input_json,protocol_json,created_by_user_id,created_at)
                VALUES (?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),?,?)
                """,id,eventId,revision,input.toString(),protocol.toString(),actorId,createdAt);
    }
}
