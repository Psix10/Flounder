package com.acme.sportplatform.registrations.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.events.EventOperationAccess;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.registrations.api.ReviewRegistrationRequest;
import com.acme.sportplatform.registrations.domain.RegistrationReviewDecision;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationEntity;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository;

class ReviewRegistrationAccessTest {

    private final UUID registrationId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID disciplineId = UUID.randomUUID();
    private com.acme.sportplatform.registrations.RegistrationProtocolGuard protocolGuard;
    private com.acme.sportplatform.competition.ProtocolDisciplineLookup disciplines;
    private jakarta.persistence.EntityManager entityManager;
    private com.acme.sportplatform.registrations.RegistrationAdmissionCheck admissionCheck;

    private RegistrationRepository registrations;
    private RegistrationMapper mapper;
    private EventOperationAccess eventAccess;
    private ReviewRegistrationRequest request;
    private ReviewRegistrationUseCase useCase;
    private RegistrationEntity registration;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        registrations = mock(RegistrationRepository.class);
        mapper = mock(RegistrationMapper.class);
        eventAccess = mock(EventOperationAccess.class);
        request = mock(ReviewRegistrationRequest.class);
        protocolGuard = mock(com.acme.sportplatform.registrations.RegistrationProtocolGuard.class);
        disciplines = mock(com.acme.sportplatform.competition.ProtocolDisciplineLookup.class);
        entityManager = mock(jakarta.persistence.EntityManager.class);
        admissionCheck = mock(com.acme.sportplatform.registrations.RegistrationAdmissionCheck.class);
        when(disciplines.lock(disciplineId)).thenReturn(
                new com.acme.sportplatform.competition.ProtocolDisciplineLookup.View(
                        disciplineId, UUID.randomUUID(), UUID.randomUUID(), "INDIVIDUAL", "HEAT", eventId));

        useCase = new ReviewRegistrationUseCase(
                registrations,
                mapper,
                eventAccess,
                protocolGuard,
                disciplines,
                entityManager,
                admissionCheck
        );

        registration = new RegistrationEntity();
        registration.setId(registrationId);
        registration.setEventId(eventId);
        registration.setEventDisciplineId(disciplineId);
        when(registrations.findById(registrationId)).thenReturn(Optional.of(registration));
        registration.setStatus("SUBMITTED");
        registration.setParticipantSnapshot(
                "{\"gender\":\"FEMALE\",\"city\":\"Moscow\"}"
        );
        registration.setRegistrationMeta(
                "{\"seedTimeSeconds\":64.44,\"keep\":\"unchanged\"}"
        );

        when(registrations.findForUpdate(registrationId))
                .thenReturn(Optional.of(registration));

        when(registrations.save(registration))
                .thenReturn(registration);

        when(eventAccess.canManageEvent(eventId, actorId))
                .thenReturn(true);

        when(request.decision())
                .thenReturn(RegistrationReviewDecision.CONFIRMED);

        when(request.reviewNote())
                .thenReturn("  Data checked  ");

        authenticate("ROLE_ORGANIZER");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAllowOrganizerOfEventAndPreserveParticipantData() {
        String snapshot = registration.getParticipantSnapshot();
        String meta = registration.getRegistrationMeta();

        useCase.execute(registrationId, actorId, request);

        assertEquals("CONFIRMED", registration.getStatus());
        assertEquals("Data checked", registration.getReviewNote());
        assertEquals(actorId, registration.getReviewedByUserId());
        assertNotNull(registration.getReviewedAt());
        assertNotNull(registration.getUpdatedAt());

        assertEquals(
                snapshot,
                registration.getParticipantSnapshot()
        );
        assertEquals(meta, registration.getRegistrationMeta());

        verify(eventAccess, org.mockito.Mockito.times(2)).canManageEvent(eventId, actorId);
        verify(registrations).save(registration);
    }

    @Test
    void shouldAllowPlatformAdminWithoutEventManagementPermission() {
        authenticate("ROLE_PLATFORM_ADMIN");

        when(eventAccess.canManageEvent(eventId, actorId))
                .thenReturn(false);

        useCase.execute(registrationId, actorId, request);

        assertEquals("CONFIRMED", registration.getStatus());
        verifyNoInteractions(eventAccess);
        verify(registrations).save(registration);
    }

