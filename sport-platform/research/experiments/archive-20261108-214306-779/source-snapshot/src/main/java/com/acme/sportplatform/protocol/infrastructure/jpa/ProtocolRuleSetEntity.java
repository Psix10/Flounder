package com.acme.sportplatform.protocol.infrastructure.jpa;

import java.util.UUID;
import java.time.OffsetDateTime;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.fasterxml.jackson.databind.JsonNode;
import com.acme.sportplatform.protocol.domain.*;

@Entity
@Table(name = "protocol_rule_sets")
public class ProtocolRuleSetEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "sport_id", nullable = false)
    private UUID sportId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "competition_format", nullable = false, length = 64)
    private CompetitionFormat competitionFormat;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type", nullable = false, length = 64)
    private ProtocolUnitType unitType;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public UUID getId() { return id; }
    public void setId(UUID value) { this.id = value; }

    public UUID getSportId() { return sportId; }
    public void setSportId(UUID value) { this.sportId = value; }

    public String getCode() { return code; }
    public void setCode(String value) { this.code = value; }

    public String getName() { return name; }
    public void setName(String value) { this.name = value; }

    public CompetitionFormat getCompetitionFormat() { return competitionFormat; }
    public void setCompetitionFormat(CompetitionFormat value) { this.competitionFormat = value; }

    public ProtocolUnitType getUnitType() { return unitType; }
    public void setUnitType(ProtocolUnitType value) { this.unitType = value; }

    public UUID getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(UUID value) { this.createdByUserId = value; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime value) { this.createdAt = value; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime value) { this.updatedAt = value; }

    public Long getVersion() { return version; }
    public void setVersion(Long value) { this.version = value; }

}
