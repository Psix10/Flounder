package com.acme.sportplatform.registrations.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.acme.sportplatform.registrations.api.RegistrationResponse;
import com.acme.sportplatform.registrations.api.ResubmitRegistrationRequest;
import com.acme.sportplatform.registrations.domain.RegistrationStatus;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationEntity;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class ResubmitRegistrationUseCase {

    private final RegistrationRepository registrationRepository;
    private final RegistrationMapper registrationMapper;
    private final ObjectMapper objectMapper;

    public ResubmitRegistrationUseCase(
            RegistrationRepository registrationRepository,
            RegistrationMapper registrationMapper,
            ObjectMapper objectMapper
    ) {
        this.registrationRepository = registrationRepository;
        this.registrationMapper = registrationMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RegistrationResponse execute(
            UUID registrationId,
            UUID participantUserId,
            ResubmitRegistrationRequest request
    ) {
        RegistrationEntity registration = registrationRepository
                .findForUpdate(registrationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Registration not found"
                ));

        if (!registration.getParticipantUserId().equals(participantUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only resubmit your own registration"
            );
        }

        if (!RegistrationStatus.NEEDS_CORRECTION.name()
                .equals(registration.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only registrations requiring correction can be resubmitted"
            );
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ObjectNode meta = readMeta(registration.getRegistrationMeta());

        JsonNode storedHistory = meta.get("correctionHistory");
        ArrayNode history;

        if (storedHistory == null || storedHistory.isNull()) {
            history = objectMapper.createArrayNode();
            meta.set("correctionHistory", history);
        } else if (storedHistory instanceof ArrayNode array) {
            history = array;
        } else {
            throw new IllegalStateException(
                    "Stored correction history must be an array"
            );
        }

        ObjectNode correction = objectMapper.createObjectNode();
        correction.put("reviewNote", registration.getReviewNote());
        correction.put(
                "reviewedAt",
                registration.getReviewedAt() == null
                        ? null
                        : registration.getReviewedAt().toString()
        );
        correction.put(
                "reviewedByUserId",
                registration.getReviewedByUserId() == null
                        ? null
                        : registration.getReviewedByUserId().toString()
        );
        correction.put("reply", request.reply().trim());
        correction.put("repliedAt", now.toString());

        history.add(correction);

        meta.put(
                "emergencyContactName",
                request.emergencyContactName().trim()
        );
        meta.put(
                "emergencyContactPhone",
                request.emergencyContactPhone().trim()
        );
        meta.put("medicalCertificate", request.medicalCertificate());
        meta.put("agreeToRules", request.agreeToRules());

        registration.setRegistrationMeta(meta.toString());
        registration.setStatus(RegistrationStatus.SUBMITTED.name());
        registration.setSubmittedAt(now);
        registration.setUpdatedAt(now);

        return registrationMapper.toResponse(
                registrationRepository.save(registration)
        );
    }

    private ObjectNode readMeta(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }

        try {
            JsonNode node = objectMapper.readTree(json);

            if (!(node instanceof ObjectNode object)) {
                throw new IllegalStateException(
                        "Stored registration metadata must be an object"
                );
            }

            return object;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Stored registration metadata is invalid",
                    exception
            );
        }
    }
}