    @Test
    void shouldRejectOrganizerOfAnotherEvent() {
        when(eventAccess.canManageEvent(eventId, actorId))
                .thenReturn(false);

        assertThrows(
                AccessDeniedException.class,
                () -> useCase.execute(
                        registrationId,
                        actorId,
                        request
                )
        );

        assertEquals("SUBMITTED", registration.getStatus());
        assertNotSaved();
    }

    @Test
    void shouldRejectParticipant() {
        authenticate("ROLE_PARTICIPANT");

        assertThrows(
                AccessDeniedException.class,
                () -> useCase.execute(
                        registrationId,
                        actorId,
                        request
                )
        );

        verifyNoInteractions(registrations, eventAccess);
    }

    @Test
    void shouldRejectDifferentReviewerId() {
        assertThrows(
                AccessDeniedException.class,
                () -> useCase.execute(
                        registrationId,
                        UUID.randomUUID(),
                        request
                )
        );

        verifyNoInteractions(registrations, eventAccess);
    }

    @Test
    void shouldRejectMissingAuthentication() {
        SecurityContextHolder.clearContext();

        assertThrows(
                AccessDeniedException.class,
                () -> useCase.execute(
                        registrationId,
                        actorId,
                        request
                )
        );

        verifyNoInteractions(registrations, eventAccess);
    }

