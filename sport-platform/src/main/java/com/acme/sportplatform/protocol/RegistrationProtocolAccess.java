package com.acme.sportplatform.protocol;

import java.util.List;
import java.util.UUID;

public interface RegistrationProtocolAccess {

    List<ConfirmedRegistrationView> findConfirmedByEventDisciplineId(UUID eventDisciplineId);

    record ConfirmedRegistrationView(
            UUID registrationId,
            String registrationMeta
    ) {
    }
}