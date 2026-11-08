package com.acme.sportplatform.protocol.application;

import java.util.List;
import java.util.ArrayList;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.acme.sportplatform.protocol.ProtocolConfigurationPublication;
import com.acme.sportplatform.protocol.api.*;
import com.acme.sportplatform.protocol.infrastructure.jpa.ProtocolRuleBlockRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ProtocolConfigurationPublicationService implements ProtocolConfigurationPublication {
    private final CreateProtocolRuleSetUseCase create;
    private final PublishProtocolRuleSetVersionUseCase publish;
    private final ProtocolRuleBlockRepository blocks;
    private final ObjectMapper json;
    public ProtocolConfigurationPublicationService(CreateProtocolRuleSetUseCase create,
            PublishProtocolRuleSetVersionUseCase publish, ProtocolRuleBlockRepository blocks, ObjectMapper json) {
        this.create = create; this.publish = publish; this.blocks = blocks; this.json = json;
    }
    @Override @Transactional
    public UUID publish(UUID sportId, String name, Settings s, UUID actorId) {
        var group = json.createObjectNode();
        group.put("capacity", s.capacity()); group.put("unitLabelPrefix", "Заход");
        group.put("heatOrder", s.heatOrder()); group.put("partialHeat", s.partialHeat());
        group.put("ntPlacement", "WEAKEST");
        if (s.categories() != null && !s.categories().isEmpty()) {
            group.set("categories", json.valueToTree(s.categories()));
        }
        ProtocolCategoryPolicy.parse(group);
        var positions = json.createObjectNode();
        positions.set("positionOrder", json.valueToTree(s.positionOrder()));
        positions.set("allowedPositions", json.valueToTree(s.positionOrder()));
        var draft = create.execute(new CreateProtocolRuleSetRequest(sportId,
                "reg-" + UUID.randomUUID(), name, "INDIVIDUAL", "HEAT", List.of(
                new RuleBlockRequest(0, "ELIGIBILITY_CONFIRMED_ONLY", json.createObjectNode()),
                new RuleBlockRequest(1, "SEED_BY_METRIC_ASC_NULLS_LAST", json.createObjectNode().put("metricKey", s.metricKey())),
                new RuleBlockRequest(2, "GROUP_BY_CAPACITY", group),
                new RuleBlockRequest(3, "ASSIGN_CENTER_OUT", positions))), actorId);
        return publish.execute(draft.id(), draft.ruleSetVersionId(), actorId, false).ruleSetVersionId();
    }
    @Override @Transactional(readOnly = true)
    public Settings read(UUID id) {
        var items = blocks.findByRuleSetVersionIdOrderByOrderIndexAsc(id);
        if (items.size() != 4) throw new IllegalStateException("Bound protocol rules are invalid");
        var seed = items.get(1).getConfig(); var group = items.get(2).getConfig();
        var order = items.get(3).getConfig().path("positionOrder");
        var values = new java.util.ArrayList<Integer>(); order.forEach(n -> values.add(n.intValue()));
        ProtocolCategoryPolicy.parse(group);
        List<JsonNode> categories = new ArrayList<>();
        if (group.has("categories")) {
            group.get("categories").forEach(category -> categories.add(category.deepCopy()));
        }
        return new Settings(group.path("capacity").intValue(), values, seed.path("metricKey").asText(),
                group.path("heatOrder").asText("FASTEST_FIRST"), group.path("partialHeat").asText("LAST"),
                List.copyOf(categories));
    }
}
