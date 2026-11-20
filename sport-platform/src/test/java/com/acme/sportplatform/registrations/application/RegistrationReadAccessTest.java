package com.acme.sportplatform.registrations.application;

import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.acme.sportplatform.events.EventOperationAccess;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegistrationReadAccessTest {
    private final UUID actor = UUID.randomUUID();
    private final UUID event = UUID.randomUUID();
    private final EventOperationAccess events = mock(EventOperationAccess.class);
    private final RegistrationReadAccess access = new RegistrationReadAccess(events);

    private void authenticate(String... roles) {
        var principal = mock(AuthenticatedUserPrincipal.class);
        when(principal.getUserId()).thenReturn(actor);

        var authorities = Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "", authorities));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingAuthenticationIsRejected() {
        SecurityContextHolder.clearContext();
        assertThrows(AccessDeniedException.class, access::currentReader);
        verifyNoInteractions(events);
    }

    @Test
    void wrongPrincipalTypeIsRejectedEvenWithAdminRole() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "not-a-platform-principal", "",
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))));

        assertThrows(AccessDeniedException.class, access::currentReader);
        verifyNoInteractions(events);
    }

    @Test
    void participantRoleIsRejected() {
        authenticate("ROLE_PARTICIPANT");
        assertThrows(AccessDeniedException.class, access::currentReader);
        verifyNoInteractions(events);
    }

    @Test
    void adminReadsWithoutEventMembershipChecks() {
        authenticate("ROLE_PLATFORM_ADMIN");
        access.currentReader().requireEvent(event);
        verifyNoInteractions(events);
    }

    @Test
    void organizerReadsManagedEvent() {
        authenticate("ROLE_ORGANIZER");
        when(events.canManageEvent(event, actor)).thenReturn(true);

        access.currentReader().requireEvent(event);

        verify(events).canManageEvent(event, actor);
        verify(events, never()).isAssignedActiveOperator(any(), any());
    }

    @Test
    void organizerCannotReadForeignEvent() {
        authenticate("ROLE_ORGANIZER");

        assertThrows(AccessDeniedException.class, () ->
                access.currentReader().requireEvent(event));

        verify(events).canManageEvent(event, actor);
        verify(events, never()).isAssignedActiveOperator(any(), any());
    }

    @Test
    void assignedOperatorReadsEvent() {
        authenticate("ROLE_OPERATOR");
        when(events.isAssignedActiveOperator(event, actor)).thenReturn(true);

        access.currentReader().requireEvent(event);

        verify(events).isAssignedActiveOperator(event, actor);
        verify(events, never()).canManageEvent(any(), any());
    }

    @Test
    void unassignedOperatorCannotReadEvent() {
        authenticate("ROLE_OPERATOR");

        assertThrows(AccessDeniedException.class, () ->
                access.currentReader().requireEvent(event));

        verify(events).isAssignedActiveOperator(event, actor);
    }

    @Test
    void operatorAssignmentIsRecheckedAfterRevocation() {
        authenticate("ROLE_OPERATOR");
        when(events.isAssignedActiveOperator(event, actor)).thenReturn(true, false);
        var reader = access.currentReader();

        reader.requireEvent(event);
        assertThrows(AccessDeniedException.class, () -> reader.requireEvent(event));

        verify(events, times(2)).isAssignedActiveOperator(event, actor);
    }

    @Test
    void multipleRolesAllowAnActualOperatorAssignment() {
        authenticate("ROLE_ORGANIZER", "ROLE_OPERATOR");
        when(events.canManageEvent(event, actor)).thenReturn(false);
        when(events.isAssignedActiveOperator(event, actor)).thenReturn(true);

        access.currentReader().requireEvent(event);

        verify(events).canManageEvent(event, actor);
        verify(events).isAssignedActiveOperator(event, actor);
    }

    @Test
    void nullEventScopeIsRejectedEvenForAdmin() {
        authenticate("ROLE_PLATFORM_ADMIN");

        assertThrows(AccessDeniedException.class, () ->
                access.currentReader().requireEvent(null));

        verifyNoInteractions(events);
    }
}