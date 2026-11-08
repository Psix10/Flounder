package com.acme.sportplatform.results.infrastructure.jpa;
import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.persistence.*;
@Entity
@Table(name="results")
public class ResultEntity {
    @Id
    private UUID id;
    @Version @Column(name="version", nullable=false)
    private Long version;
    @Column(name="competition_unit_entry_id", nullable=false)
    private UUID competitionUnitEntryId;
    @Column(name="raw_value", length=50)
    private String rawValue;
    @Column(name="result_type", nullable=false, length=64)
    private String resultType;
    @Column(name="status", nullable=false, length=32)
    private String status;
    @Column(name="final_place")
    private Integer finalPlace;
    @Column(name="regulation_version_id")
    private UUID regulationVersionId;
    @Column(name="recorded_by_user_id")
    private UUID recordedByUserId;
    @Column(name="created_at", nullable=false)
    private OffsetDateTime createdAt;
    @Column(name="updated_at", nullable=false)
    private OffsetDateTime updatedAt;
    public UUID getId() { return id; }
    public void setId(UUID value) { this.id=value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { this.version=value; }
    public UUID getCompetitionUnitEntryId() { return competitionUnitEntryId; }
    public void setCompetitionUnitEntryId(UUID value) { this.competitionUnitEntryId=value; }
    public String getRawValue() { return rawValue; }
    public void setRawValue(String value) { this.rawValue=value; }
    public String getResultType() { return resultType; }
    public void setResultType(String value) { this.resultType=value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status=value; }
    public Integer getFinalPlace() { return finalPlace; }
    public void setFinalPlace(Integer value) { this.finalPlace=value; }
    public UUID getRegulationVersionId() { return regulationVersionId; }
    public void setRegulationVersionId(UUID value) { this.regulationVersionId=value; }
    public UUID getRecordedByUserId() { return recordedByUserId; }
    public void setRecordedByUserId(UUID value) { this.recordedByUserId=value; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime value) { this.createdAt=value; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime value) { this.updatedAt=value; }
}
