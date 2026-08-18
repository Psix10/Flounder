package com.acme.sportplatform.registrations.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.registrations.RegistrationLookup;
import com.acme.sportplatform.registrations.RegistrationLookupResult;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationEntity;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository;

@Service
@Transactional(readOnly = true)
public class RegistrationLookupService
        implements RegistrationLookup {

    private final RegistrationRepository registrationRepository;

    public RegistrationLookupService(
            RegistrationRepository registrationRepository
    ) {
        this.registrationRepository = registrationRepository;
    }

    @Override
    public RegistrationLookupResult getById(UUID registrationId) {
        RegistrationEntity registration = registrationRepository
                .findById(registrationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Registration not found: " + registrationId
                ));

        return new RegistrationLookupResult(
                registration.getId(),
                registration.getParticipantUserId(),
                registration.getEventDisciplineId(),
                registration.getStatus()
        );
    }
}