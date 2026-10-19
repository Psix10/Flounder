package com.acme.sportplatform.results.infrastructure.jpa;
import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.persistence.*;
@Entity
@Table(name="competition_unit_entries")
public class CompetitionUnitEntryEntity {
    @Id
    private UUID id;
    @Column(name="competition_unit_id", nullable=false)
    private UUID competitionUnitId;
    @Column(name="registration_id", nullable=false)
    private UUID registrationId;
    @Column(name="lane_or_position")
    private Integer laneOrPosition;
    @Column(name="created_at", nullable=false)
    private OffsetDateTime createdAt;
    public UUID getId() { return id; }
    public void setId(UUID value) { this.id=value; }
    public UUID getCompetitionUnitId() { return competitionUnitId; }
    public void setCompetitionUnitId(UUID value) { this.competitionUnitId=value; }
    public UUID getRegistrationId() { return registrationId; }
    public void setRegistrationId(UUID value) { this.registrationId=value; }
    public Integer getLaneOrPosition() { return laneOrPosition; }
    public void setLaneOrPosition(Integer value) { this.laneOrPosition=value; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime value) { this.createdAt=value; }
}
