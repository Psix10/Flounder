package com.acme.sportplatform.events.web;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.acme.sportplatform.events.api.EventOperatorCandidateResponse;
import com.acme.sportplatform.events.api.EventOperatorMemberResponse;
import com.acme.sportplatform.events.application.EventOperatorDirectoryService;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;

@RestController
@RequestMapping("/api/v1/events/{eventId}/operators")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER')")
public class EventOperatorDirectoryController {
    private final EventOperatorDirectoryService directory;
    public EventOperatorDirectoryController(EventOperatorDirectoryService directory) { this.directory = directory; }

    @GetMapping("/members")
    public List<EventOperatorMemberResponse> members(@PathVariable UUID eventId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal, Authentication authentication) {
        return directory.members(eventId, actor(principal), admin(authentication));
    }

    @GetMapping("/candidates")
    public List<EventOperatorCandidateResponse> candidates(@PathVariable UUID eventId, @RequestParam String email,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal, Authentication authentication) {
        return directory.candidates(eventId, email, actor(principal), admin(authentication));
    }

    private UUID actor(AuthenticatedUserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) throw new AccessDeniedException("Authenticated user ID is required");
        return principal.getUserId();
    }
    private boolean admin(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated() && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_PLATFORM_ADMIN".equals(authority.getAuthority()));
    }
}
