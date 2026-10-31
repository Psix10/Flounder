package com.acme.sportplatform.protocol.infrastructure.jpa;
import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.fasterxml.jackson.databind.JsonNode;
@Entity
@Table(name = "start_protocol_snapshots")
public class StartProtocolSnapshotEntity {
    @Id private UUID id;
    @Column(name = "event_discipline_id", nullable = false) private UUID eventDisciplineId;
    @Column(name = "rule_set_version_id", nullable = false) private UUID ruleSetVersionId;
    @Column(name = "revision_no", nullable = false) private Integer revisionNo;
    @Column(name = "operation", nullable = false, length = 32) private String operation;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_json", nullable = false, columnDefinition = "jsonb") private JsonNode input;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "protocol_json", nullable = false, columnDefinition = "jsonb") private JsonNode protocol;
    @Column(name = "created_by_user_id", nullable = false) private UUID createdByUserId;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    public UUID getId() { return id; }
    public void setId(UUID value) { id = value; }
    public UUID getEventDisciplineId() { return eventDisciplineId; }
    public void setEventDisciplineId(UUID value) { eventDisciplineId = value; }
    public UUID getRuleSetVersionId() { return ruleSetVersionId; }
    public void setRuleSetVersionId(UUID value) { ruleSetVersionId = value; }
    public Integer getRevisionNo() { return revisionNo; }
    public void setRevisionNo(Integer value) { revisionNo = value; }
    public String getOperation() { return operation; }
    public void setOperation(String value) { operation = value; }
    public JsonNode getInput() { return input; }
    public void setInput(JsonNode value) { input = value; }
    public JsonNode getProtocol() { return protocol; }
    public void setProtocol(JsonNode value) { protocol = value; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(UUID value) { createdByUserId = value; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime value) { createdAt = value; }
}
