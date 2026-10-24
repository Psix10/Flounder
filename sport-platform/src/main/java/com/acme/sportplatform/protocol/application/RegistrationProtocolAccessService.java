package com.acme.sportplatform.protocol.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.protocol.RegistrationProtocolAccess;
import com.acme.sportplatform.registrations.RegistrationLookup;

@Service
@Transactional(readOnly = true)
public class RegistrationProtocolAccessService implements RegistrationProtocolAccess {

    private final RegistrationLookup registrationLookup;

    public RegistrationProtocolAccessService(RegistrationLookup registrationLookup) {
        this.registrationLookup = registrationLookup;
    }

    @Override
    public List<ConfirmedRegistrationView> findConfirmedByEventDisciplineId(UUID eventDisciplineId) {
        return registrationLookup.findByEventDisciplineId(eventDisciplineId).stream()
                .filter(registration -> "CONFIRMED".equals(registration.status()))
                .map(registration -> new ConfirmedRegistrationView(
                        registration.id(),
                        registration.registrationMeta()
                ))
                .toList();
    }
}