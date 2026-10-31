package com.acme.sportplatform.protocol.application.strategy;
import java.util.*;
import org.springframework.stereotype.Component;
import com.acme.sportplatform.protocol.application.*;
import com.acme.sportplatform.common.exception.BusinessException;
@Component
public class GroupByCapacityExecutor implements RuleBlockExecutor {
    @Override public String code() { return "GROUP_BY_CAPACITY"; }
    @Override public void apply(ProtocolGenerationContext context) {
        int capacity = context.getUnitCapacity();
        if (capacity < 1) throw new BusinessException("protocol.invalid_settings", "Invalid capacity");
        List<SeedParticipant> participants = context.getParticipants();
        context.getGroupedAssignments().clear();
        boolean slowestFirst = "SLOWEST_FIRST".equals(context.getHeatOrder());
        boolean partialFirstInOutput = "FIRST".equals(context.getPartialHeat());
        boolean partialFirstBeforeReverse = slowestFirst ? !partialFirstInOutput : partialFirstInOutput;
        int remainder = participants.size() % capacity;
        int offset = 0;
        while (offset < participants.size()) {
            int size = offset == 0 && partialFirstBeforeReverse && remainder > 0
                    ? remainder : Math.min(capacity, participants.size() - offset);
            List<GeneratedAssignment> group = new ArrayList<>();
            for (SeedParticipant participant : participants.subList(offset, offset + size)) {
                group.add(new GeneratedAssignment(participant.registrationId(), null, participant.seedMetric()));
            }
            context.getGroupedAssignments().add(group);
            offset += size;
        }
        if (slowestFirst) Collections.reverse(context.getGroupedAssignments());
    }
}
