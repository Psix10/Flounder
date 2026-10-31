package com.acme.sportplatform.protocol.application;
import java.util.*;
public class ProtocolGenerationContext {
    private final UUID eventDisciplineId;
    private final int unitCapacity;
    private final String unitLabelPrefix;
    private final List<Integer> positionOrder;
    private final List<Integer> allowedPositions;
    private final String heatOrder;
    private final String partialHeat;
    private List<SeedParticipant> participants;
    private final List<List<GeneratedAssignment>> groupedAssignments = new ArrayList<>();
    public ProtocolGenerationContext(UUID id, int capacity, String prefix,
            List<Integer> order, List<SeedParticipant> participants) {
        this(id, capacity, prefix, order,
                java.util.stream.IntStream.rangeClosed(1, capacity).boxed().toList(),
                "FASTEST_FIRST", "LAST", participants);
    }
    public ProtocolGenerationContext(UUID id, int capacity, String prefix,
            List<Integer> order, List<Integer> allowed, String heatOrder,
            String partialHeat, List<SeedParticipant> participants) {
        this.eventDisciplineId = id; this.unitCapacity = capacity;
        this.unitLabelPrefix = prefix; this.positionOrder = List.copyOf(order);
        this.allowedPositions = List.copyOf(allowed);
        this.heatOrder = heatOrder; this.partialHeat = partialHeat;
        this.participants = new ArrayList<>(participants);
    }
    public UUID getEventDisciplineId() { return eventDisciplineId; }
    public int getUnitCapacity() { return unitCapacity; }
    public String getUnitLabelPrefix() { return unitLabelPrefix; }
    public List<Integer> getPositionOrder() { return positionOrder; }
    public List<Integer> getAllowedPositions() { return allowedPositions; }
    public String getHeatOrder() { return heatOrder; }
    public String getPartialHeat() { return partialHeat; }
    public List<SeedParticipant> getParticipants() { return participants; }
    public void setParticipants(List<SeedParticipant> value) { participants = new ArrayList<>(value); }
    public List<List<GeneratedAssignment>> getGroupedAssignments() { return groupedAssignments; }
}
