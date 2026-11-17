package com.acme.sportplatform.protocol.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.DisciplineSettingsPolicy;
import com.acme.sportplatform.protocol.RegistrationProtocolAccess.ConfirmedRegistrationView;
import com.acme.sportplatform.protocol.api.RuleBlockRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Narrows event rules for one discipline before any start-protocol persistence. */
public final class DisciplineProtocolAdmission {
    private DisciplineProtocolAdmission() {}
    public static JsonNode snapshotSettings(String raw) { return DisciplineSettingsPolicy.read(raw).deepCopy(); }
    public static ResolvedProtocolRules apply(String raw, ResolvedProtocolRules rules,
            List<ConfirmedRegistrationView> registrations, ObjectMapper json, java.util.UUID ruleVersionId) {
        var settings = DisciplineSettingsPolicy.read(raw);
        DisciplineSettingsPolicy.validate(settings);
        String gender = DisciplineSettingsPolicy.gender(settings);
        if (gender != null) {
            var restriction = json.createObjectNode();
            restriction.putArray("allowedGenders").add(gender);
            new ProtocolEligibilityVerifier(json).verify(registrations, ProtocolEligibilityPolicy.parse(restriction));
        }
        var allowedCodes = DisciplineSettingsPolicy.allowedCategoryCodes(settings);
        if (allowedCodes.isEmpty()) return rules;
        var grouping = rules.blocks().stream().filter(block -> "GROUP_BY_CAPACITY".equals(block.executorCode())).toList();
        if (grouping.size() != 1) throw error("protocol.invalid_rule_pipeline", "Exactly one grouping block is required");
        var original = grouping.get(0);
        var policy = ProtocolCategoryPolicy.parse(original.config());
        if (!policy.enabled()) throw error("protocol.discipline_categories_not_configured", "This discipline selects categories, but the bound regulation has no categories");
        JsonNode categories = original.config().get("categories");
        var known = new HashSet<String>();categories.forEach(item -> known.add(item.path("code").asText()));
        if (!known.containsAll(allowedCodes)) throw error("protocol.discipline_category_unknown", "Discipline refers to category codes absent from the bound regulation");
        var selected = new HashSet<>(allowedCodes);
        var config = original.config().deepCopy();
        if (!config.isObject()) throw error("protocol.invalid_rule_config", "Grouping configuration must be an object");
        var array = json.createArrayNode();
        for (JsonNode category : categories) if (selected.contains(category.path("code").asText())) array.add(category.deepCopy());
        ((com.fasterxml.jackson.databind.node.ObjectNode) config).set("categories", array);
        ProtocolCategoryPolicy.parse(config).classify(registrations, json);
        var blocks = new ArrayList<RuleBlockRequest>();
        for (var block : rules.blocks()) blocks.add(block == original
                ? new RuleBlockRequest(original.orderIndex(), original.executorCode(), config) : block);
        return new ResolvedProtocolRules(ruleVersionId, rules.capacity(), rules.prefix(), rules.metricKey(),
                rules.order(), rules.allowed(), rules.heatOrder(), rules.partialHeat(), List.copyOf(blocks));
    }
    private static BusinessException error(String code, String message) { return new BusinessException(code, message); }
}