    @Test
    void shouldRejectAlreadyConfirmedRegistration() {
        registration.setStatus("CONFIRMED");

        assertThrows(
                BusinessException.class,
                () -> useCase.execute(
                        registrationId,
                        actorId,
                        request
                )
        );

        assertNotSaved();
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


@Test void programGuardRejectsBeforeAnyRegistrationMutation() {
    String snapshot = registration.getParticipantSnapshot();
    String meta = registration.getRegistrationMeta();
    org.mockito.Mockito.doThrow(new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.CONFLICT, "Programme exists"))
            .when(protocolGuard).requireNoProtocolsOrUnits(eventId, disciplineId);
    var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> useCase.execute(registrationId, actorId, request));
    assertEquals(409, error.getStatusCode().value());
    assertEquals("SUBMITTED", registration.getStatus());
    assertEquals(snapshot, registration.getParticipantSnapshot());
    assertEquals(meta, registration.getRegistrationMeta());
    assertEquals(null, registration.getReviewedAt());
    assertEquals(null, registration.getReviewedByUserId());
    assertNotSaved();
}
@Test void platformAdminCannotBypassProgramGuard() {
    authenticate("ROLE_PLATFORM_ADMIN");
    org.mockito.Mockito.doThrow(new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.CONFLICT, "Programme exists"))
            .when(protocolGuard).requireNoProtocolsOrUnits(eventId, disciplineId);
    assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> useCase.execute(registrationId, actorId, request));
    assertEquals("SUBMITTED", registration.getStatus()); assertNotSaved();
}
@Test void locksEventThenDisciplineThenRegistrationAndRefreshesBeforeGuard() {
    useCase.execute(registrationId, actorId, request);
    var order = org.mockito.Mockito.inOrder(registrations, protocolGuard, disciplines, entityManager);
    order.verify(registrations).findById(registrationId);
    order.verify(protocolGuard).lockEventBatch(eventId);
    order.verify(disciplines).lock(disciplineId);
    order.verify(registrations).findForUpdate(registrationId);
    order.verify(entityManager).refresh(registration);
    order.verify(protocolGuard).requireNoProtocolsOrUnits(eventId, disciplineId);
    order.verify(registrations).save(registration);
}
@Test void refreshedStatusIsRecheckedAfterLockWait() {
    org.mockito.Mockito.doAnswer(call -> {
        registration.setStatus("CONFIRMED"); return null;
    }).when(entityManager).refresh(registration);
    assertThrows(BusinessException.class, () -> useCase.execute(registrationId, actorId, request));
    assertNotSaved();
    verify(protocolGuard, never()).requireNoProtocolsOrUnits(any(), any());
}
@Test void refreshedScopeCannotBeMovedToAnotherEvent() {
    org.mockito.Mockito.doAnswer(call -> {
        registration.setEventId(UUID.randomUUID()); return null;
    }).when(entityManager).refresh(registration);
    assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> useCase.execute(registrationId, actorId, request));
    assertNotSaved();
}
@Test void foreignOrganizerIsRejectedBeforeTakingProgramLocks() {
    when(eventAccess.canManageEvent(eventId, actorId)).thenReturn(false);
    assertThrows(AccessDeniedException.class, () -> useCase.execute(registrationId, actorId, request));
    verifyNoInteractions(protocolGuard, disciplines, entityManager);
    assertNotSaved();
}
@Test void permissionRevokedWhileWaitingCannotBeUsed() {
    when(eventAccess.canManageEvent(eventId, actorId)).thenReturn(true, false);
    assertThrows(AccessDeniedException.class, () -> useCase.execute(registrationId, actorId, request));
    assertNotSaved();
}
@Test void rejectingASubmittedRegistrationDoesNotChangeTheAdmittedPool() {
    when(request.decision()).thenReturn(RegistrationReviewDecision.REJECTED);
    useCase.execute(registrationId, actorId, request);
    assertEquals("REJECTED", registration.getStatus());
    verify(protocolGuard, never()).requireNoProtocolsOrUnits(any(), any());
}

    @Test
    void admissionFailureDoesNotMutateRegistration() {
        String snapshot = registration.getParticipantSnapshot();
        String meta = registration.getRegistrationMeta();
        var failure = new BusinessException(
                "protocol.eligibility_violation", "Synthetic admission failure");
        org.mockito.Mockito.doThrow(failure).when(admissionCheck)
                .verify(disciplineId, registrationId, snapshot);

        assertEquals(failure, assertThrows(BusinessException.class, () ->
                useCase.execute(registrationId, actorId, request)));

        assertEquals("SUBMITTED", registration.getStatus());
        assertEquals(snapshot, registration.getParticipantSnapshot());
        assertEquals(meta, registration.getRegistrationMeta());
        assertEquals(null, registration.getReviewedAt());
        assertEquals(null, registration.getReviewedByUserId());
        assertEquals(null, registration.getReviewNote());
        assertEquals(null, registration.getUpdatedAt());
        assertNotSaved();
    }

    @Test
    void platformAdminCannotBypassAdmission() {
        authenticate("ROLE_PLATFORM_ADMIN");
        org.mockito.Mockito.doThrow(new BusinessException(
                "protocol.eligibility_violation", "Synthetic admission failure"))
                .when(admissionCheck).verify(
                        disciplineId, registrationId,
                        registration.getParticipantSnapshot());

        assertThrows(BusinessException.class, () ->
                useCase.execute(registrationId, actorId, request));

        assertEquals("SUBMITTED", registration.getStatus());
        assertNotSaved();
    }

    @Test
    void rejectionAndCorrectionDoNotRequireAdmission() {
        for (var decision : List.of(
                RegistrationReviewDecision.REJECTED,
                RegistrationReviewDecision.NEEDS_CORRECTION)) {
            registration.setStatus("SUBMITTED");
            when(request.decision()).thenReturn(decision);
            useCase.execute(registrationId, actorId, request);
            assertEquals(decision.name(), registration.getStatus());
        }
        verifyNoInteractions(admissionCheck);
    }

    @Test
    void admissionRunsAfterProtocolGuardAndBeforeSave() {
        String snapshot = registration.getParticipantSnapshot();
        useCase.execute(registrationId, actorId, request);

        var order = org.mockito.Mockito.inOrder(
                protocolGuard, admissionCheck, registrations);
        order.verify(protocolGuard).requireNoProtocolsOrUnits(eventId, disciplineId);
        order.verify(admissionCheck).verify(disciplineId, registrationId, snapshot);
        order.verify(registrations).save(registration);
    }

    @Test
    void existingProgrammeStopsBeforeAdmissionCheck() {
        org.mockito.Mockito.doThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, "Programme exists"))
                .when(protocolGuard).requireNoProtocolsOrUnits(eventId, disciplineId);

        assertThrows(org.springframework.web.server.ResponseStatusException.class, () ->
                useCase.execute(registrationId, actorId, request));

        verifyNoInteractions(admissionCheck);
        assertNotSaved();
    }

    private void assertNotSaved() {
        verify(registrations, never())
                .save(any(RegistrationEntity.class));
    }
}