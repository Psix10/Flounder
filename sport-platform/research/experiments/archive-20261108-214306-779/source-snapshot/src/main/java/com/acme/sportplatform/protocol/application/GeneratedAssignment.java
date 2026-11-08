package com.acme.sportplatform.protocol.application;

import java.math.BigDecimal;
import java.util.UUID;

public record GeneratedAssignment(
        UUID registrationId,
        Integer laneOrPosition,
        BigDecimal seedMetric,
        String categoryCode,
        String seedingGroupCode
) {
    public GeneratedAssignment(UUID registrationId, Integer laneOrPosition, BigDecimal seedMetric) {
        this(registrationId, laneOrPosition, seedMetric, null, null);
    }
}
