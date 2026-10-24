package com.acme.sportplatform.protocol.application;

import java.math.BigDecimal;
import java.util.UUID;

public record GeneratedAssignment(
        UUID registrationId,
        Integer laneOrPosition,
        BigDecimal seedMetric
) {
}