package com.acme.sportplatform.registrations;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface RegistrationWithdrawalPaymentAccess {
    /** Caller must hold the registration write lock in the same transaction. */
    void cancelUnpaidManualPaymentIfPresent(
            UUID registrationId, UUID actorId, String reason, OffsetDateTime now
    );
}
