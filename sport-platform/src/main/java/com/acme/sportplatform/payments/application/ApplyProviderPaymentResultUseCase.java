package com.acme.sportplatform.payments.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.payments.domain.PaymentStatus;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentEntity;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentRepository;
import com.acme.sportplatform.payments.provider.ProviderPaymentResult;
import jakarta.persistence.EntityManager;

@Service
public class ApplyProviderPaymentResultUseCase {
    private final PaymentRepository paymentRepository;
    private final EntityManager entityManager;

    public ApplyProviderPaymentResultUseCase(
            PaymentRepository paymentRepository, EntityManager entityManager
    ) {
        this.paymentRepository = paymentRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public PaymentEntity execute(UUID paymentId, ProviderPaymentResult providerResult) {
        PaymentEntity payment = paymentRepository.findForUpdate(paymentId)
                .orElseThrow(() -> new BusinessException(
                        "payments.not_found", "Payment not found"
                ));
        entityManager.refresh(payment);

        if (PaymentStatus.CANCELED.name().equals(payment.getStatus())) {
            throw new BusinessException(
                    "payments.provider_result_not_applicable",
                    "An initial provider result cannot overwrite a canceled payment"
            );
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        payment.setExternalPaymentId(providerResult.externalPaymentId());
        payment.setConfirmationUrl(providerResult.confirmationUrl());
        payment.setStatus(providerResult.status().name());
        payment.setExpiresAt(providerResult.expiresAt());
        payment.setProviderMetadata(providerResult.providerMetadata());
        payment.setUpdatedAt(now);
        return paymentRepository.save(payment);
    }
}
