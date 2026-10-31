package com.acme.sportplatform.protocol.application;
import java.util.*;
import org.springframework.stereotype.Component;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.protocol.api.*;
import com.acme.sportplatform.protocol.domain.RuleBlockType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Validator;
@Component
public class ProtocolRuleSetValidator {
    private final Validator beanValidator;
    public ProtocolRuleSetValidator(Validator beanValidator) { this.beanValidator = beanValidator; }
    public List<RuleBlockRequest> validate(CreateProtocolRuleSetRequest request) {
        if (request == null || !beanValidator.validate(request).isEmpty()) {
            throw error("protocol.invalid_rule_set", "Invalid rule set request");
        }
        if (!"INDIVIDUAL".equals(request.competitionFormat()) || !"HEAT".equals(request.unitType())) {
            throw error("protocol.unsupported_rule_format", "Supported pipeline: INDIVIDUAL / HEAT");
        }
        List<RuleBlockRequest> blocks = request.blocks().stream()
                .sorted(Comparator.comparing(RuleBlockRequest::orderIndex)).toList();
        RuleBlockType[] expected = RuleBlockType.values();
        if (blocks.size() != expected.length) throw error(
                "protocol.invalid_rule_pipeline", "Exactly four stages are required");
        for (int i = 0; i < blocks.size(); i++) {
            RuleBlockRequest block = blocks.get(i);
            if (block.orderIndex() != i || !expected[i].name().equals(block.executorCode())) {
                throw error("protocol.invalid_rule_pipeline", "Invalid stage order or executor");
            }
            if (!block.config().isObject()) throw error(
                    "protocol.invalid_rule_config", "Config must be a JSON object");
        }
        keys(blocks.get(0).config(), Set.of());
        JsonNode seed = blocks.get(1).config();
        keys(seed, Set.of("metricKey")); text(seed, "metricKey", 100);
        JsonNode group = blocks.get(2).config();
        keys(group, Set.of("capacity", "unitLabelPrefix", "heatOrder", "partialHeat", "ntPlacement"));
        JsonNode n = group.path("capacity");
        if (!n.isIntegralNumber() || !n.canConvertToInt() || n.intValue() < 1 || n.intValue() > 1024) {
            throw error("protocol.invalid_rule_config", "Capacity must be an integer in 1..1024");
        }
        int capacity = n.intValue(); text(group, "unitLabelPrefix", 80);
        option(group, "heatOrder", Set.of("FASTEST_FIRST", "SLOWEST_FIRST"));
        option(group, "partialHeat", Set.of("FIRST", "LAST"));
        option(group, "ntPlacement", Set.of("WEAKEST"));
        JsonNode assignment = blocks.get(3).config();
        keys(assignment, Set.of("positionOrder", "allowedPositions"));
        List<Integer> allowed = assignment.has("allowedPositions")
                ? positions(assignment.get("allowedPositions"), capacity)
                : java.util.stream.IntStream.rangeClosed(1, capacity).boxed().toList();
        List<Integer> order = positions(assignment.path("positionOrder"), capacity);
        if (!new HashSet<>(allowed).equals(new HashSet<>(order))) throw error(
                "protocol.invalid_position_order", "positionOrder must be a permutation of allowedPositions");
        return blocks;
    }
    private List<Integer> positions(JsonNode node, int capacity) {
        if (!node.isArray() || node.size() != capacity) throw error(
                "protocol.invalid_position_order", "Positions must cover capacity exactly");
        List<Integer> values = new ArrayList<>(); Set<Integer> seen = new HashSet<>();
        for (JsonNode value : node) {
            if (!value.isIntegralNumber() || !value.canConvertToInt()
                    || value.intValue() < 0 || !seen.add(value.intValue())) {
                throw error("protocol.invalid_position_order", "Positions must be unique nonnegative integers");
            }
            values.add(value.intValue());
        }
        return values;
    }
    private void keys(JsonNode node, Set<String> allowed) {
        node.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) throw error(
                    "protocol.invalid_rule_config", "Unknown parameter: " + field);
        });
    }
    private void text(JsonNode node, String key, int limit) {
        JsonNode value = node.path(key);
        if (!value.isTextual() || value.textValue().isBlank() || value.textValue().length() > limit) {
            throw error("protocol.invalid_rule_config", "Invalid text parameter: " + key);
        }
    }
    private void option(JsonNode node, String key, Set<String> allowed) {
        if (node.has(key) && (!node.get(key).isTextual() || !allowed.contains(node.get(key).textValue()))) {
            throw error("protocol.invalid_rule_config", "Invalid option: " + key);
        }
    }
    private BusinessException error(String code, String message) { return new BusinessException(code, message); }
}
