package com.acme.sportplatform.protocol.application.strategy;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.protocol.application.GeneratedAssignment;
import com.acme.sportplatform.protocol.application.ProtocolGenerationContext;
import com.acme.sportplatform.protocol.application.RuleBlockExecutor;

@Component
public class AssignCenterOutExecutor implements RuleBlockExecutor {

    @Override
    public String code() {
        return "ASSIGN_CENTER_OUT";
    }

    @Override
    public void apply(ProtocolGenerationContext context) {
        List<Integer> order = context.getPositionOrder();

        for (int groupIndex = 0; groupIndex < context.getGroupedAssignments().size(); groupIndex++) {
            List<GeneratedAssignment> group = context.getGroupedAssignments().get(groupIndex);

            if (order.size() < group.size()) {
                throw new BusinessException(
                        "protocol.invalid_position_order",
                        "Position order does not cover all participants in a unit"
                );
            }

            List<GeneratedAssignment> updated = new ArrayList<>();

            for (int i = 0; i < group.size(); i++) {
                GeneratedAssignment assignment = group.get(i);
                updated.add(new GeneratedAssignment(
                        assignment.registrationId(),
                        order.get(i),
                        assignment.seedMetric()
                ));
            }

            context.getGroupedAssignments().set(groupIndex, updated);
        }
    }
}