package com.acme.sportplatform.payments.application;

import java.math.BigDecimal;
import java.util.UUID;

import com.acme.sportplatform.payments.domain.PaymentProvider;

public record CreatedLocalPayment(
        UUID paymentId,
        UUID registrationId,
        BigDecimal amount,
        String currency,
        UUID idempotencyKey,
        PaymentProvider provider
) {
}