package com.acme.sportplatform.registrations.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.acme.sportplatform.competition.ProtocolDisciplineLookup;
import com.acme.sportplatform.identity.ProfileLookup;
import com.acme.sportplatform.identity.ProfileLookupResult;
import com.acme.sportplatform.registrations.RegistrationPaymentGuard;
import com.acme.sportplatform.registrations.RegistrationProtocolGuard;
import com.acme.sportplatform.registrations.api.ResubmitRegistrationRequest;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationEntity;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.persistence.EntityManager;

class ResubmitRegistrationGenderTest {

    private final UUID registrationId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final UUID disciplineId = UUID.randomUUID();
    private final UUID participantId = UUID.randomUUID();
    private final UUID profileId = UUID.randomUUID();
    private final UUID reviewerId = UUID.randomUUID();

    private RegistrationRepository registrations;
    private RegistrationMapper mapper;
    private ProfileLookup profiles;
    private ProtocolDisciplineLookup disciplines;
    private RegistrationProtocolGuard protocolGuard;
    private RegistrationPaymentGuard paymentGuard;
    private EntityManager entityManager;
    private com.acme.sportplatform.registrations.RegistrationAdmissionCheck admissionCheck;
    private ObjectMapper json;

    private ResubmitRegistrationUseCase useCase;
    private RegistrationEntity registration;

    @BeforeEach
    void setUp() {
        registrations = mock(RegistrationRepository.class);
        mapper = mock(RegistrationMapper.class);
        profiles = mock(ProfileLookup.class);
        disciplines = mock(
                ProtocolDisciplineLookup.class,
                RETURNS_DEEP_STUBS
        );
        protocolGuard = mock(RegistrationProtocolGuard.class);
        paymentGuard = mock(RegistrationPaymentGuard.class);
        entityManager = mock(EntityManager.class);
        admissionCheck = mock(com.acme.sportplatform.registrations.RegistrationAdmissionCheck.class);
        json = new ObjectMapper();

        useCase = new ResubmitRegistrationUseCase(
                registrations,
                mapper,
                json,
                profiles,
                disciplines,
                protocolGuard,
                paymentGuard,
                entityManager,
                admissionCheck
        );

        registration = new RegistrationEntity();
        registration.setId(registrationId);
        registration.setEventId(eventId);
        registration.setEventDisciplineId(disciplineId);
        registration.setParticipantUserId(participantId);
        registration.setParticipantProfileId(profileId);
        registration.setStatus("NEEDS_CORRECTION");
        registration.setParticipantSnapshot("""
                {
                  "firstName": "SnapshotName",
                  "lastName": "SnapshotSurname",
                  "middleName": null,
                  "birthDate": "2003-03-03",
                  "gender": null,
                  "city": "SnapshotCity",
                  "countryCode": "RU",
                  "clubName": "SnapshotClub",
                  "sportMeta": {
                    "level": "KEEP"
                  },
                  "customSnapshotField": "KEEP"
                }
                """);
        registration.setRegistrationMeta("""
                {
                  "seedTimeSeconds": 64.44,
                  "emergencyContactName": "Old contact",
                  "emergencyContactPhone": "79990000000",
                  "medicalCertificate": false,
                  "agreeToRules": true,
                  "customMetaField": {
                    "value": "KEEP"
                  },
                  "confirmedCorrection": {
                    "reservePlace": true,
                    "returnedAt": "2026-11-14T10:00:00Z"
                  },
                  "reviewReturnHistory": [
                    {
                      "reason": "Please check gender"
                    }
                  ],
                  "correctionHistory": [
                    {
                      "reply": "Previous correction"
                    }
                  ]
                }
                """);
        registration.setReviewNote("Please check gender");
        registration.setReviewedByUserId(reviewerId);
        registration.setReviewedAt(
                OffsetDateTime.parse("2026-11-14T10:00:00Z")
        );
        registration.setCreatedAt(
                OffsetDateTime.parse("2026-11-10T10:00:00Z")
        );
        registration.setSubmittedAt(
                OffsetDateTime.parse("2026-11-10T10:00:00Z")
        );

        when(registrations.findById(registrationId))
                .thenReturn(Optional.of(registration));

        when(registrations.findForUpdate(registrationId))
                .thenReturn(Optional.of(registration));

        when(registrations.saveAndFlush(registration))
                .thenReturn(registration);

        when(disciplines.lock(disciplineId).eventId())
                .thenReturn(eventId);

        when(profiles.getByUserId(participantId))
                .thenReturn(profile(
                        profileId,
                        participantId,
                        "FEMALE"
                ));
    }

