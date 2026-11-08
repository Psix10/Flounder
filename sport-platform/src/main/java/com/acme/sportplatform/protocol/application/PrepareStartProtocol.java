package com.acme.sportplatform.protocol.application;

import java.math.BigDecimal;
import java.util.*;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.protocol.RegistrationProtocolAccess.ConfirmedRegistrationView;
import com.acme.sportplatform.protocol.api.RuleBlockRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Executes existing rule blocks and validates assignments, without persistence or authentication. */
public final class PrepareStartProtocol {
    private final RuleExecutorRegistry registry;
    private final ObjectMapper json;
    public PrepareStartProtocol(RuleExecutorRegistry registry, ObjectMapper json) {
        this.registry = Objects.requireNonNull(registry); this.json = Objects.requireNonNull(json);
    }
    public PreparedStartProtocol prepare(UUID disciplineId, ResolvedProtocolRules rules,
                                          List<ConfirmedRegistrationView> confirmed) {
        Objects.requireNonNull(disciplineId); Objects.requireNonNull(rules);
        confirmed = List.copyOf(confirmed);
        if (confirmed.isEmpty()) throw error("protocol.no_confirmed_registrations",
                "No confirmed registrations found; existing protocol has not been replaced");
        var eligibility=rules.blocks().stream().filter(block -> "ELIGIBILITY_CONFIRMED_ONLY".equals(block.executorCode())).toList();
        if(eligibility.size()>1) throw error("protocol.invalid_rule_pipeline", "Duplicate eligibility stage");
        if(!eligibility.isEmpty()) new ProtocolEligibilityVerifier(json).verify(confirmed,
                ProtocolEligibilityPolicy.parse(eligibility.get(0).config()));
        List<SeedParticipant> participants = confirmed.stream().map(reg -> new SeedParticipant(
                reg.registrationId(), metric(reg.registrationMeta(), rules.metricKey()))).toList();
        Map<UUID, BigDecimal> metrics = new HashMap<>();
        for (SeedParticipant participant : participants) {
            if (participant.registrationId() == null || metrics.containsKey(participant.registrationId())) {
                throw error("protocol.invalid_participants", "Registration IDs must be present and unique");
            }
            metrics.put(participant.registrationId(), participant.seedMetric());
        }
        var grouping = rules.blocks().stream().filter(b -> "GROUP_BY_CAPACITY".equals(b.executorCode())).toList();
        if (grouping.size() > 1) throw error("protocol.invalid_rule_pipeline", "Duplicate grouping stage");
        var categoryPolicy = ProtocolCategoryPolicy.parse(grouping.isEmpty() ? json.createObjectNode() : grouping.get(0).config());
        var membership = categoryPolicy.classify(confirmed, json);
        List<List<SeedParticipant>> batches = new ArrayList<>();
        if (categoryPolicy.enabled()) {
            for (String group : categoryPolicy.groupOrder()) {
                var batch = participants.stream().filter(p -> group.equals(membership.get(p.registrationId()).seedingGroupCode())).toList();
                if (!batch.isEmpty()) batches.add(batch);
            }
        } else batches.add(participants);
        List<GeneratedCompetitionUnit> units = new ArrayList<>();
        for (var batch : batches) {
            var context = new ProtocolGenerationContext(disciplineId, rules.capacity(), rules.prefix(),
                    rules.order(), rules.allowed(), rules.heatOrder(), rules.partialHeat(), batch);
            for (RuleBlockRequest block : rules.blocks()) registry.get(block.executorCode()).apply(context);
            validateOutput(context, batch.stream().map(SeedParticipant::registrationId).collect(java.util.stream.Collectors.toSet()));
            for (var group : context.getGroupedAssignments()) {
                var assignments = group.stream().map(a -> {
                    var category = membership.get(a.registrationId());
                    return new GeneratedAssignment(a.registrationId(), a.laneOrPosition(), metrics.get(a.registrationId()),
                            category == null ? null : category.categoryCode(), category == null ? null : category.seedingGroupCode());
                }).toList();
                int sequence = units.size() + 1;
                units.add(new GeneratedCompetitionUnit(UUID.randomUUID(), rules.prefix() + " " + sequence, sequence, assignments));
            }
        }
        return new PreparedStartProtocol(disciplineId, participants, units);
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
