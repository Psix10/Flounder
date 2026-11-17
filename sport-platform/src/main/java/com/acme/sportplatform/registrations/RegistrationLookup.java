package com.acme.sportplatform.registrations;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegistrationLookup {

    RegistrationLookupResult getById(UUID registrationId);

    /**
     * Locks the registration for an operation that changes related data.
     * Must be called inside the caller's read-write transaction.
     */
    RegistrationLookupResult getByIdForUpdate(UUID registrationId);

    Optional<RegistrationLookupResult> findById(UUID registrationId);

    List<RegistrationLookupResult> findByIdIn(
            Collection<UUID> registrationIds
    );

    List<RegistrationLookupResult> findByEventDisciplineId(
            UUID eventDisciplineId
    );
}