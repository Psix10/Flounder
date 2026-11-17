package com.acme.sportplatform.registrations.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import com.acme.sportplatform.competition.ProtocolDisciplineLookup;
import com.acme.sportplatform.events.EventOperationAccess;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.registrations.RegistrationPaymentGuard;
import com.acme.sportplatform.registrations.RegistrationProtocolGuard;
import com.acme.sportplatform.registrations.api.ReturnRegistrationForCorrectionRequest;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationEntity;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;

class ReturnRegistrationForCorrectionUseCaseTest {

    private final UUID registrationId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final UUID disciplineId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID previousReviewerId = UUID.randomUUID();

    private final OffsetDateTime previousReviewedAt =
            OffsetDateTime.parse("2026-11-10T10:00:00Z");

    private final OffsetDateTime originalSubmittedAt =
            OffsetDateTime.parse("2026-11-09T10:00:00Z");

    private final String originalSnapshot = """
            {
              "firstName": "Test",
              "lastName": "Participant",
              "birthDate": "2003-03-03",
              "gender": null,
              "city": "Moscow",
              "sportMeta": {}
            }
            """;

    private RegistrationRepository registrations;
    private RegistrationMapper mapper;
    private EventOperationAccess eventAccess;
    private ProtocolDisciplineLookup disciplines;
    private RegistrationProtocolGuard protocolGuard;
    private RegistrationPaymentGuard paymentGuard;
    private EntityManager entityManager;
    private ObjectMapper json;

    private ReturnRegistrationForCorrectionUseCase useCase;
    private RegistrationEntity registration;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        registrations = mock(RegistrationRepository.class);
        mapper = mock(RegistrationMapper.class);
        eventAccess = mock(EventOperationAccess.class);
        disciplines = mock(
                ProtocolDisciplineLookup.class,
                RETURNS_DEEP_STUBS
        );
        protocolGuard = mock(RegistrationProtocolGuard.class);
        paymentGuard = mock(RegistrationPaymentGuard.class);
        entityManager = mock(EntityManager.class);
        json = new ObjectMapper();

        useCase = new ReturnRegistrationForCorrectionUseCase(
                registrations,
                mapper,
                eventAccess,
                disciplines,
                protocolGuard,
                paymentGuard,
                json,
                entityManager
        );

        registration = new RegistrationEntity();
        registration.setId(registrationId);
        registration.setEventId(eventId);
        registration.setEventDisciplineId(disciplineId);
        registration.setParticipantUserId(UUID.randomUUID());
        registration.setParticipantProfileId(UUID.randomUUID());
        registration.setStatus("CONFIRMED");
        registration.setParticipantSnapshot(originalSnapshot);
        registration.setRegistrationMeta("""
                {
                  "seedTimeSeconds": 64.44,
                  "emergencyContactName": "Contact",
                  "emergencyContactPhone": "79990000000",
                  "medicalCertificate": true,
                  "agreeToRules": true,
                  "customField": {
                    "keep": "unchanged"
                  },
                  "correctionHistory": [
                    {
                      "reply": "Previous reply"
                    }
                  ]
                }
                """);
        registration.setReviewNote("Previously confirmed");
        registration.setReviewedByUserId(previousReviewerId);
        registration.setReviewedAt(previousReviewedAt);
        registration.setSubmittedAt(originalSubmittedAt);
        registration.setCreatedAt(originalSubmittedAt);
        registration.setUpdatedAt(previousReviewedAt);

        when(registrations.findById(registrationId))
                .thenReturn(Optional.of(registration));

        when(registrations.findForUpdate(registrationId))
                .thenReturn(Optional.of(registration));

        when(registrations.saveAndFlush(registration))
                .thenReturn(registration);

        when(eventAccess.canManageEvent(eventId, actorId))
                .thenReturn(true);

        when(disciplines.lock(disciplineId).eventId())
                .thenReturn(eventId);

        authenticate("ROLE_ORGANIZER");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnConfirmedRegistrationAndPreserveParticipantData()
            throws Exception {
        String originalMeta = registration.getRegistrationMeta();

        useCase.execute(registrationId, request());

        assertEquals("NEEDS_CORRECTION", registration.getStatus());
        assertEquals(
                originalSnapshot,
                registration.getParticipantSnapshot()
        );
        assertEquals(
                originalSubmittedAt,
                registration.getSubmittedAt()
        );
        assertEquals(
                originalSubmittedAt,
                registration.getCreatedAt()
        );

        assertEquals(
                "Please verify participant data",
                registration.getReviewNote()
        );
        assertEquals(actorId, registration.getReviewedByUserId());
        assertNotNull(registration.getReviewedAt());
        assertNotNull(registration.getUpdatedAt());

        JsonNode before = json.readTree(originalMeta);
        JsonNode after = json.readTree(
                registration.getRegistrationMeta()
        );

        assertEquals(
                new BigDecimal("64.44"),
                after.path("seedTimeSeconds").decimalValue()
        );

        var fields = before.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();

            assertEquals(
                    before.get(field),
                    after.get(field),
                    "Existing metadata changed: " + field
            );
        }

        assertTrue(
                after.path("confirmedCorrection")
                        .path("reservePlace")
                        .asBoolean()
        );
        assertEquals(
                actorId.toString(),
                after.path("confirmedCorrection")
                        .path("returnedByUserId")
                        .asText()
        );

        JsonNode history = after.path("reviewReturnHistory");
        assertEquals(1, history.size());

