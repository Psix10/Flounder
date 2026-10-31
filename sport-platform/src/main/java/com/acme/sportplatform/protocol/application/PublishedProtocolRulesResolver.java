package com.acme.sportplatform.protocol.application;
import java.util.*;
import java.util.stream.IntStream;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.ProtocolDisciplineLookup;
import com.acme.sportplatform.organizations.OrganizationManagementAccess;
import com.acme.sportplatform.protocol.api.*;
import com.acme.sportplatform.protocol.domain.ProtocolRuleSetStatus;
import com.acme.sportplatform.protocol.infrastructure.jpa.*;
import com.fasterxml.jackson.databind.JsonNode;
@Service
public class PublishedProtocolRulesResolver {
    private final ProtocolRuleSetRepository sets;
    private final ProtocolRuleSetVersionRepository versions;
    private final ProtocolRuleBlockRepository blocks;
    private final ProtocolRuleSetValidator validator;
    private final OrganizationManagementAccess organizations;
    @PersistenceContext private EntityManager entityManager;
    public PublishedProtocolRulesResolver(ProtocolRuleSetRepository sets,
            ProtocolRuleSetVersionRepository versions, ProtocolRuleBlockRepository blocks,
            ProtocolRuleSetValidator validator, OrganizationManagementAccess organizations) {
        this.sets = sets; this.versions = versions; this.blocks = blocks;
        this.validator = validator; this.organizations = organizations;
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public ResolvedProtocolRules resolve(UUID versionId, ProtocolDisciplineLookup.View discipline,
            UUID actorId, boolean admin) {
        var version = versions.findById(versionId).orElseThrow(() -> error(
                "protocol.rule_version_not_found", "Rule version not found"));
        var set = sets.findLockedById(version.getRuleSetId()).orElseThrow(() -> error(
                "protocol.rule_set_not_found", "Rule set not found"));
        entityManager.refresh(version);
        if (!admin && (!actorId.equals(set.getCreatedByUserId())
                || !organizations.canManageOrganization(discipline.organizationId(), actorId))) {
            throw new AccessDeniedException("You cannot use these rules for this discipline");
        }
        if (version.getStatus() != ProtocolRuleSetStatus.PUBLISHED) throw error(
                "protocol.rule_version_not_published", "Select a published rule version");
        if (!set.getSportId().equals(discipline.sportId())
                || !set.getCompetitionFormat().name().equals(discipline.competitionFormat())
                || !set.getUnitType().name().equals(discipline.unitType())) {
            throw error("protocol.rule_version_incompatible", "Rule version is incompatible with discipline");
        }
        var requests = blocks.findByRuleSetVersionIdOrderByOrderIndexAsc(versionId).stream()
                .map(block -> new RuleBlockRequest(block.getOrderIndex(), block.getExecutorCode(),
                        block.getConfig().deepCopy())).toList();
        var validated = validator.validate(new CreateProtocolRuleSetRequest(set.getSportId(), set.getCode(),
                set.getName(), set.getCompetitionFormat().name(), set.getUnitType().name(), requests));
        JsonNode group = validated.get(2).config(), assignment = validated.get(3).config();
        int capacity = group.path("capacity").intValue();
        List<Integer> allowed = assignment.has("allowedPositions")
                ? integers(assignment.get("allowedPositions")) : IntStream.rangeClosed(1, capacity).boxed().toList();
        return new ResolvedProtocolRules(versionId, capacity, group.path("unitLabelPrefix").textValue(),
                validated.get(1).config().path("metricKey").textValue(),
                integers(assignment.path("positionOrder")), allowed,
                group.path("heatOrder").asText("FASTEST_FIRST"),
                group.path("partialHeat").asText("LAST"), validated);
    }
    private List<Integer> integers(JsonNode array) {
        List<Integer> values = new ArrayList<>(); array.forEach(value -> values.add(value.intValue()));
        return List.copyOf(values);
    }
    private BusinessException error(String code, String message) { return new BusinessException(code, message); }
}
