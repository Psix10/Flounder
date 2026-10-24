package com.acme.sportplatform.protocol.application;

import java.math.BigDecimal;
import java.util.UUID;

public record SeedParticipant(
        UUID registrationId,
        BigDecimal seedMetric
) {
}