    @Test
    void shouldCopyOnlyGenderAndPreserveSeedTimeAndOtherData()
            throws Exception {
        ObjectNode originalSnapshot = (ObjectNode) json.readTree(
                registration.getParticipantSnapshot()
        );
        JsonNode originalMeta = json.readTree(
                registration.getRegistrationMeta()
        );
        OffsetDateTime createdAt = registration.getCreatedAt();

        useCase.execute(
                registrationId,
                participantId,
                request(true)
        );

        assertEquals("SUBMITTED", registration.getStatus());
        assertEquals(createdAt, registration.getCreatedAt());

        ObjectNode updatedSnapshot = (ObjectNode) json.readTree(
                registration.getParticipantSnapshot()
        );

        assertEquals(
                "FEMALE",
                updatedSnapshot.path("gender").asText()
        );

        ObjectNode expectedSnapshot = originalSnapshot.deepCopy();
        expectedSnapshot.put("gender", "FEMALE");

        assertEquals(expectedSnapshot, updatedSnapshot);

        JsonNode updatedMeta = json.readTree(
                registration.getRegistrationMeta()
        );

        assertEquals(
                new BigDecimal("64.44"),
                updatedMeta.path("seedTimeSeconds").decimalValue()
        );
        assertEquals(
                originalMeta.get("customMetaField"),
                updatedMeta.get("customMetaField")
        );
        assertEquals(
                originalMeta.get("confirmedCorrection"),
                updatedMeta.get("confirmedCorrection")
        );
        assertEquals(
                originalMeta.get("reviewReturnHistory"),
                updatedMeta.get("reviewReturnHistory")
        );

        assertEquals(
                "New contact",
                updatedMeta.path("emergencyContactName").asText()
        );
        assertEquals(
                "79991112233",
                updatedMeta.path("emergencyContactPhone").asText()
        );
        assertTrue(
                updatedMeta.path("medicalCertificate").asBoolean()
        );

        JsonNode history = updatedMeta.path("correctionHistory");
        assertEquals(2, history.size());
        assertEquals(
                originalMeta.path("correctionHistory").get(0),
                history.get(0)
        );

        JsonNode correction = history.get(1);
        assertEquals(
                "Profile checked",
                correction.path("reply").asText()
        );
        assertEquals(
                reviewerId.toString(),
                correction.path("reviewedByUserId").asText()
        );

        JsonNode change = correction.path("genderChange");
        assertTrue(change.path("oldValue").isNull());
        assertEquals(
                "FEMALE",
                change.path("newValue").asText()
        );
        assertEquals(
                profileId.toString(),
                change.path("sourceProfileId").asText()
        );
        assertEquals(
                participantId.toString(),
                change.path("confirmedByUserId").asText()
        );
        assertFalse(change.path("confirmedAt").asText().isBlank());

        verify(registrations).saveAndFlush(registration);
    }

    @Test
    void shouldRejectMissingConsentForReturnedRegistrationWithNullGender() {
        assertConflictWithoutSave(false);

        verifyNoInteractions(profiles);
    }

    @Test
    void shouldRejectDifferentProfileId() {
        when(profiles.getByUserId(participantId))
                .thenReturn(profile(
                        UUID.randomUUID(),
                        participantId,
                        "FEMALE"
                ));

        assertConflictWithoutSave(true);
    }

    @Test
    void shouldRejectProfileOfAnotherUser() {
        when(profiles.getByUserId(participantId))
                .thenReturn(profile(
                        profileId,
                        UUID.randomUUID(),
                        "FEMALE"
                ));

        assertConflictWithoutSave(true);
    }

    @Test
    void shouldRejectUnsupportedProfileGender() {
        when(profiles.getByUserId(participantId))
                .thenReturn(profile(
                        profileId,
                        participantId,
                        "female"
                ));

        assertConflictWithoutSave(true);
    }

    @Test
    void shouldNotReadProfileOrChangeSnapshotWithoutConsentWhenGenderIsValid()
            throws Exception {
        ObjectNode snapshot = (ObjectNode) json.readTree(
                registration.getParticipantSnapshot()
        );
        snapshot.put("gender", "MALE");

        String storedSnapshot = snapshot.toString();
        registration.setParticipantSnapshot(storedSnapshot);

        useCase.execute(
                registrationId,
                participantId,
                request(false)
        );

        assertEquals("SUBMITTED", registration.getStatus());
        assertEquals(
                storedSnapshot,
                registration.getParticipantSnapshot()
        );

        verifyNoInteractions(profiles);

        JsonNode history = json.readTree(
                registration.getRegistrationMeta()
        ).path("correctionHistory");

        assertFalse(history.get(1).has("genderChange"));
        verify(registrations).saveAndFlush(registration);
    }

