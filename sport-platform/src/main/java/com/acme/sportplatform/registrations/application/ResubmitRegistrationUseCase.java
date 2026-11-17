package com.acme.sportplatform.registrations.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.acme.sportplatform.competition.ProtocolDisciplineLookup;
import com.acme.sportplatform.identity.ProfileLookup;
import com.acme.sportplatform.identity.ProfileLookupResult;
import com.acme.sportplatform.registrations.RegistrationPaymentGuard;
import com.acme.sportplatform.registrations.RegistrationProtocolGuard;
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

import jakarta.persistence.EntityManager;

@Service
public class ResubmitRegistrationUseCase {

    private final RegistrationRepository registrations;
    private final RegistrationMapper mapper;
    private final ObjectMapper json;
    private final ProfileLookup profiles;
    private final ProtocolDisciplineLookup disciplines;
    private final RegistrationProtocolGuard protocolGuard;
    private final RegistrationPaymentGuard paymentGuard;
    private final EntityManager entityManager;

    public ResubmitRegistrationUseCase(
            RegistrationRepository registrations,
            RegistrationMapper mapper,
            ObjectMapper json,
            ProfileLookup profiles,
            ProtocolDisciplineLookup disciplines,
            RegistrationProtocolGuard protocolGuard,
            RegistrationPaymentGuard paymentGuard,
            EntityManager entityManager
    ) {
        this.registrations = registrations;
        this.mapper = mapper;
        this.json = json;
        this.profiles = profiles;
        this.disciplines = disciplines;
        this.protocolGuard = protocolGuard;
        this.paymentGuard = paymentGuard;
        this.entityManager = entityManager;
    }

    @Transactional
    public RegistrationResponse execute(
            UUID registrationId,
            UUID participantUserId,
            ResubmitRegistrationRequest request
    ) {
        if (participantUserId == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Authenticated participant is required"
            );
        }

        RegistrationEntity observed = registrations
                .findById(registrationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Registration not found"
                ));

        requireOwner(observed, participantUserId);

        UUID eventId = observed.getEventId();
        UUID disciplineId = observed.getEventDisciplineId();

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

        entityManager.refresh(registration);

        requireOwner(registration, participantUserId);

        if (!eventId.equals(registration.getEventId())
                || !disciplineId.equals(
                        registration.getEventDisciplineId()
                )) {
            throw conflict(
                    "Registration event or discipline has changed"
            );
        }

        if (!RegistrationStatus.NEEDS_CORRECTION.name()
                .equals(registration.getStatus())) {
            throw conflict(
                    "Only registrations requiring correction "
                            + "can be resubmitted"
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

        ObjectNode meta = readObject(
                registration.getRegistrationMeta(),
                "registration metadata"
        );

        ArrayNode history = correctionHistory(meta);

        boolean returnedFromConfirmed = meta
                .path("confirmedCorrection")
                .path("reservePlace")
                .asBoolean(false);

        ObjectNode snapshot = null;
        ObjectNode genderChange = null;

        if (request.refreshGenderFromProfile()) {
            ProfileLookupResult profile;

            try {
                profile = profiles.getByUserId(participantUserId);
            } catch (IllegalArgumentException exception) {
                throw conflict(
                        "Participant profile not found"
                );
            }

            if (profile == null
                    || !participantUserId.equals(profile.userId())
                    || !registration.getParticipantProfileId()
                            .equals(profile.id())) {
                throw conflict(
                        "Participant profile does not match registration"
                );
            }

            if (!isSupportedGender(profile.gender())) {
                throw conflict(
                        "Set MALE or FEMALE in your profile "
                                + "before updating the registration"
                );
            }

            snapshot = readObject(
                    registration.getParticipantSnapshot(),
                    "participant snapshot"
            );

            JsonNode previousGender = snapshot.get("gender");

            genderChange = json.createObjectNode();
            genderChange.put(
                    "source",
                    "PARTICIPANT_PROFILE"
            );
            genderChange.put(
                    "sourceProfileId",
                    profile.id().toString()
            );
            genderChange.put(
                    "confirmedByUserId",
                    participantUserId.toString()
            );

            genderChange.set(
                    "oldValue",
                    previousGender == null
                            ? json.nullNode()
                            : previousGender.deepCopy()
            );
            genderChange.put("newValue", profile.gender());

            snapshot.put("gender", profile.gender());
        } else if (returnedFromConfirmed) {
            ObjectNode storedSnapshot = readObject(
                    registration.getParticipantSnapshot(),
                    "participant snapshot"
            );

            JsonNode storedGender = storedSnapshot.get("gender");

            if (storedGender == null
                    || !storedGender.isTextual()
                    || !isSupportedGender(storedGender.textValue())) {
                throw conflict(
                        "Explicitly confirm copying gender "
                                + "from your profile before resubmitting"
                );
            }
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        ObjectNode correction = json.createObjectNode();
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
        correction.put(
                "repliedByUserId",
                participantUserId.toString()
        );

        if (genderChange != null) {
            genderChange.put("confirmedAt", now.toString());
            correction.set("genderChange", genderChange);
        }

        history.add(correction);

        meta.put(
                "emergencyContactName",
                request.emergencyContactName().trim()
        );
        meta.put(
                "emergencyContactPhone",
                request.emergencyContactPhone().trim()
        );
        meta.put(
                "medicalCertificate",
                request.medicalCertificate()
        );
        meta.put("agreeToRules", request.agreeToRules());

        if (snapshot != null) {
            registration.setParticipantSnapshot(
                    snapshot.toString()
            );
        }

        registration.setRegistrationMeta(meta.toString());
        registration.setStatus(
                RegistrationStatus.SUBMITTED.name()
        );
        registration.setSubmittedAt(now);
        registration.setUpdatedAt(now);

        return mapper.toResponse(
                registrations.saveAndFlush(registration)
        );
    }

    private void requireOwner(
            RegistrationEntity registration,
            UUID participantUserId
    ) {
        if (!participantUserId.equals(
                registration.getParticipantUserId()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only resubmit your own registration"
            );
        }
    }

    private boolean isSupportedGender(String value) {
        return "MALE".equals(value) || "FEMALE".equals(value);
    }

    private ArrayNode correctionHistory(ObjectNode meta) {
        JsonNode storedHistory = meta.get("correctionHistory");

        if (storedHistory == null || storedHistory.isNull()) {
            ArrayNode history = json.createArrayNode();
            meta.set("correctionHistory", history);
            return history;
        }

        if (storedHistory instanceof ArrayNode array) {
            return array;
        }

        throw conflict(
                "Stored correction history must be an array"
        );
    }

    private ObjectNode readObject(
            String raw,
            String description
    ) {
        if (raw == null || raw.isBlank()) {
            throw conflict(
                    "Stored " + description + " is missing"
            );
        }

        try {
            JsonNode node = json.readTree(raw);

            if (node instanceof ObjectNode object) {
                return object;
            }

            throw conflict(
                    "Stored " + description + " must be an object"
            );
        } catch (JsonProcessingException exception) {
            throw conflict(
                    "Stored " + description + " is invalid"
            );
        }
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                message
        );
    }
}