package com.acme.sportplatform.registrations;

import java.util.UUID;

public interface RegistrationProtocolGuard {

    /**
     * Must be called inside the correction transaction,
     * before locking the discipline and registration.
     */
    void lockEventBatch(UUID eventId);

    /**
     * Must be called after locking the event batch,
     * discipline and registration.
     */
    void requireNoProtocolsOrUnits(
            UUID eventId,
            UUID disciplineId
    );
}