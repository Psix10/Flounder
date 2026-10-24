package com.acme.sportplatform.protocol.api;

import java.math.BigDecimal;
import java.util.UUID;

public record GeneratedAssignmentResponse(
        UUID registrationId,
        Integer laneOrPosition,
        BigDecimal seedMetric
) {
}