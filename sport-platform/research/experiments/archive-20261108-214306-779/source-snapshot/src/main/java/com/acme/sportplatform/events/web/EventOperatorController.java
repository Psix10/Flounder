package com.acme.sportplatform.events.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acme.sportplatform.events.api.EventOperatorAssignmentResponse;
import com.acme.sportplatform.events.application.EventOperatorService;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;

@RestController
@RequestMapping("/api/v1/events/{eventId}/operators")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER')")
public class EventOperatorController {

    private final EventOperatorService eventOperatorService;

    public EventOperatorController(
            EventOperatorService eventOperatorService
    ) {
        this.eventOperatorService = eventOperatorService;
    }

    @GetMapping
    public List<EventOperatorAssignmentResponse> list(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            Authentication authentication
    ) {
        return eventOperatorService.list(
                eventId,
                requireUserId(principal),
                isPlatformAdmin(authentication)
        );
    }

    @PutMapping("/{operatorUserId}")
    public ResponseEntity<Void> assign(
            @PathVariable UUID eventId,
            @PathVariable UUID operatorUserId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            Authentication authentication
    ) {
        eventOperatorService.assign(
                eventId,
                operatorUserId,
                requireUserId(principal),
                isPlatformAdmin(authentication)
        );

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{operatorUserId}")
    public ResponseEntity<Void> remove(
            @PathVariable UUID eventId,
            @PathVariable UUID operatorUserId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            Authentication authentication
    ) {
        eventOperatorService.remove(
                eventId,
                operatorUserId,
                requireUserId(principal),
                isPlatformAdmin(authentication)
        );

        return ResponseEntity.noContent().build();
    }

    private UUID requireUserId(
            AuthenticatedUserPrincipal principal
    ) {
        if (principal == null || principal.getUserId() == null) {
            throw new AccessDeniedException(
                    "Authenticated user principal is unavailable"
            );
        }

        return principal.getUserId();
    }

    private boolean isPlatformAdmin(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                        .anyMatch(authority ->
                                "ROLE_PLATFORM_ADMIN".equals(
                                        authority.getAuthority()
                                )
                        );
    }
}