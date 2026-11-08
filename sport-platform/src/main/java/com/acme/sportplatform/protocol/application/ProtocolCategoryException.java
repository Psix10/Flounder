package com.acme.sportplatform.protocol.application;
import java.util.List;
import java.util.UUID;
import com.acme.sportplatform.common.exception.BusinessException;
public final class ProtocolCategoryException extends BusinessException {
    public record Violation(UUID registrationId, String reason, String message) { }
    private final List<Violation> violations;
    public ProtocolCategoryException(List<Violation> violations) {
        super("protocol.category_violation", "Each registration must match exactly one category");
        if (violations == null || violations.isEmpty()) throw new IllegalArgumentException("Violations required");
        this.violations = List.copyOf(violations);
    }
    public List<Violation> violations() { return violations; }
}