        JsonNode item = history.get(0);
        assertEquals(
                "RETURN_CONFIRMED_FOR_CORRECTION",
                item.path("operation").asText()
        );
        assertEquals("CONFIRMED", item.path("fromStatus").asText());
        assertEquals(
                "NEEDS_CORRECTION",
                item.path("toStatus").asText()
        );
        assertEquals(
                "Previously confirmed",
                item.path("previousReviewNote").asText()
        );
        assertEquals(
                previousReviewerId.toString(),
                item.path("previousReviewedByUserId").asText()
        );
        assertEquals(
                previousReviewedAt.toString(),
                item.path("previousReviewedAt").asText()
        );

        verify(registrations).saveAndFlush(registration);
        verify(mapper).toResponse(registration);
    }

    @Test
    void shouldAppendToExistingReturnHistory() throws Exception {
        registration.setRegistrationMeta("""
                {
                  "seedTimeSeconds": 64.44,
                  "reviewReturnHistory": [
                    {
                      "operation": "PREVIOUS_OPERATION",
                      "reason": "Keep previous record"
                    }
                  ]
                }
                """);

        useCase.execute(registrationId, request());

        JsonNode history = json.readTree(
                registration.getRegistrationMeta()
        ).path("reviewReturnHistory");

        assertEquals(2, history.size());
        assertEquals(
                "PREVIOUS_OPERATION",
                history.get(0).path("operation").asText()
        );
        assertEquals(
                "RETURN_CONFIRMED_FOR_CORRECTION",
                history.get(1).path("operation").asText()
        );
    }

    @Test
    void shouldAllowPlatformAdminWithoutOrganizationManagementAccess() {
        authenticate("ROLE_PLATFORM_ADMIN");

        when(eventAccess.canManageEvent(eventId, actorId))
                .thenReturn(false);

        useCase.execute(registrationId, request());

        assertEquals("NEEDS_CORRECTION", registration.getStatus());
        verifyNoInteractions(eventAccess);
        verify(registrations).saveAndFlush(registration);
    }

    @Test
    void shouldRejectOrganizerOfAnotherEvent() {
        when(eventAccess.canManageEvent(eventId, actorId))
                .thenReturn(false);

        assertThrows(
                AccessDeniedException.class,
                () -> useCase.execute(registrationId, request())
        );

        verifyNoInteractions(protocolGuard, paymentGuard);
        assertNotSaved();
    }

    @Test
    void shouldRejectParticipant() {
        authenticate("ROLE_PARTICIPANT");

        assertThrows(
                AccessDeniedException.class,
                () -> useCase.execute(registrationId, request())
        );

        verifyNoInteractions(registrations);
    }

    @Test
    void shouldRejectMissingAuthentication() {
        SecurityContextHolder.clearContext();

        assertThrows(
                AccessDeniedException.class,
                () -> useCase.execute(registrationId, request())
        );

        verifyNoInteractions(registrations);
    }

    @Test
    void shouldRejectNonConfirmedRegistration() {
        registration.setStatus("SUBMITTED");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> useCase.execute(registrationId, request())
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("SUBMITTED", registration.getStatus());
        assertNotSaved();
    }

    @Test
    void shouldRejectRegistrationWithAnyPayment() {
        when(paymentGuard.hasAnyPayment(registrationId))
                .thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> useCase.execute(registrationId, request())
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("CONFIRMED", registration.getStatus());

        verify(protocolGuard, never())
                .requireNoProtocolsOrUnits(eventId, disciplineId);
        assertNotSaved();
    }

    @Test
    void shouldStopWhenProtocolGuardRejectsCorrection() {
        doThrow(new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Discipline already has competition units"
        )).when(protocolGuard)
                .requireNoProtocolsOrUnits(eventId, disciplineId);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> useCase.execute(registrationId, request())
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("CONFIRMED", registration.getStatus());
        assertNotSaved();
    }

    @Test
    void shouldRejectBlankReviewNote() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> useCase.execute(
                        registrationId,
                        new ReturnRegistrationForCorrectionRequest("   ")
                )
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );
        verifyNoInteractions(registrations);
    }

    @Test
    void shouldRejectInvalidStoredHistoryWithoutChangingStatus() {
        registration.setRegistrationMeta("""
                {
                  "seedTimeSeconds": 64.44,
                  "reviewReturnHistory": {}
                }
                """);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> useCase.execute(registrationId, request())
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("CONFIRMED", registration.getStatus());
        assertNotSaved();
    }

    @Test
    void shouldRequestLocksBeforeCheckingRelatedDataAndSaving() {
        useCase.execute(registrationId, request());

        var order = inOrder(
                protocolGuard,
                disciplines,
                registrations,
                entityManager,
                paymentGuard
        );

        order.verify(protocolGuard).lockEventBatch(eventId);
        order.verify(disciplines).lock(disciplineId);
        order.verify(registrations).findForUpdate(registrationId);
        order.verify(entityManager).refresh(registration);
        order.verify(paymentGuard).hasAnyPayment(registrationId);
        order.verify(protocolGuard)
                .requireNoProtocolsOrUnits(eventId, disciplineId);
        order.verify(registrations).saveAndFlush(registration);
    }

    private ReturnRegistrationForCorrectionRequest request() {
        return new ReturnRegistrationForCorrectionRequest(
                "  Please verify participant data  "
        );
    }

    private void authenticate(String authority) {
        AuthenticatedUserPrincipal principal =
                mock(AuthenticatedUserPrincipal.class);

        when(principal.getUserId()).thenReturn(actorId);

        var authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority(authority))
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);
    }

    private void assertNotSaved() {
        verify(registrations, never())
                .saveAndFlush(any(RegistrationEntity.class));
    }
}