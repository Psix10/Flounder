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
import com.acme.sportplatform.registrations.RegistrationPaymentGuard;
import com.acme.sportplatform.registrations.RegistrationProtocolGuard;
import com.acme.sportplatform.registrations.api.RegistrationResponse;
import com.acme.sportplatform.registrations.api.ReturnRegistrationForCorrectionRequest;
import com.acme.sportplatform.registrations.domain.RegistrationStatus;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationEntity;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.persistence.EntityManager;

@Service
public class ReturnRegistrationForCorrectionUseCase {

    private final RegistrationRepository registrations;
    private final RegistrationMapper mapper;
    private final EventOperationAccess eventAccess;
    private final ProtocolDisciplineLookup disciplines;
    private final RegistrationProtocolGuard protocolGuard;
    private final RegistrationPaymentGuard paymentGuard;
    private final ObjectMapper json;
    private final EntityManager entityManager;

    public ReturnRegistrationForCorrectionUseCase(
            RegistrationRepository registrations,
            RegistrationMapper mapper,
            EventOperationAccess eventAccess,
            ProtocolDisciplineLookup disciplines,
            RegistrationProtocolGuard protocolGuard,
            RegistrationPaymentGuard paymentGuard,
            ObjectMapper json,
            EntityManager entityManager
    ) {
        this.registrations = registrations;
        this.mapper = mapper;
        this.eventAccess = eventAccess;
        this.disciplines = disciplines;
        this.protocolGuard = protocolGuard;
        this.paymentGuard = paymentGuard;
        this.json = json;
        this.entityManager = entityManager;
    }

    @Transactional
    public RegistrationResponse execute(
            UUID registrationId,
            ReturnRegistrationForCorrectionRequest request
    ) {
        var authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal()
                     instanceof AuthenticatedUserPrincipal principal)
                || principal.getUserId() == null) {
            throw new AccessDeniedException(
                    "Authenticated platform principal is required"
            );
        }

        UUID actorId = principal.getUserId();

        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        "ROLE_PLATFORM_ADMIN".equals(
                                authority.getAuthority()
                        )
                );

        boolean organizer = authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        "ROLE_ORGANIZER".equals(
                                authority.getAuthority()
                        )
                );

        if (!admin && !organizer) {
            throw new AccessDeniedException(
                    "Organizer access is required"
            );
        }

        if (request == null
                || request.reviewNote() == null
                || request.reviewNote().isBlank()
                || request.reviewNote().length() > 2000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Review note must contain 1 to 2000 characters"
            );
        }

        RegistrationEntity observed = registrations
                .findById(registrationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Registration not found"
                ));

        UUID eventId = observed.getEventId();
        UUID disciplineId = observed.getEventDisciplineId();

        requireEventAccess(eventId, actorId, admin);

        protocolGuard.lockEventBatch(eventId);

        var discipline = disciplines.lock(disciplineId);

        if (!eventId.equals(discipline.eventId())) {
            throw conflict(
                    "Registration discipline does not belong to its event"
            );
        }

        RegistrationEntity registration = registrations
                .findForUpdate(registrationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Registration not found"
                ));

        // Reload after locking: the initial read may be in the JPA cache.
        entityManager.refresh(registration);

        if (!eventId.equals(registration.getEventId())
                || !disciplineId.equals(
                        registration.getEventDisciplineId()
                )) {
            throw conflict(
                    "Registration event or discipline has changed"
            );
        }

        requireEventAccess(eventId, actorId, admin);

        if (!RegistrationStatus.CONFIRMED.name()
                .equals(registration.getStatus())) {
            throw conflict(
                    "Only confirmed registrations can be returned "
                            + "for correction"
            );
        }

        if (paymentGuard.hasAnyPayment(registrationId)) {
            throw conflict(
                    "Registration already has a payment; "
                            + "a separate correction procedure is required"
            );
        }

        protocolGuard.requireNoProtocolsOrUnits(
                eventId,
                disciplineId
        );

        ObjectNode meta = readMeta(
                registration.getRegistrationMeta()
        );

        ArrayNode history = returnHistory(meta);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String note = request.reviewNote().trim();

        ObjectNode item = json.createObjectNode();
        item.put("operation", "RETURN_CONFIRMED_FOR_CORRECTION");
        item.put("actorUserId", actorId.toString());
        item.put("occurredAt", now.toString());
        item.put("fromStatus", registration.getStatus());
        item.put(
                "toStatus",
                RegistrationStatus.NEEDS_CORRECTION.name()
        );
        item.put("reason", note);
        item.put(
                "previousReviewNote",
                registration.getReviewNote()
        );
        item.put(
                "previousReviewedByUserId",
                registration.getReviewedByUserId() == null
                        ? null
                        : registration.getReviewedByUserId().toString()
        );
        item.put(
                "previousReviewedAt",
                registration.getReviewedAt() == null
                        ? null
                        : registration.getReviewedAt().toString()
        );
        history.add(item);

        ObjectNode workflow = json.createObjectNode();
        workflow.put("reservePlace", true);
        workflow.put("returnedAt", now.toString());
        workflow.put("returnedByUserId", actorId.toString());
        meta.set("confirmedCorrection", workflow);

        registration.setRegistrationMeta(meta.toString());
        registration.setStatus(
                RegistrationStatus.NEEDS_CORRECTION.name()
        );
        registration.setReviewNote(note);
        registration.setReviewedByUserId(actorId);
        registration.setReviewedAt(now);
        registration.setUpdatedAt(now);

        return mapper.toResponse(
                registrations.saveAndFlush(registration)
        );
    }

    private void requireEventAccess(
            UUID eventId,
            UUID actorId,
            boolean admin
    ) {
        if (!admin && !eventAccess.canManageEvent(eventId, actorId)) {
            throw new AccessDeniedException(
                    "You cannot manage registrations for this event"
            );
        }
    }

    private ObjectNode readMeta(String raw) {
        if (raw == null || raw.isBlank()) {
            throw conflict(
                    "Stored registration metadata is missing"
            );
        }

        try {
            JsonNode value = json.readTree(raw);

            if (value instanceof ObjectNode object) {
                return object;
            }

            throw conflict(
                    "Stored registration metadata must be an object"
            );
        } catch (JsonProcessingException exception) {
            throw conflict(
                    "Stored registration metadata is invalid"
            );
        }
    }

    private ArrayNode returnHistory(ObjectNode meta) {
        JsonNode value = meta.get("reviewReturnHistory");

        if (value == null || value.isNull()) {
            ArrayNode array = json.createArrayNode();
            meta.set("reviewReturnHistory", array);
            return array;
        }

        if (value instanceof ArrayNode array) {
            return array;
        }

        throw conflict(
                "Stored review return history must be an array"
        );
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                message
        );
    }
}