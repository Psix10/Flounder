package com.acme.sportplatform.registrations.application;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.acme.sportplatform.events.EventOperationAccess;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;

@Service
public class RegistrationReadAccess {
    private final EventOperationAccess events;

    public RegistrationReadAccess(EventOperationAccess events) {
        this.events = events;
    }

    public Reader currentReader() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUserPrincipal principal)
                || principal.getUserId() == null) {
            throw denied();
        }

        var roles = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toUnmodifiableSet());

        if (!roles.contains("ROLE_PLATFORM_ADMIN")
                && !roles.contains("ROLE_ORGANIZER")
                && !roles.contains("ROLE_OPERATOR")) {
            throw denied();
        }

        return new Reader(principal.getUserId(), roles);
    }

    public final class Reader {
        private final UUID actorId;
        private final Set<String> roles;

        private Reader(UUID actorId, Set<String> roles) {
            this.actorId = actorId;
            this.roles = Set.copyOf(roles);
        }

        public boolean canRead(UUID eventId) {
            if (eventId == null) {
                return false;
            }
            return roles.contains("ROLE_PLATFORM_ADMIN")
                    || (roles.contains("ROLE_ORGANIZER")
                        && events.canManageEvent(eventId, actorId))
                    || (roles.contains("ROLE_OPERATOR")
                        && events.isAssignedActiveOperator(eventId, actorId));
        }

        public void requireEvent(UUID eventId) {
            if (!canRead(eventId)) {
                throw denied();
            }
        }
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException(
                "You cannot read registrations for this event");
    }
}