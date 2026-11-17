package com.acme.sportplatform.protocol.application;

import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.acme.sportplatform.events.EventOperationAccess;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.protocol.SessionProtocolSnapshotReader;
import com.fasterxml.jackson.databind.JsonNode;

@Service
@Transactional(readOnly = true)
public class GetSessionProtocolUseCase {

    private final EventOperationAccess events;
    private final SessionProtocolSnapshotReader snapshots;

    public GetSessionProtocolUseCase(
            EventOperationAccess events,
            SessionProtocolSnapshotReader snapshots
    ) {
        this.events = events;
        this.snapshots = snapshots;
    }

    public JsonNode execute(UUID eventId) {
        Objects.requireNonNull(eventId, "Event ID is required");

        var authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal()
                     instanceof AuthenticatedUserPrincipal principal)
                || principal.getUserId() == null) {
            throw new AccessDeniedException(
                    "Authenticated platform principal is required"
            );
        }

        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        "ROLE_PLATFORM_ADMIN".equals(
                                authority.getAuthority()
                        )
                );

        boolean organizer = authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        "ROLE_ORGANIZER".equals(
                                authority.getAuthority()
                        )
                );

        if (!admin && !organizer) {
            throw new AccessDeniedException(
                    "Organizer access is required"
            );
        }

        if (!admin && !events.canManageEvent(
                eventId,
                principal.getUserId()
        )) {
            throw new AccessDeniedException(
                    "You cannot read this event session protocol"
            );
        }

        return snapshots.findLatestProtocol(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Saved session protocol not found"
                ));
    }
}