package com.acme.sportplatform.registrations.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.events.EventLookup;
import com.acme.sportplatform.identity.ProfileLookup;
import com.acme.sportplatform.registrations.api.CreateRegistrationRequest;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

class RegistrationSeedTimeValidationTest {
    private final ObjectMapper json = new ObjectMapper();
    private final RegistrationRepository registrations = mock(RegistrationRepository.class);
    private final ProfileLookup profiles = mock(ProfileLookup.class);
    private final EventLookup events = mock(EventLookup.class);
    private final EventDisciplineLookup disciplines = mock(EventDisciplineLookup.class);
    private final RegistrationMapper mapper = mock(RegistrationMapper.class);
    private final CreateRegistrationUseCase service = new CreateRegistrationUseCase(
            registrations, profiles, events, disciplines, mapper, json);
    @Test void serviceRejectsInvalidSeedWithoutControllerValidation() throws Exception {
        for (String value : new String[]{"0", "-1", "28.111", "true", "\"28.11\""}) {
            var meta = json.readTree("{\"seedTimeSeconds\":" + value + "}");
            assertThrows(BusinessException.class, () -> service.execute(UUID.randomUUID(),
                    new CreateRegistrationRequest(UUID.randomUUID(), meta)));
        }
        verifyNoInteractions(registrations, profiles, events, disciplines, mapper);
    }
    @Test void serviceRejectsNonObjectMetadataWithoutDatabaseWork() throws Exception {
        var request = new CreateRegistrationRequest(UUID.randomUUID(), json.readTree("[]"));
        assertThrows(BusinessException.class, () -> service.execute(UUID.randomUUID(), request));
        verifyNoInteractions(registrations, profiles, events, disciplines, mapper);
    }
    @Test void ntPassesTheSeedGateAndContinuesToProfileLookup() {
        UUID participant = UUID.randomUUID();
        when(profiles.getByUserId(participant)).thenThrow(new IllegalArgumentException("Fixture profile missing"));
        assertThrows(BusinessException.class, () -> service.execute(participant,
                new CreateRegistrationRequest(UUID.randomUUID(), null)));
        verify(profiles).getByUserId(participant);
        verifyNoInteractions(registrations);
    }
}
