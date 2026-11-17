package com.acme.sportplatform.protocol.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import com.acme.sportplatform.events.EventOperationAccess;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal;
import com.acme.sportplatform.protocol.SessionProtocolSnapshotReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class GetSessionProtocolUseCaseTest {

    private final UUID eventId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    private EventOperationAccess events;
    private SessionProtocolSnapshotReader snapshots;
    private GetSessionProtocolUseCase useCase;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        events = mock(EventOperationAccess.class);
        snapshots = mock(SessionProtocolSnapshotReader.class);

        useCase = new GetSessionProtocolUseCase(
                events,
                snapshots
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String role) {
        var principal = new PlatformUserPrincipal(
                actorId,
                "session-reader@example.com",
                "",
                "active",
                List.of(
                        new SimpleGrantedAuthority("ROLE_" + role)
                )
        );

        var context = SecurityContextHolder.createEmptyContext();

        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        "",
                        principal.getAuthorities()
                )
        );

        SecurityContextHolder.setContext(context);
    }

    private ObjectNode savedProtocol() {
        var result = new ObjectMapper().createObjectNode();

        result.put("snapshotId", UUID.randomUUID().toString());
        result.put("eventId", eventId.toString());
        result.put("revisionNo", 2);
        result.putArray("disciplines");
        result.putArray("schedule");

        return result;
    }

    @Test
    void adminReadsSavedProtocolWithoutOrganizationCheck() {
        authenticate("PLATFORM_ADMIN");

        var saved = savedProtocol();

        when(snapshots.findLatestProtocol(eventId))
                .thenReturn(Optional.of(saved));

        assertEquals(saved, useCase.execute(eventId));

        verify(snapshots).findLatestProtocol(eventId);
        verifyNoInteractions(events);
    }

    @Test
    void managingOrganizerReadsSavedProtocol() {
        authenticate("ORGANIZER");

        var saved = savedProtocol();

        when(events.canManageEvent(eventId, actorId))
                .thenReturn(true);

        when(snapshots.findLatestProtocol(eventId))
                .thenReturn(Optional.of(saved));

        assertEquals(saved, useCase.execute(eventId));

        verify(events).canManageEvent(eventId, actorId);
        verify(snapshots).findLatestProtocol(eventId);
    }

    @Test
    void unrelatedOrganizerCannotReadSnapshot() {
        authenticate("ORGANIZER");

        when(events.canManageEvent(eventId, actorId))
                .thenReturn(false);

        assertThrows(
                AccessDeniedException.class,
                () -> useCase.execute(eventId)
        );

        verify(events).canManageEvent(eventId, actorId);
        verifyNoInteractions(snapshots);
    }

    @Test
    void participantCannotReadSnapshot() {
        authenticate("PARTICIPANT");

        assertThrows(
                AccessDeniedException.class,
                () -> useCase.execute(eventId)
        );

        verifyNoInteractions(events, snapshots);
    }

    @Test
    void unauthenticatedInvocationCannotReadSnapshot() {
        assertThrows(
                AccessDeniedException.class,
                () -> useCase.execute(eventId)
        );

        verifyNoInteractions(events, snapshots);
    }

    @Test
    void missingSnapshotReturnsNotFound() {
        authenticate("PLATFORM_ADMIN");

        when(snapshots.findLatestProtocol(eventId))
                .thenReturn(Optional.empty());

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> useCase.execute(eventId)
        );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        verify(snapshots).findLatestProtocol(eventId);
        verifyNoInteractions(events);
    }
}