    @Test
    void shouldRejectAnotherParticipant() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> useCase.execute(
                        registrationId,
                        UUID.randomUUID(),
                        request(true)
                )
        );

        assertEquals(
                HttpStatus.FORBIDDEN,
                exception.getStatusCode()
        );
        verifyNoInteractions(
                profiles,
                protocolGuard,
                paymentGuard
        );
        assertNotSaved();
    }

    @Test
    void shouldRejectExistingPaymentBeforeReadingProfile() {
        when(paymentGuard.hasAnyPayment(registrationId))
                .thenReturn(true);

        assertConflictWithoutSave(true);

        verifyNoInteractions(profiles);
        verify(protocolGuard, never())
                .requireNoProtocolsOrUnits(eventId, disciplineId);
    }

    @Test
    void shouldRejectProtocolGuardFailureBeforeReadingProfile() {
        doThrow(new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Discipline already has competition units"
        )).when(protocolGuard)
                .requireNoProtocolsOrUnits(eventId, disciplineId);

        assertConflictWithoutSave(true);

        verifyNoInteractions(profiles);
    }

    @Test
    void admissionChecksUpdatedGenderBeforeSaving() throws Exception {
        useCase.execute(registrationId, participantId, request(true));

        var candidate = org.mockito.ArgumentCaptor.forClass(String.class);
        var order = org.mockito.Mockito.inOrder(admissionCheck, registrations);
        order.verify(admissionCheck).verify(
                org.mockito.ArgumentMatchers.eq(disciplineId),
                org.mockito.ArgumentMatchers.eq(registrationId),
                candidate.capture());
        order.verify(registrations).saveAndFlush(registration);

        assertEquals("FEMALE", json.readTree(candidate.getValue()).path("gender").asText());
        assertEquals(candidate.getValue(), registration.getParticipantSnapshot());
    }

    @Test
    void admissionChecksStoredSnapshotWithoutRefreshingProfile() throws Exception {
        var snapshot = (ObjectNode) json.readTree(registration.getParticipantSnapshot());
        snapshot.put("gender", "MALE");
        registration.setParticipantSnapshot(snapshot.toString());
        String captured = registration.getParticipantSnapshot();

        useCase.execute(registrationId, participantId, request(false));

        verify(admissionCheck).verify(disciplineId, registrationId, captured);
        verifyNoInteractions(profiles);
        assertEquals(captured, registration.getParticipantSnapshot());
    }

    @Test
    void admissionFailurePreservesSnapshotMetadataAndStatus() {
        String snapshot = registration.getParticipantSnapshot();
        String meta = registration.getRegistrationMeta();
        var submittedAt = registration.getSubmittedAt();
        var updatedAt = registration.getUpdatedAt();

        var failure = new com.acme.sportplatform.common.exception.BusinessException(
                "protocol.eligibility_violation", "Synthetic admission failure");
        doThrow(failure).when(admissionCheck).verify(
                org.mockito.ArgumentMatchers.eq(disciplineId),
                org.mockito.ArgumentMatchers.eq(registrationId),
                org.mockito.ArgumentMatchers.anyString());

        var actual = assertThrows(
                com.acme.sportplatform.common.exception.BusinessException.class,
                () -> useCase.execute(registrationId, participantId, request(true)));

        org.junit.jupiter.api.Assertions.assertSame(failure, actual);
        assertEquals("NEEDS_CORRECTION", registration.getStatus());
        assertEquals(snapshot, registration.getParticipantSnapshot());
        assertEquals(meta, registration.getRegistrationMeta());
        assertEquals(submittedAt, registration.getSubmittedAt());
        assertEquals(updatedAt, registration.getUpdatedAt());
        assertNotSaved();
    }

    private ResubmitRegistrationRequest request(boolean consent) {
        return new ResubmitRegistrationRequest(
                "  Profile checked  ",
                "  New contact  ",
                "  79991112233  ",
                true,
                true,
                consent
        );
    }

    private ProfileLookupResult profile(
            UUID id,
            UUID userId,
            String gender
    ) {
        return new ProfileLookupResult(
                id,
                userId,
                "DifferentProfileName",
                "DifferentProfileSurname",
                "DifferentMiddleName",
                LocalDate.of(1999, 1, 1),
                gender,
                "DifferentProfileCity",
                "RU",
                "DifferentProfileClub",
                Map.of("level", "DO_NOT_COPY")
        );
    }

    private void assertConflictWithoutSave(boolean consent) {
        String originalSnapshot =
                registration.getParticipantSnapshot();
        String originalMeta =
                registration.getRegistrationMeta();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> useCase.execute(
                        registrationId,
                        participantId,
                        request(consent)
                )
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );
        assertEquals(
                "NEEDS_CORRECTION",
                registration.getStatus()
        );
        assertEquals(
                originalSnapshot,
                registration.getParticipantSnapshot()
        );
        assertEquals(
                originalMeta,
                registration.getRegistrationMeta()
        );

        assertNotSaved();
    }

    private void assertNotSaved() {
        verify(registrations, never())
                .saveAndFlush(any(RegistrationEntity.class));
    }

@Test void rejectsStoredNonPositiveSeedBeforeSavingCorrection() throws Exception {
    ObjectNode meta = (ObjectNode) json.readTree(registration.getRegistrationMeta());
    meta.put("seedTimeSeconds", -1);
    registration.setRegistrationMeta(meta.toString());
    var request = new ResubmitRegistrationRequest("Checked", "Contact", "79990000000", true, true, true);
    assertThrows(com.acme.sportplatform.common.exception.BusinessException.class,
            () -> useCase.execute(registrationId, participantId, request));
    assertEquals("NEEDS_CORRECTION", registration.getStatus());
    verify(registrations, never()).saveAndFlush(any());
}

}
