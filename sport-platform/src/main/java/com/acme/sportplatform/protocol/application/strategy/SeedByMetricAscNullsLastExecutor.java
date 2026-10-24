package com.acme.sportplatform.protocol.application.strategy;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.acme.sportplatform.protocol.application.ProtocolGenerationContext;
import com.acme.sportplatform.protocol.application.RuleBlockExecutor;
import com.acme.sportplatform.protocol.application.SeedParticipant;

@Component
public class SeedByMetricAscNullsLastExecutor implements RuleBlockExecutor {

    @Override
    public String code() {
        return "SEED_BY_METRIC_ASC_NULLS_LAST";
    }

    @Override
    public void apply(ProtocolGenerationContext context) {
        List<SeedParticipant> sorted = context.getParticipants().stream()
                .sorted(Comparator.comparing(
                        SeedParticipant::seedMetric,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .toList();

        context.setParticipants(sorted);
    }
}