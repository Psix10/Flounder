package com.acme.sportplatform.protocol.web;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.protocol.api.*;
import com.acme.sportplatform.protocol.application.*;

@RestController
@RequestMapping("/api/v1/protocol-rule-sets")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER')")
public class ProtocolRuleSetController {
    private final CreateProtocolRuleSetUseCase createUseCase;
    private final GetProtocolRuleSetByIdUseCase getUseCase;
    private final PublishProtocolRuleSetVersionUseCase publishUseCase;

    public ProtocolRuleSetController(CreateProtocolRuleSetUseCase createUseCase,
            GetProtocolRuleSetByIdUseCase getUseCase,
            PublishProtocolRuleSetVersionUseCase publishUseCase) {
        this.createUseCase = createUseCase; this.getUseCase = getUseCase;
        this.publishUseCase = publishUseCase;
    }

    @PostMapping
    public ResponseEntity<ProtocolRuleSetResponse> create(
            @Valid @RequestBody CreateProtocolRuleSetRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return ResponseEntity.status(201).body(createUseCase.execute(request, userId(principal)));
    }

    @GetMapping("/{id}")
    public ProtocolRuleSetResponse get(@PathVariable UUID id,
            @RequestParam(required = false) UUID versionId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            Authentication authentication) {
        return getUseCase.execute(id, versionId, userId(principal), isAdmin(authentication));
    }

    @PostMapping("/{id}/versions/{versionId}/publish")
    public ProtocolRuleSetResponse publish(@PathVariable UUID id, @PathVariable UUID versionId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            Authentication authentication) {
        return publishUseCase.execute(id, versionId, userId(principal), isAdmin(authentication));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().anyMatch(authority ->
                        "ROLE_PLATFORM_ADMIN".equals(authority.getAuthority()));
    }

    private UUID userId(AuthenticatedUserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new AccessDeniedException("Authenticated user principal is unavailable");
        }
        return principal.getUserId();
    }
}
