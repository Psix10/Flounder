package com.acme.sportplatform.protocol.application.strategy;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.acme.sportplatform.protocol.application.GeneratedAssignment;
import com.acme.sportplatform.protocol.application.ProtocolGenerationContext;
import com.acme.sportplatform.protocol.application.RuleBlockExecutor;
import com.acme.sportplatform.protocol.application.SeedParticipant;

@Component
public class GroupByCapacityExecutor implements RuleBlockExecutor {

    @Override
    public String code() {
        return "GROUP_BY_CAPACITY";
    }

    @Override
    public void apply(ProtocolGenerationContext context) {
        List<SeedParticipant> participants = context.getParticipants();
        int capacity = context.getUnitCapacity();

        for (int i = 0; i < participants.size(); i += capacity) {
            List<SeedParticipant> slice = participants.subList(
                    i,
                    Math.min(i + capacity, participants.size())
            );

            List<GeneratedAssignment> assignments = new ArrayList<>();

            for (SeedParticipant participant : slice) {
                assignments.add(new GeneratedAssignment(
                        participant.registrationId(),
                        null,
                        participant.seedMetric()
                ));
            }

            context.getGroupedAssignments().add(assignments);
        }
    }
}