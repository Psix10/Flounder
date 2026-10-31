package com.acme.sportplatform.protocol.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.competition.ProtocolDisciplineLookup;
import com.acme.sportplatform.protocol.*;
import com.acme.sportplatform.protocol.api.*;
import com.acme.sportplatform.protocol.infrastructure.jpa.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class GenerateStartProtocolUseCase {
    private final EventProtocolAccess events;
    private final RegistrationProtocolAccess registrations;
    private final CompetitionUnitProtocolAccess units;
    private final RuleExecutorRegistry registry;
    private final ObjectMapper json;
    private final ProtocolDisciplineLookup disciplines;
    private final PublishedProtocolRulesResolver resolver;
    private final StartProtocolSnapshotRepository snapshots;
    public GenerateStartProtocolUseCase(EventProtocolAccess events, RegistrationProtocolAccess registrations,
            CompetitionUnitProtocolAccess units, RuleExecutorRegistry registry, ObjectMapper json,
            ProtocolDisciplineLookup disciplines, PublishedProtocolRulesResolver resolver,
            StartProtocolSnapshotRepository snapshots) {
        this.events = events; this.registrations = registrations; this.units = units;
        this.registry = registry; this.json = json; this.disciplines = disciplines;
        this.resolver = resolver; this.snapshots = snapshots;
    }

    @Transactional
    public ProtocolGenerationResult execute(UUID disciplineId, GenerateStartProtocolRequest request) {
        var discipline = disciplines.lock(disciplineId);
        var published = events.getPublishedEventDiscipline(disciplineId);
        UUID selectedVersion = request == null ? null : request.ruleSetVersionId();
        UUID actorId = null;
        ResolvedProtocolRules rules;
        JsonNode legacySettings = null;
        if (selectedVersion != null) {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()
                    || !(authentication.getPrincipal() instanceof AuthenticatedUserPrincipal principal)
                    || principal.getUserId() == null) {
                throw new AccessDeniedException("Authenticated platform principal is required");
            }
            actorId = principal.getUserId();
            boolean admin = authentication.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_PLATFORM_ADMIN".equals(a.getAuthority()));
            boolean organizer = authentication.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ORGANIZER".equals(a.getAuthority()));
            if (!admin && !organizer) throw new AccessDeniedException("Organizer access is required");
            rules = resolver.resolve(selectedVersion, discipline, actorId, admin);
        } else {
            legacySettings = object(published.settingsJson(), "protocol.invalid_settings").path("protocol");
            rules = legacy(legacySettings);
        }
        if (units.hasCompetitionUnits(disciplineId)) {
            boolean regenerate = request != null && Boolean.TRUE.equals(request.regenerate());
            boolean allowed = selectedVersion != null
                    || legacySettings.path("allowRegenerate").asBoolean(false);
            if (!regenerate || !allowed) throw error("protocol.already_generated",
                    "Start protocol has already been generated for this discipline");
            throw error("protocol.regenerate_not_supported_yet", "Regeneration is not supported yet");
        }
        var confirmed = registrations.findConfirmedByEventDisciplineId(disciplineId);
        if (confirmed.isEmpty()) throw error("protocol.no_confirmed_registrations",
                "No confirmed registrations found for this discipline");
        List<SeedParticipant> participants = confirmed.stream().map(reg -> new SeedParticipant(
                reg.registrationId(), metric(reg.registrationMeta(), rules.metricKey()))).toList();
        Map<UUID, BigDecimal> metrics = new HashMap<>();
        for (SeedParticipant p : participants) {
            if (p.registrationId() == null || metrics.containsKey(p.registrationId())) throw error(
                    "protocol.invalid_participants", "Registration IDs must be present and unique");
            metrics.put(p.registrationId(), p.seedMetric());
        }
        var context = new ProtocolGenerationContext(disciplineId, rules.capacity(), rules.prefix(),
                rules.order(), rules.allowed(), rules.heatOrder(), rules.partialHeat(), participants);
        for (RuleBlockRequest block : rules.blocks()) registry.get(block.executorCode()).apply(context);
        validateOutput(context, metrics.keySet());
        List<CompetitionUnitProtocolAccess.CompetitionUnitDraftView> drafts = new ArrayList<>();
        for (int i = 0; i < context.getGroupedAssignments().size(); i++) {
            var assignments = context.getGroupedAssignments().get(i).stream().map(a ->
                    new CompetitionUnitProtocolAccess.AssignmentDraftView(a.registrationId(), a.laneOrPosition())).toList();
            drafts.add(new CompetitionUnitProtocolAccess.CompetitionUnitDraftView(
                    rules.prefix() + " " + (i + 1), i + 1, "DRAFT", assignments));
        }
        var saved = units.createDraftStartProtocol(disciplineId, drafts);
        var result = new ProtocolGenerationResult(disciplineId, saved.stream().map(u ->
                new GeneratedCompetitionUnit(u.competitionUnitId(), u.label(), u.sequenceNumber(),
                        u.assignments().stream().map(a -> new GeneratedAssignment(a.registrationId(),
                                a.laneOrPosition(), metrics.get(a.registrationId()))).toList())).toList());
        if (selectedVersion != null) {
            StartProtocolSnapshotEntity snapshot = new StartProtocolSnapshotEntity();
            snapshot.setId(UUID.randomUUID()); snapshot.setEventDisciplineId(disciplineId);
            snapshot.setRuleSetVersionId(selectedVersion);
            int revision = snapshots.findFirstByEventDisciplineIdOrderByRevisionNoDesc(disciplineId)
                    .map(s -> s.getRevisionNo() + 1).orElse(1);
            snapshot.setRevisionNo(revision); snapshot.setOperation("GENERATE");
            ObjectNode input = json.createObjectNode();
            input.put("schemaVersion", 1); input.put("ruleSetVersionId", selectedVersion.toString());
            input.set("rules", json.valueToTree(rules));
            input.set("confirmedRegistrations", json.valueToTree(confirmed));
            input.set("participants", json.valueToTree(participants));
            snapshot.setInput(input); snapshot.setProtocol(json.valueToTree(result));
            snapshot.setCreatedByUserId(actorId); snapshot.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            snapshots.saveAndFlush(snapshot);
        }
        return result;
    }

    private ResolvedProtocolRules legacy(JsonNode node) {
        JsonNode capacityNode = node.path("unitCapacity");
        if (!capacityNode.isIntegralNumber() || !capacityNode.canConvertToInt()
                || capacityNode.intValue() < 1 || capacityNode.intValue() > 1024) {
            throw error("protocol.invalid_settings", "unitCapacity must be an integer in 1..1024");
        }
        int capacity = capacityNode.intValue();
        String key = node.path("seedMetricKey").asText(null);
        String prefix = node.path("unitLabelPrefix").asText("Heat");
        if (key == null || key.isBlank() || prefix.isBlank() || prefix.length() > 80) {
            throw error("protocol.invalid_settings", "Invalid seedMetricKey or unitLabelPrefix");
        }
        List<Integer> allowed = IntStream.rangeClosed(1, capacity).boxed().toList();
        List<Integer> order = integers(node.path("positionOrder"));
        if (order.size() != capacity || new HashSet<>(order).size() != capacity
                || !new HashSet<>(order).equals(new HashSet<>(allowed))) throw error(
                        "protocol.invalid_position_order", "Invalid legacy positionOrder");
        List<RuleBlockRequest> blocks = List.of(
                new RuleBlockRequest(0, "ELIGIBILITY_CONFIRMED_ONLY", json.createObjectNode()),
                new RuleBlockRequest(1, "SEED_BY_METRIC_ASC_NULLS_LAST", json.createObjectNode()),
                new RuleBlockRequest(2, "GROUP_BY_CAPACITY", json.createObjectNode()),
                new RuleBlockRequest(3, "ASSIGN_CENTER_OUT", json.createObjectNode()));
        return new ResolvedProtocolRules(null, capacity, prefix, key, order, allowed,
                "FASTEST_FIRST", "LAST", blocks);
    }
    private List<Integer> integers(JsonNode array) {
        if (!array.isArray()) throw error("protocol.invalid_settings", "Positions must be an array");
        List<Integer> result = new ArrayList<>();
        for (JsonNode value : array) {
            if (!value.isIntegralNumber() || !value.canConvertToInt()) throw error(
                    "protocol.invalid_settings", "Positions must be integers");
            result.add(value.intValue());
        }
        return result;
    }
    private JsonNode object(String raw, String errorCode) {
        if (raw == null || raw.isBlank()) return json.createObjectNode();
        JsonNode node;
        try { node = json.readTree(raw); }
        catch (Exception ex) { throw error(errorCode, "Invalid JSON"); }
        if (node == null || !node.isObject()) throw error(errorCode, "Expected a JSON object");
        return node;
    }
    private BigDecimal metric(String raw, String key) {
        JsonNode value = object(raw, "protocol.invalid_seed_metric").path(key);
        if (value.isMissingNode() || value.isNull()) return null;
        if (!value.isNumber()) throw error("protocol.invalid_seed_metric", "Seed metric must be numeric");
        return value.decimalValue();
    }
    private void validateOutput(ProtocolGenerationContext context, Set<UUID> expected) {
        Set<UUID> actual = new HashSet<>(); Set<Integer> allowed = new HashSet<>(context.getAllowedPositions());
        for (var group : context.getGroupedAssignments()) {
            if (group.isEmpty() || group.size() > context.getUnitCapacity()) throw invalidOutput();
            Set<Integer> occupied = new HashSet<>();
            for (int i = 0; i < group.size(); i++) {
                var a = group.get(i);
                if (!expected.contains(a.registrationId()) || !actual.add(a.registrationId())
                        || !allowed.contains(a.laneOrPosition()) || !occupied.add(a.laneOrPosition())
                        || !Objects.equals(a.laneOrPosition(), context.getPositionOrder().get(i))) throw invalidOutput();
            }
        }
        if (!actual.equals(expected)) throw invalidOutput();
    }
    private BusinessException invalidOutput() {
        return error("protocol.invalid_generated_assignments", "Generated assignments violate invariants");
    }
    private BusinessException error(String code, String message) { return new BusinessException(code, message); }
}
