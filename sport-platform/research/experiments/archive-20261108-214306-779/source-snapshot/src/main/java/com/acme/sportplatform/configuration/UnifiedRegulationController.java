package com.acme.sportplatform.configuration;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.regulations.RegulationBundleAccess;
import com.acme.sportplatform.protocol.ProtocolConfigurationPublication;

@RestController
@RequestMapping("/api/v1/regulation-configurations")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER')")
public class UnifiedRegulationController {
    private final RegulationBundleAccess documents;
    private final ProtocolConfigurationPublication protocols;
    public UnifiedRegulationController(RegulationBundleAccess documents, ProtocolConfigurationPublication protocols) {
        this.documents = documents; this.protocols = protocols;
    }
    public record Request(UUID sourceVersionId, @NotNull UUID sportId,
            @NotBlank @Size(max=64) String code, @NotBlank @Size(max=255) String name,
            @Size(max=10000) String description, @Min(1) int versionNo, LocalDate effectiveFrom,
            @Size(max=10000) String notes,
            @NotNull @Size(max=256) List<@NotBlank @Size(max=2000) String> conditions,
            @Valid ProtocolConfigurationPublication.Settings protocol) { }
    public record Response(RegulationBundleAccess.Document document, ProtocolConfigurationPublication.Settings protocol) { }
    @PostMapping @Transactional
    public Response publish(@Valid @RequestBody Request r,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal, Authentication authentication) {
        UUID actor = actor(principal);
        boolean admin = admin(authentication);
        if (r.sourceVersionId() != null) {
            var source = documents.read(r.sourceVersionId());
            if (!admin && !actor.equals(source.createdBy())) throw new AccessDeniedException("Only the owner can create a new version");
            if (!source.sportId().equals(r.sportId())) throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Sport cannot change across versions");
        }
        UUID ruleId = r.protocol() == null ? null : protocols.publish(r.sportId(), r.name().trim(), r.protocol(), actor);
        var document = documents.publish(r.sourceVersionId(), r.sportId(), r.code(), r.name(), r.description(),
                r.versionNo(), r.effectiveFrom(), r.notes(), r.conditions(), ruleId, actor, admin);
        return new Response(document, r.protocol());
    }
    @GetMapping("/{id}") @Transactional(readOnly = true)
    public Response get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUserPrincipal principal, Authentication authentication) {
        var document = documents.read(id);
        if (!admin(authentication) && !actor(principal).equals(document.createdBy())) {
            throw new AccessDeniedException("Only the owner can open this version for revision");
        }
        return new Response(document, document.binding() == null ? null : protocols.read(document.binding().ruleVersionId()));
    }
    private UUID actor(AuthenticatedUserPrincipal p) {
        if (p == null || p.getUserId() == null) throw new AccessDeniedException("Authenticated principal is required");
        return p.getUserId();
    }
    private boolean admin(Authentication a) {
        return a != null && a.getAuthorities().stream().anyMatch(v -> "ROLE_PLATFORM_ADMIN".equals(v.getAuthority()));
    }
}
