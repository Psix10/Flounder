package com.acme.sportplatform.protocol.application;

import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.protocol.api.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

class ProtocolRuleSetValidatorTest {
    private final ObjectMapper json = new ObjectMapper();
    private ValidatorFactory factory;
    private ProtocolRuleSetValidator validator;

    @BeforeEach void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = new ProtocolRuleSetValidator(factory.getValidator());
    }
    @AfterEach void close() { factory.close(); }

    private CreateProtocolRuleSetRequest request(List<RuleBlockRequest> blocks) {
        return new CreateProtocolRuleSetRequest(UUID.randomUUID(), "test-rule", "Test rule",
                "INDIVIDUAL", "HEAT", blocks);
    }
    private List<RuleBlockRequest> validBlocks() throws Exception {
        return new ArrayList<>(List.of(
                new RuleBlockRequest(0, "ELIGIBILITY_CONFIRMED_ONLY", json.readTree("{}")),
                new RuleBlockRequest(1, "SEED_BY_METRIC_ASC_NULLS_LAST",
                        json.readTree("{\"metricKey\":\"seedTimeSeconds\"}")),
                new RuleBlockRequest(2, "GROUP_BY_CAPACITY",
                        json.readTree("{\"capacity\":2,\"unitLabelPrefix\":\"Heat\"}")),
                new RuleBlockRequest(3, "ASSIGN_CENTER_OUT",
                        json.readTree("{\"positionOrder\":[2,1]}"))
        ));
    }
    @Test void shouldAcceptValidBlocksAndSortByIndex() throws Exception {
        List<RuleBlockRequest> blocks = validBlocks();
        Collections.reverse(blocks);
        assertEquals(List.of(0, 1, 2, 3), validator.validate(request(blocks)).stream()
                .map(RuleBlockRequest::orderIndex).toList());
    }
    @Test void shouldRejectDuplicatePositions() throws Exception {
        List<RuleBlockRequest> blocks = validBlocks();
        blocks.set(3, new RuleBlockRequest(3, "ASSIGN_CENTER_OUT",
                json.readTree("{\"positionOrder\":[1,1]}")));
        assertThrows(BusinessException.class, () -> validator.validate(request(blocks)));
    }
    @Test void shouldRejectUnknownExecutor() throws Exception {
        List<RuleBlockRequest> blocks = validBlocks();
        blocks.set(0, new RuleBlockRequest(0, "UNKNOWN", json.createObjectNode()));
        assertThrows(BusinessException.class, () -> validator.validate(request(blocks)));
    }
    @Test void shouldRejectWrongStageOrder() throws Exception {
        List<RuleBlockRequest> blocks = validBlocks();
        RuleBlockRequest seed = blocks.get(1);
        blocks.set(1, new RuleBlockRequest(2, seed.executorCode(), seed.config()));
        assertThrows(BusinessException.class, () -> validator.validate(request(blocks)));
    }
    @Test void shouldRejectUnknownConfigParameter() throws Exception {
        List<RuleBlockRequest> blocks = validBlocks();
        ((ObjectNode) blocks.get(2).config()).put("capcity", 2);
        assertThrows(BusinessException.class, () -> validator.validate(request(blocks)));
    }
    @Test void shouldRejectArrayConfig() throws Exception {
        List<RuleBlockRequest> blocks = validBlocks();
        blocks.set(0, new RuleBlockRequest(0, "ELIGIBILITY_CONFIRMED_ONLY", json.createArrayNode()));
        assertThrows(BusinessException.class, () -> validator.validate(request(blocks)));
    }
}
