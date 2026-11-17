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

        useCase = new ReviewRegistrationUseCase(
                registrations,
                mapper,
                eventAccess
        );

        registration = new RegistrationEntity();
        registration.setId(registrationId);
        registration.setEventId(eventId);
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

        verify(eventAccess).canManageEvent(eventId, actorId);
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

    private void assertNotSaved() {
        verify(registrations, never())
                .save(any(RegistrationEntity.class));
    }
}