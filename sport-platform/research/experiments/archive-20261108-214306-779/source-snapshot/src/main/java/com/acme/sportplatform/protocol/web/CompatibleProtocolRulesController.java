package com.acme.sportplatform.protocol.web;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.acme.sportplatform.competition.ProtocolDisciplineLookup;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.organizations.OrganizationManagementAccess;
import com.acme.sportplatform.protocol.api.ProtocolRuleSetResponse;
import com.acme.sportplatform.protocol.application.ProtocolRuleSetMapper;
import com.acme.sportplatform.protocol.domain.CompetitionFormat;
import com.acme.sportplatform.protocol.domain.ProtocolRuleSetStatus;
import com.acme.sportplatform.protocol.domain.ProtocolUnitType;
import com.acme.sportplatform.protocol.infrastructure.jpa.ProtocolRuleBlockRepository;
import com.acme.sportplatform.protocol.infrastructure.jpa.ProtocolRuleSetEntity;
import com.acme.sportplatform.protocol.infrastructure.jpa.ProtocolRuleSetVersionEntity;

@RestController
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER')")
public class CompatibleProtocolRulesController {
    private final ProtocolDisciplineLookup disciplines;
    private final OrganizationManagementAccess organizations;
    private final ProtocolRuleBlockRepository blocks;
    private final ProtocolRuleSetMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    public CompatibleProtocolRulesController(
            ProtocolDisciplineLookup disciplines,
            OrganizationManagementAccess organizations,
            ProtocolRuleBlockRepository blocks,
            ProtocolRuleSetMapper mapper
    ) {
        this.disciplines = disciplines;
        this.organizations = organizations;
        this.blocks = blocks;
        this.mapper = mapper;
    }

    @GetMapping("/api/v1/event-disciplines/{disciplineId}/protocol-rule-versions")
    @Transactional
    public List<ProtocolRuleSetResponse> list(
            @PathVariable UUID disciplineId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            Authentication authentication
    ) {
        if (principal == null || principal.getUserId() == null) {
            throw new AccessDeniedException("Authenticated platform principal is required");
        }
        UUID actorId = principal.getUserId();
        boolean admin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_PLATFORM_ADMIN".equals(a.getAuthority()));
        var discipline = disciplines.lock(disciplineId);
        if (!admin && !organizations.canManageOrganization(discipline.organizationId(), actorId)) {
            throw new AccessDeniedException("You cannot manage this event");
        }
        if (!"INDIVIDUAL".equals(discipline.competitionFormat())
                || !"HEAT".equals(discipline.unitType())) {
            return List.of();
        }
        var query = entityManager.createQuery("""
                select v from ProtocolRuleSetVersionEntity v, ProtocolRuleSetEntity s
                where v.ruleSetId = s.id
                  and v.status = :status
                  and s.sportId = :sportId
                  and s.competitionFormat = :format
                  and s.unitType = :unitType
                  and (:admin = true or s.createdByUserId = :actorId)
                order by s.name, v.versionNo desc
                """, ProtocolRuleSetVersionEntity.class);
        var versions = query
                .setParameter("status", ProtocolRuleSetStatus.PUBLISHED)
                .setParameter("sportId", discipline.sportId())
                .setParameter("format", CompetitionFormat.INDIVIDUAL)
                .setParameter("unitType", ProtocolUnitType.HEAT)
                .setParameter("admin", admin)
                .setParameter("actorId", actorId)
                .getResultList();
        return versions.stream().map(version -> {
            var set = entityManager.find(ProtocolRuleSetEntity.class, version.getRuleSetId());
            return mapper.toResponse(set, version,
                    blocks.findByRuleSetVersionIdOrderByOrderIndexAsc(version.getId()));
        }).toList();
    }
}
