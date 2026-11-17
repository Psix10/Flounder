package com.acme.sportplatform.events.web;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.acme.sportplatform.events.api.OperatorEventResponse;
import com.acme.sportplatform.events.application.OperatorEventsService;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;

@RestController
@RequestMapping("/api/v1/operator/events")
@PreAuthorize("hasRole('OPERATOR')")
public class OperatorEventsController {
    private final OperatorEventsService service;
    public OperatorEventsController(OperatorEventsService service) { this.service = service; }
    @GetMapping
    public List<OperatorEventResponse> list(@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return service.list(actor(principal));
    }
    @GetMapping("/{eventId}")
    public OperatorEventResponse get(@PathVariable UUID eventId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return service.get(eventId, actor(principal));
    }
    private UUID actor(AuthenticatedUserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) throw new AccessDeniedException("Authenticated user ID is required");
        return principal.getUserId();
    }
}
