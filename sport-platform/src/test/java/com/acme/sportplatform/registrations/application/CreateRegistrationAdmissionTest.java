package com.acme.sportplatform.registrations.application;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.events.EventLookup;
import com.acme.sportplatform.identity.ProfileLookup;
import com.acme.sportplatform.registrations.RegistrationAdmissionCheck;
import com.acme.sportplatform.registrations.api.CreateRegistrationRequest;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationEntity;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CreateRegistrationAdmissionTest {
    private final UUID participant = UUID.randomUUID();
    private final UUID profileId = UUID.randomUUID();
    private final UUID disciplineId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();

    private final RegistrationRepository registrations = mock(RegistrationRepository.class);
    private final ProfileLookup profiles = mock(ProfileLookup.class, RETURNS_DEEP_STUBS);
    private final EventLookup events = mock(EventLookup.class, RETURNS_DEEP_STUBS);
    private final EventDisciplineLookup disciplines =
            mock(EventDisciplineLookup.class, RETURNS_DEEP_STUBS);
    private final RegistrationMapper mapper = mock(RegistrationMapper.class);
    private final RegistrationAdmissionCheck admission = mock(RegistrationAdmissionCheck.class);
    private final ObjectMapper json = new ObjectMapper();

    private final CreateRegistrationUseCase service = new CreateRegistrationUseCase(
            registrations, profiles, events, disciplines, mapper, json, admission);

    @BeforeEach
    void setup() {
        var profile = profiles.getByUserId(participant);
        when(profile.id()).thenReturn(profileId);
        when(profile.userId()).thenReturn(participant);
        when(profile.firstName()).thenReturn("Captured");
        when(profile.lastName()).thenReturn("Participant");
        when(profile.birthDate()).thenReturn(LocalDate.of(2004, 5, 10));
        when(profile.gender()).thenReturn("MALE");
        when(profile.sportMeta()).thenReturn(Map.of());

        var discipline = disciplines.getPublishedById(disciplineId);
        when(discipline.id()).thenReturn(disciplineId);
        when(discipline.eventId()).thenReturn(eventId);
        when(discipline.participantLimit()).thenReturn(null);

        var event = events.getById(eventId);
        when(event.id()).thenReturn(eventId);
        when(event.status()).thenReturn("REGISTRATION_OPEN");

        when(registrations.save(any(RegistrationEntity.class)))
                .thenAnswer(call -> call.getArgument(0));

        clearInvocations(profiles);
    }

    @Test
    void checksExactlyTheIdAndSnapshotThatAreSaved() throws Exception {
        service.execute(participant, new CreateRegistrationRequest(disciplineId, null));

        var checkedId = ArgumentCaptor.forClass(UUID.class);
        var checkedSnapshot = ArgumentCaptor.forClass(String.class);
        var stored = ArgumentCaptor.forClass(RegistrationEntity.class);

        var order = inOrder(admission, registrations);
        order.verify(admission).verify(
                eq(disciplineId), checkedId.capture(), checkedSnapshot.capture());
        order.verify(registrations).save(stored.capture());

        var entity = stored.getValue();
        assertNotNull(checkedId.getValue());
        assertEquals(checkedId.getValue(), entity.getId());
        assertEquals(checkedSnapshot.getValue(), entity.getParticipantSnapshot());
        assertEquals("SUBMITTED", entity.getStatus());
        assertEquals(participant, entity.getParticipantUserId());
        assertEquals(profileId, entity.getParticipantProfileId());

        var snapshot = json.readTree(checkedSnapshot.getValue());
        assertEquals("2004-05-10", snapshot.path("birthDate").asText());
        assertEquals("MALE", snapshot.path("gender").asText());
        verify(profiles, times(1)).getByUserId(participant);
    }

    @Test
    void admissionFailurePreventsSaveAndResponseMapping() {
        var failure = new BusinessException(
                "protocol.eligibility_violation", "Synthetic admission failure");
        doThrow(failure).when(admission).verify(eq(disciplineId), any(UUID.class), anyString());

        assertSame(failure, assertThrows(BusinessException.class, () ->
                service.execute(participant,
                        new CreateRegistrationRequest(disciplineId, null))));

        verify(registrations, never()).save(any());
        verifyNoInteractions(mapper);
    }

    @Test
    void invalidSeedIsRejectedBeforeAdmission() throws Exception {
        assertThrows(BusinessException.class, () ->
                service.execute(participant, new CreateRegistrationRequest(
                        disciplineId, json.readTree("{\"seedTimeSeconds\":0}"))));

        verifyNoInteractions(admission);
        verify(registrations, never()).save(any());
        verifyNoInteractions(mapper);
    }
}