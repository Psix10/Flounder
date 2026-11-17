package com.acme.sportplatform.payments.application;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.acme.sportplatform.payments.infrastructure.jpa.PaymentRepository;
import com.acme.sportplatform.registrations.RegistrationPaymentGuard;

@Service
public class RegistrationPaymentGuardService
        implements RegistrationPaymentGuard {

    private final PaymentRepository payments;

    public RegistrationPaymentGuardService(
            PaymentRepository payments
    ) {
        this.payments = payments;
    }

    @Override
    public boolean hasAnyPayment(UUID registrationId) {
        return payments.findByRegistrationId(registrationId)
                .isPresent();
    }
}