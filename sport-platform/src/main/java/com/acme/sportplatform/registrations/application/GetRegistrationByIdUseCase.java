package com.acme.sportplatform.registrations.application;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.registrations.api.RegistrationResponse;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationEntity;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository;

@Service
public class GetRegistrationByIdUseCase {

    private final RegistrationRepository registrationRepository;
    private final RegistrationMapper registrationMapper;
    private final RegistrationReadAccess readAccess;

    public GetRegistrationByIdUseCase(
            RegistrationRepository registrationRepository,
            RegistrationMapper registrationMapper,
            RegistrationReadAccess readAccess
    ) {
        this.registrationRepository = registrationRepository;
        this.registrationMapper = registrationMapper;
        this.readAccess = readAccess;
    }

    public RegistrationResponse execute(UUID registrationId) {
        var reader = readAccess.currentReader();
        RegistrationEntity registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new BusinessException(
                        "registrations.not_found",
                        "Registration not found: " + registrationId
                ));

        reader.requireEvent(registration.getEventId());
        return registrationMapper.toResponse(registration);
    }
}