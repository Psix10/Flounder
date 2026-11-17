package com.acme.sportplatform.protocol.infrastructure.jdbc;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.protocol.SessionProtocolSnapshotReader;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
@Transactional(readOnly = true)
public class JdbcSessionProtocolSnapshotReader
        implements SessionProtocolSnapshotReader {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcSessionProtocolSnapshotReader(
            JdbcTemplate jdbc,
            ObjectMapper json
    ) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<JsonNode> findLatestProtocol(UUID eventId) {
        var rows = jdbc.query(
                """
                SELECT protocol_json::text AS protocol
                FROM session_protocol_snapshots
                WHERE event_id = ?
                ORDER BY revision_no DESC
                LIMIT 1
                """,
                (rs, rowNum) -> parseProtocol(
                        rs.getString("protocol")
                ),
                eventId
        );

        return rows.stream().findFirst();
    }

    private JsonNode parseProtocol(String raw) {
        try {
            JsonNode protocol = json.readTree(raw);

            if (protocol == null || !protocol.isObject()) {
                throw new IllegalStateException(
                        "Saved session protocol must be a JSON object"
                );
            }

            return protocol;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Saved session protocol cannot be read",
                    exception
            );
        }
    }
}