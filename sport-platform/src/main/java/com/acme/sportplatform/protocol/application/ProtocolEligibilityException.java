package com.acme.sportplatform.protocol.application;

import java.util.List;
import java.util.UUID;
import com.acme.sportplatform.common.exception.BusinessException;

public final class ProtocolEligibilityException extends BusinessException {
    public record Violation(UUID registrationId,String field,String reason,String message) { }
    private final List<Violation> violations;
    public ProtocolEligibilityException(List<Violation> violations) {
        super("protocol.eligibility_violation","Eligibility checks failed; registration IDs and reasons are listed in violations");
        if(violations==null || violations.isEmpty()) throw new IllegalArgumentException("Violations are required");
        this.violations=List.copyOf(violations);
    }
    public List<Violation> violations(){return violations;}
}
