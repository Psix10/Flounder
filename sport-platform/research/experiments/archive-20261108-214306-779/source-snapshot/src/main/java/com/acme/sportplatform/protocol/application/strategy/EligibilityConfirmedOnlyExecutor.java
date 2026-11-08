package com.acme.sportplatform.protocol.application.strategy;

import org.springframework.stereotype.Component;

import com.acme.sportplatform.protocol.application.ProtocolGenerationContext;
import com.acme.sportplatform.protocol.application.RuleBlockExecutor;

@Component
public class EligibilityConfirmedOnlyExecutor implements RuleBlockExecutor {

    @Override
    public String code() {
        return "ELIGIBILITY_CONFIRMED_ONLY";
    }

    @Override
    public void apply(ProtocolGenerationContext context) {
        // no-op: participants are prefiltered before context creation
    }
}