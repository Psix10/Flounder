package com.acme.sportplatform.payments.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.payments.api.PaymentResponse;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentEntity;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentRepository;
import com.acme.sportplatform.registrations.RegistrationLookup;
import com.acme.sportplatform.registrations.RegistrationLookupResult;

@Service
@Transactional(readOnly = true)
public class GetPaymentForRegistrationUseCase {

    private final RegistrationLookup registrationLookup;
    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;

    public GetPaymentForRegistrationUseCase(
            RegistrationLookup registrationLookup,
            PaymentRepository paymentRepository,
            PaymentMapper paymentMapper
    ) {
        this.registrationLookup = registrationLookup;
        this.paymentRepository = paymentRepository;
        this.paymentMapper = paymentMapper;
    }

    public PaymentResponse execute(
            UUID registrationId,
            UUID participantUserId
    ) {
        RegistrationLookupResult registration;

        try {
            registration = registrationLookup.getById(registrationId);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    "payments.registration_not_found",
                    "Registration not found"
            );
        }

        if (!registration.participantUserId().equals(participantUserId)) {
            throw new BusinessException(
                    "payments.registration_access_denied",
                    "You cannot access another participant payment"
            );
        }

        PaymentEntity payment = paymentRepository
                .findByRegistrationId(registrationId)
                .orElseThrow(() -> new BusinessException(
                        "payments.not_found",
                        "Payment not found for registration"
                ));

        return paymentMapper.toResponse(payment);
    }
}