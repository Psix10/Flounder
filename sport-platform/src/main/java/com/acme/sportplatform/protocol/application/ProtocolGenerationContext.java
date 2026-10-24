package com.acme.sportplatform.protocol.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ProtocolGenerationContext {

    private final UUID eventDisciplineId;
    private final int unitCapacity;
    private final String unitLabelPrefix;
    private final List<Integer> positionOrder;
    private List<SeedParticipant> participants;
    private final List<List<GeneratedAssignment>> groupedAssignments;

    public ProtocolGenerationContext(
            UUID eventDisciplineId,
            int unitCapacity,
            String unitLabelPrefix,
            List<Integer> positionOrder,
            List<SeedParticipant> participants
    ) {
        this.eventDisciplineId = eventDisciplineId;
        this.unitCapacity = unitCapacity;
        this.unitLabelPrefix = unitLabelPrefix;
        this.positionOrder = positionOrder;
        this.participants = new ArrayList<>(participants);
        this.groupedAssignments = new ArrayList<>();
    }

    public UUID getEventDisciplineId() {
        return eventDisciplineId;
    }

    public int getUnitCapacity() {
        return unitCapacity;
    }

    public String getUnitLabelPrefix() {
        return unitLabelPrefix;
    }

    public List<Integer> getPositionOrder() {
        return positionOrder;
    }

    public List<SeedParticipant> getParticipants() {
        return participants;
    }

    public void setParticipants(List<SeedParticipant> participants) {
        this.participants = participants;
    }

    public List<List<GeneratedAssignment>> getGroupedAssignments() {
        return groupedAssignments;
    }
}