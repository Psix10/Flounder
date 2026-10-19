package com.acme.sportplatform.results.infrastructure.jpa;
import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.persistence.*;
@Entity
@Table(name="competition_units")
public class CompetitionUnitEntity {
    @Id
    private UUID id;
    @Version @Column(name="version", nullable=false)
    private Long version;
    @Column(name="event_discipline_id", nullable=false)
    private UUID eventDisciplineId;
    @Column(name="label", nullable=false, length=100)
    private String label;
    @Column(name="sequence_number", nullable=false)
    private Integer sequenceNumber;
    @Column(name="status", nullable=false, length=32)
    private String status;
    @Column(name="scheduled_at")
    private OffsetDateTime scheduledAt;
    @Column(name="created_at", nullable=false)
    private OffsetDateTime createdAt;
    @Column(name="updated_at", nullable=false)
    private OffsetDateTime updatedAt;
    public UUID getId() { return id; }
    public void setId(UUID value) { this.id=value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { this.version=value; }
    public UUID getEventDisciplineId() { return eventDisciplineId; }
    public void setEventDisciplineId(UUID value) { this.eventDisciplineId=value; }
    public String getLabel() { return label; }
    public void setLabel(String value) { this.label=value; }
    public Integer getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(Integer value) { this.sequenceNumber=value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status=value; }
    public OffsetDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(OffsetDateTime value) { this.scheduledAt=value; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime value) { this.createdAt=value; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime value) { this.updatedAt=value; }
}
