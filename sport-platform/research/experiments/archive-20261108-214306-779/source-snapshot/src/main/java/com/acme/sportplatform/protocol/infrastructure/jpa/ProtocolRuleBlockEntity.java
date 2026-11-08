package com.acme.sportplatform.protocol.infrastructure.jpa;

import java.util.UUID;
import java.time.OffsetDateTime;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.fasterxml.jackson.databind.JsonNode;
import com.acme.sportplatform.protocol.domain.*;

@Entity
@Table(name = "protocol_rule_blocks")
public class ProtocolRuleBlockEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "rule_set_version_id", nullable = false)
    private UUID ruleSetVersionId;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(name = "executor_code", nullable = false, length = 100)
    private String executorCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode config;

    public UUID getId() { return id; }
    public void setId(UUID value) { this.id = value; }

    public UUID getRuleSetVersionId() { return ruleSetVersionId; }
    public void setRuleSetVersionId(UUID value) { this.ruleSetVersionId = value; }

    public Integer getOrderIndex() { return orderIndex; }
    public void setOrderIndex(Integer value) { this.orderIndex = value; }

    public String getExecutorCode() { return executorCode; }
    public void setExecutorCode(String value) { this.executorCode = value; }

    public JsonNode getConfig() { return config; }
    public void setConfig(JsonNode value) { this.config = value; }

}
