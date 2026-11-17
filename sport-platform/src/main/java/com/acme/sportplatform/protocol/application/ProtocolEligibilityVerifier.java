package com.acme.sportplatform.protocol.application;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.acme.sportplatform.protocol.RegistrationProtocolAccess.ConfirmedRegistrationView;
import com.acme.sportplatform.protocol.application.ProtocolEligibilityException.Violation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Validates captured registration snapshots without mutating them.
 * Gender values are compared case-insensitively for compatibility
 * with existing snapshots.
 */
public final class ProtocolEligibilityVerifier {

    private static final Set<String> KNOWN_GENDERS = Set.of(
            "MALE",
            "FEMALE"
    );

    private final ObjectMapper json;

    public ProtocolEligibilityVerifier(ObjectMapper json) {
        this.json = Objects.requireNonNull(json);
    }

    public void verify(
            List<ConfirmedRegistrationView> registrations,
            ProtocolEligibilityPolicy policy
    ) {
        if (!policy.enabled()) {
            return;
        }

        List<Violation> errors = new ArrayList<>();

        for (var registration : registrations) {
            JsonNode snapshot;

            try {
                snapshot = registration.participantSnapshot() == null
                        ? null
                        : json.readTree(registration.participantSnapshot());

                if (snapshot == null || !snapshot.isObject()) {
                    throw new IllegalArgumentException(
                            "Participant snapshot must be an object"
                    );
                }
            } catch (Exception exception) {
                errors.add(new Violation(
                        registration.registrationId(),
                        "participantSnapshot",
                        "INVALID_PARTICIPANT_SNAPSHOT",
                        "An object snapshot of the participant is required"
                ));
                continue;
            }

            if (policy.checksAge()) {
                checkAge(
                        registration.registrationId(),
                        snapshot,
                        policy,
                        errors
                );
            }

            if (!policy.allowedGenders().isEmpty()) {
                checkGender(
                        registration.registrationId(),
                        snapshot,
                        policy,
                        errors
                );
            }
        }

        if (!errors.isEmpty()) {
            throw new ProtocolEligibilityException(errors);
        }
    }

    private void checkGender(
            UUID registrationId,
            JsonNode snapshot,
            ProtocolEligibilityPolicy policy,
            List<Violation> errors
    ) {
        JsonNode gender = snapshot.path("gender");

        if (gender.isMissingNode() || gender.isNull()) {
            errors.add(new Violation(
                    registrationId,
                    "participantSnapshot.gender",
                    "MISSING_GENDER",
                    "Participant gender is required"
            ));
            return;
        }

        if (!gender.isTextual()) {
            addInvalidGender(registrationId, errors);
            return;
        }

        String normalizedGender = gender.textValue()
                .toUpperCase(Locale.ROOT);

        if (!KNOWN_GENDERS.contains(normalizedGender)) {
            addInvalidGender(registrationId, errors);
            return;
        }

        if (!policy.allowedGenders().contains(normalizedGender)) {
            errors.add(new Violation(
                    registrationId,
                    "participantSnapshot.gender",
                    "GENDER_NOT_ALLOWED",
                    "Participant gender is not allowed by selected rules"
            ));
        }
    }

    private void addInvalidGender(
            UUID registrationId,
            List<Violation> errors
    ) {
        errors.add(new Violation(
                registrationId,
                "participantSnapshot.gender",
                "INVALID_GENDER",
                "Expected a known MALE/FEMALE gender value"
        ));
    }

    private void checkAge(
            UUID registrationId,
            JsonNode snapshot,
            ProtocolEligibilityPolicy policy,
            List<Violation> errors
    ) {
        JsonNode node = snapshot.path("birthDate");
        LocalDate birth;

        if (node.isMissingNode() || node.isNull()) {
            errors.add(new Violation(
                    registrationId,
                    "participantSnapshot.birthDate",
                    "MISSING_BIRTH_DATE",
                    "Participant birth date is required"
            ));
            return;
        }

        try {
            if (!node.isTextual()) {
                throw new IllegalArgumentException(
                        "Birth date must be a string"
                );
            }

            birth = LocalDate.parse(node.textValue());
        } catch (IllegalArgumentException | DateTimeException exception) {
            errors.add(new Violation(
                    registrationId,
                    "participantSnapshot.birthDate",
                    "INVALID_BIRTH_DATE",
                    "Expected an ISO local date"
            ));
            return;
        }

        if (birth.isAfter(policy.referenceDate())) {
            errors.add(new Violation(
                    registrationId,
                    "participantSnapshot.birthDate",
                    "BIRTH_DATE_AFTER_REFERENCE_DATE",
                    "Birth date is after the configured reference date"
            ));
            return;
        }

        int age = policy.ageMethod()
                == ProtocolEligibilityPolicy.AgeMethod.BY_YEAR
                ? policy.referenceDate().getYear() - birth.getYear()
                : Period.between(birth, policy.referenceDate()).getYears();

        if ((policy.minAge() != null && age < policy.minAge())
                || (policy.maxAge() != null && age > policy.maxAge())) {
            errors.add(new Violation(
                    registrationId,
                    "participantSnapshot.birthDate",
                    "AGE_OUT_OF_RANGE",
                    "Computed age " + age
                            + " is outside the configured inclusive bounds"
            ));
        }
    }
}