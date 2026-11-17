package com.acme.sportplatform.payments.application;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.acme.sportplatform.payments.domain.PaymentProvider;
import com.acme.sportplatform.payments.domain.PaymentStatus;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentRepository;
import com.acme.sportplatform.registrations.RegistrationWithdrawalPaymentAccess;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class RegistrationWithdrawalPaymentService
        implements RegistrationWithdrawalPaymentAccess {
    private final PaymentRepository payments;
    private final EntityManager entityManager;
    private final ObjectMapper json;

    public RegistrationWithdrawalPaymentService(
            PaymentRepository payments, EntityManager entityManager, ObjectMapper json
    ) {
        this.payments = payments;
        this.entityManager = entityManager;
        this.json = json;
    }

    @Override
    public void cancelUnpaidManualPaymentIfPresent(
            UUID registrationId, UUID actorId, String reason, OffsetDateTime now
    ) {
        var found = payments.findForUpdateByRegistrationId(registrationId);
        if (found.isEmpty()) {
            return;
        }
        var payment = found.get();
        entityManager.refresh(payment);

        if (!PaymentProvider.MANUAL.name().equals(payment.getProvider())
                || !PaymentStatus.CREATED.name().equals(payment.getStatus())
                || payment.getPaidAt() != null
                || payment.getCanceledAt() != null
                || payment.getExternalPaymentId() != null
                || payment.getConfirmationUrl() != null) {
            throw conflict("Only an unpaid CREATED manual payment without external "
                    + "payment details can be canceled during withdrawal");
        }

        ObjectNode metadata;
        try {
            var parsed = json.readTree(payment.getProviderMetadata());
            if (!(parsed instanceof ObjectNode object)) {
                throw conflict("Payment metadata must be a JSON object");
            }
            metadata = object;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw conflict("Payment metadata is invalid");
        }
        if (metadata.has("registrationWithdrawal")) {
            throw conflict("Payment already contains withdrawal audit data");
        }

        ObjectNode audit = json.createObjectNode();
        audit.put("operation", "CANCEL_MANUAL_PAYMENT_FOR_REGISTRATION_WITHDRAWAL");
        audit.put("registrationId", registrationId.toString());
        audit.put("actorUserId", actorId.toString());
        audit.put("occurredAt", now.toString());
        audit.put("reason", reason);
        audit.put("fromStatus", payment.getStatus());
        audit.put("toStatus", PaymentStatus.CANCELED.name());
        metadata.set("registrationWithdrawal", audit);

        payment.setProviderMetadata(metadata.toString());
        payment.setStatus(PaymentStatus.CANCELED.name());
        payment.setCanceledAt(now);
        payment.setUpdatedAt(now);
        payments.saveAndFlush(payment);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
