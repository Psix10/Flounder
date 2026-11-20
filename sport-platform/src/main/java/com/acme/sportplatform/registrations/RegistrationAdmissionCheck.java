package com.acme.sportplatform.registrations;

import java.util.UUID;

/**
 * Checks a candidate registration snapshot without changing registration data.
 * Call inside the workflow transaction, after authorization and any required
 * scope locks, before mutating the registration.
 */
public interface RegistrationAdmissionCheck {
    void verify(
            UUID eventDisciplineId,
            UUID registrationId,
            String participantSnapshot
    );
}