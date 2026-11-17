package com.acme.sportplatform.competition.web;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.acme.sportplatform.competition.api.OperatorDisciplineResponse;
import com.acme.sportplatform.competition.application.OperatorDisciplineQueryService;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;

@RestController
@RequestMapping("/api/v1/operator/events/{eventId}/disciplines")
@PreAuthorize("hasRole('OPERATOR')")
public class OperatorDisciplinesController {
    private final OperatorDisciplineQueryService service;
    public OperatorDisciplinesController(OperatorDisciplineQueryService service) { this.service = service; }
    @GetMapping
    public List<OperatorDisciplineResponse> list(@PathVariable UUID eventId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) throw new AccessDeniedException("Authenticated user ID is required");
        return service.list(eventId, principal.getUserId());
    }
}
