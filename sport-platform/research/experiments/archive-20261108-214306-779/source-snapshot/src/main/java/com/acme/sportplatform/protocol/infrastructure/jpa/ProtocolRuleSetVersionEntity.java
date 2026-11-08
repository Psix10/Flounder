package com.acme.sportplatform.protocol.infrastructure.jpa;

import java.util.UUID;
import java.time.OffsetDateTime;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.fasterxml.jackson.databind.JsonNode;
import com.acme.sportplatform.protocol.domain.*;

@Entity
@Table(name = "protocol_rule_set_versions")
public class ProtocolRuleSetVersionEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "rule_set_id", nullable = false)
    private UUID ruleSetId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ProtocolRuleSetStatus status;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public UUID getId() { return id; }
    public void setId(UUID value) { this.id = value; }

    public UUID getRuleSetId() { return ruleSetId; }
    public void setRuleSetId(UUID value) { this.ruleSetId = value; }

    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer value) { this.versionNo = value; }

    public ProtocolRuleSetStatus getStatus() { return status; }
    public void setStatus(ProtocolRuleSetStatus value) { this.status = value; }

    public UUID getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(UUID value) { this.createdByUserId = value; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime value) { this.createdAt = value; }

    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime value) { this.publishedAt = value; }

    public Long getVersion() { return version; }
    public void setVersion(Long value) { this.version = value; }

}
