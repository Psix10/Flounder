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

    public ProtocolRuleSetValidator(Validator beanValidator) {
        this.beanValidator = beanValidator;
    }

    public List<RuleBlockRequest> validate(CreateProtocolRuleSetRequest request) {
        if (request == null || !beanValidator.validate(request).isEmpty()) {
            throw error("protocol.invalid_rule_set", "Invalid rule set request");
        }
        if (!"INDIVIDUAL".equals(request.competitionFormat())
                || !"HEAT".equals(request.unitType())) {
            throw error("protocol.unsupported_rule_format",
                    "The first rule pipeline supports INDIVIDUAL / HEAT only");
        }
        List<RuleBlockRequest> blocks = request.blocks().stream()
                .sorted(Comparator.comparing(RuleBlockRequest::orderIndex)).toList();
        RuleBlockType[] expected = RuleBlockType.values();
        if (blocks.size() != expected.length) {
            throw error("protocol.invalid_rule_pipeline", "Exactly four pipeline stages are required");
        }
        for (int i = 0; i < blocks.size(); i++) {
            RuleBlockRequest block = blocks.get(i);
            if (block.orderIndex() != i || !expected[i].name().equals(block.executorCode())) {
                throw error("protocol.invalid_rule_pipeline",
                        "Required stage order: eligibility, seeding, grouping, assignment; indexes start at zero");
            }
            if (!block.config().isObject()) {
                throw error("protocol.invalid_rule_config", "Block config must be a JSON object");
            }
        }
        requireKeys(blocks.get(0).config(), Set.of());
        JsonNode seed = blocks.get(1).config();
        requireKeys(seed, Set.of("metricKey"));
        requireText(seed, "metricKey", 100);
        JsonNode group = blocks.get(2).config();
        requireKeys(group, Set.of("capacity", "unitLabelPrefix"));
        JsonNode capacityNode = group.path("capacity");
        if (!capacityNode.isIntegralNumber() || !capacityNode.canConvertToInt()
                || capacityNode.intValue() < 1 || capacityNode.intValue() > 1024) {
            throw error("protocol.invalid_rule_config", "Capacity must be an integer between 1 and 1024");
        }
        requireText(group, "unitLabelPrefix", 80);
        JsonNode assignment = blocks.get(3).config();
        requireKeys(assignment, Set.of("positionOrder"));
        JsonNode order = assignment.path("positionOrder");
        int capacity = capacityNode.intValue();
        if (!order.isArray() || order.size() != capacity) {
            throw error("protocol.invalid_position_order", "Position order must cover capacity exactly");
        }
        Set<Integer> seen = new HashSet<>();
        for (JsonNode position : order) {
            if (!position.isIntegralNumber() || !position.canConvertToInt()
                    || position.intValue() < 1 || position.intValue() > capacity
                    || !seen.add(position.intValue())) {
                throw error("protocol.invalid_position_order",
                        "Position order must be a permutation of 1..capacity");
            }
        }
        return blocks;
    }

    private void requireKeys(JsonNode node, Set<String> allowed) {
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!allowed.contains(field)) {
                throw error("protocol.invalid_rule_config", "Unknown block parameter: " + field);
            }
        }
    }

    private void requireText(JsonNode node, String key, int limit) {
        JsonNode value = node.path(key);
        if (!value.isTextual() || value.textValue().isBlank() || value.textValue().length() > limit) {
            throw error("protocol.invalid_rule_config", key + " must be nonblank text, max " + limit);
        }
    }

    private BusinessException error(String code, String message) {
        return new BusinessException(code, message);
    }
}
