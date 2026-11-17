package com.acme.sportplatform.registrations.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.acme.sportplatform.competition.ProtocolDisciplineLookup;
import com.acme.sportplatform.events.EventOperationAccess;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.registrations.RegistrationProtocolGuard;
import com.acme.sportplatform.registrations.RegistrationWithdrawalPaymentAccess;
import com.acme.sportplatform.registrations.api.RegistrationResponse;
import com.acme.sportplatform.registrations.api.WithdrawConfirmedRegistrationRequest;
import com.acme.sportplatform.registrations.domain.RegistrationStatus;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;

@Service
public class WithdrawConfirmedRegistrationUseCase {
    private final RegistrationRepository registrations;
    private final RegistrationMapper mapper;
    private final EventOperationAccess eventAccess;
    private final ProtocolDisciplineLookup disciplines;
    private final RegistrationProtocolGuard protocolGuard;
    private final RegistrationWithdrawalPaymentAccess paymentAccess;
    private final ObjectMapper json;
    private final EntityManager entityManager;

    public WithdrawConfirmedRegistrationUseCase(
            RegistrationRepository registrations, RegistrationMapper mapper,
            EventOperationAccess eventAccess, ProtocolDisciplineLookup disciplines,
            RegistrationProtocolGuard protocolGuard,
            RegistrationWithdrawalPaymentAccess paymentAccess,
            ObjectMapper json, EntityManager entityManager
    ) {
        this.registrations = registrations;
        this.mapper = mapper;
        this.eventAccess = eventAccess;
        this.disciplines = disciplines;
        this.protocolGuard = protocolGuard;
        this.paymentAccess = paymentAccess;
        this.json = json;
        this.entityManager = entityManager;
    }

    @Transactional
    public RegistrationResponse execute(
            UUID registrationId, WithdrawConfirmedRegistrationRequest request
    ) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUserPrincipal principal)
                || principal.getUserId() == null) {
            throw new AccessDeniedException("Authenticated platform principal is required");
        }
        UUID actorId = principal.getUserId();
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_PLATFORM_ADMIN".equals(a.getAuthority()));
        boolean organizer = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ORGANIZER".equals(a.getAuthority()));
        if (!admin && !organizer) {
            throw new AccessDeniedException("Organizer access is required");
        }
        if (request == null || request.reason() == null
                || request.reason().isBlank() || request.reason().length() > 2000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Reason must contain 1 to 2000 characters"
            );
        }
        var observed = registrations.findById(registrationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Registration not found"
                ));
        UUID eventId = observed.getEventId();
        UUID disciplineId = observed.getEventDisciplineId();
        requireAccess(eventId, actorId, admin);

        protocolGuard.lockEventBatch(eventId);
        var discipline = disciplines.lock(disciplineId);
        if (!eventId.equals(discipline.eventId())) {
            throw conflict("Registration discipline does not belong to its event");
        }
        var registration = registrations.findForUpdate(registrationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Registration not found"
                ));
        entityManager.refresh(registration);
        if (!eventId.equals(registration.getEventId())
                || !disciplineId.equals(registration.getEventDisciplineId())) {
            throw conflict("Registration event or discipline has changed");
        }
        requireAccess(eventId, actorId, admin);
        if (!RegistrationStatus.CONFIRMED.name().equals(registration.getStatus())) {
            throw conflict("Only confirmed registrations can be withdrawn by this procedure");
        }
        protocolGuard.requireNoProtocolsOrUnits(eventId, disciplineId);

        ObjectNode meta = readMeta(registration.getRegistrationMeta());
        var storedHistory = meta.get("withdrawalHistory");
        ArrayNode history;
        if (storedHistory == null || storedHistory.isNull()) {
            history = json.createArrayNode();
            meta.set("withdrawalHistory", history);
        } else if (storedHistory instanceof ArrayNode array) {
            history = array;
        } else {
            throw conflict("Stored withdrawal history must be an array");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String reason = request.reason().trim();

        paymentAccess.cancelUnpaidManualPaymentIfPresent(
                registrationId, actorId, reason, now
        );

        ObjectNode audit = json.createObjectNode();
        audit.put("operation", "WITHDRAW_CONFIRMED_REGISTRATION");
        audit.put("actorUserId", actorId.toString());
        audit.put("occurredAt", now.toString());
        audit.put("fromStatus", registration.getStatus());
        audit.put("toStatus", RegistrationStatus.WITHDRAWN.name());
        audit.put("reason", reason);
        audit.put("previousReviewNote", registration.getReviewNote());
        audit.put("previousReviewedByUserId", registration.getReviewedByUserId() == null
                ? null : registration.getReviewedByUserId().toString());
        audit.put("previousReviewedAt", registration.getReviewedAt() == null
                ? null : registration.getReviewedAt().toString());
        history.add(audit);

        registration.setRegistrationMeta(meta.toString());
        registration.setStatus(RegistrationStatus.WITHDRAWN.name());
        registration.setReviewNote(reason);
        registration.setReviewedByUserId(actorId);
        registration.setReviewedAt(now);
        registration.setUpdatedAt(now);
        return mapper.toResponse(registrations.saveAndFlush(registration));
    }

    private void requireAccess(UUID eventId, UUID actorId, boolean admin) {
        if (!admin && !eventAccess.canManageEvent(eventId, actorId)) {
            throw new AccessDeniedException("You cannot manage registrations for this event");
        }
    }

    private ObjectNode readMeta(String raw) {
        if (raw == null || raw.isBlank()) {
            throw conflict("Stored registration metadata is missing");
        }
        try {
            var value = json.readTree(raw);
            if (value instanceof ObjectNode object) {
                return object;
            }
            throw conflict("Stored registration metadata must be an object");
        } catch (JsonProcessingException exception) {
            throw conflict("Stored registration metadata is invalid");
        }
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
