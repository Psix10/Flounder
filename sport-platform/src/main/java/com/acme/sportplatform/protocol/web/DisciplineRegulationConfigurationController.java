package com.acme.sportplatform.protocol.web;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.competition.ProtocolDisciplineLookup;
import com.acme.sportplatform.organizations.OrganizationManagementAccess;
import com.acme.sportplatform.events.EventRegulationLookup;
import com.acme.sportplatform.regulations.RegulationBundleAccess;
import com.acme.sportplatform.protocol.ProtocolConfigurationPublication;
@RestController
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER')")
public class DisciplineRegulationConfigurationController {
    private final ProtocolDisciplineLookup disciplines;
    private final OrganizationManagementAccess organizations;
    private final EventRegulationLookup events;
    private final RegulationBundleAccess documents;
    private final ProtocolConfigurationPublication protocols;
    public DisciplineRegulationConfigurationController(ProtocolDisciplineLookup disciplines,
            OrganizationManagementAccess organizations, EventRegulationLookup events,
            RegulationBundleAccess documents, ProtocolConfigurationPublication protocols) {
        this.disciplines=disciplines; this.organizations=organizations; this.events=events;
        this.documents=documents; this.protocols=protocols;
    }
    public record Response(UUID regulationVersionId, String name, int versionNo,
            UUID ruleSetVersionId, ProtocolConfigurationPublication.Settings settings) { }
    @GetMapping("/api/v1/event-disciplines/{id}/regulation-configuration") @Transactional
    public Response get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUserPrincipal p, Authentication a) {
        if (p == null || p.getUserId() == null) throw new AccessDeniedException("Authenticated principal is required");
        var d = disciplines.lock(id);
        boolean admin = a != null && a.getAuthorities().stream().anyMatch(v -> "ROLE_PLATFORM_ADMIN".equals(v.getAuthority()));
        if (!admin && !organizations.canManageOrganization(d.organizationId(), p.getUserId())) throw new AccessDeniedException("You cannot manage this event");
        var document = documents.read(events.versionId(d.eventId()));
        var binding = document.binding();
        return new Response(document.id(), document.name(), document.versionNo(),
                binding == null ? null : binding.ruleVersionId(),
                binding == null ? null : protocols.read(binding.ruleVersionId()));
    }
}
