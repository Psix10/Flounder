package com.acme.sportplatform.protocol.application;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.acme.sportplatform.protocol.application.strategy.*;
import com.acme.sportplatform.protocol.api.*;
import com.acme.sportplatform.common.exception.BusinessException;
import com.fasterxml.jackson.databind.*;
import jakarta.validation.Validation;
class ProtocolConfiguredGroupingTest {
    private ProtocolGenerationContext run(String order, String partial) {
        List<SeedParticipant> participants = new ArrayList<>();
        for (int i = 0; i < 4; i++) participants.add(new SeedParticipant(
                new UUID(0, i + 1), BigDecimal.valueOf(20 + i)));
        participants.add(new SeedParticipant(new UUID(0, 5), null));
        var context = new ProtocolGenerationContext(UUID.randomUUID(), 3, "Heat",
                List.of(4, 3, 5), List.of(3, 4, 5), order, partial, participants);
        new SeedByMetricAscNullsLastExecutor().apply(context);
        new GroupByCapacityExecutor().apply(context);
        new AssignCenterOutExecutor().apply(context);
        return context;
    }
    private List<Long> ids(ProtocolGenerationContext context, int group) {
        return context.getGroupedAssignments().get(group).stream()
                .map(a -> a.registrationId().getLeastSignificantBits()).toList();
    }
    @Test void shouldKeepLegacyFastestFirstPartialLast() {
        var c = run("FASTEST_FIRST", "LAST");
        assertEquals(List.of(1L, 2L, 3L), ids(c, 0));
        assertEquals(List.of(4L, 5L), ids(c, 1));
    }
    @Test void shouldPutSlowestAndNtInFirstPartialHeat() {
        var c = run("SLOWEST_FIRST", "FIRST");
        assertEquals(List.of(4L, 5L), ids(c, 0));
        assertEquals(List.of(1L, 2L, 3L), ids(c, 1));
        assertEquals(List.of(4, 3), c.getGroupedAssignments().get(0).stream()
                .map(GeneratedAssignment::laneOrPosition).toList());
    }
    @Test void shouldSupportFastestFirstPartialFirst() {
        var c = run("FASTEST_FIRST", "FIRST");
        assertEquals(List.of(1L, 2L), ids(c, 0));
        assertEquals(List.of(3L, 4L, 5L), ids(c, 1));
    }
    @Test void shouldSupportSlowestFirstPartialLast() {
        var c = run("SLOWEST_FIRST", "LAST");
        assertEquals(List.of(3L, 4L, 5L), ids(c, 0));
        assertEquals(List.of(1L, 2L), ids(c, 1));
    }
    private CreateProtocolRuleSetRequest request(String allowed) throws Exception {
        var json = new ObjectMapper();
        return new CreateProtocolRuleSetRequest(UUID.randomUUID(), "zero-lanes", "Zero lanes",
                "INDIVIDUAL", "HEAT", List.of(
                new RuleBlockRequest(0, "ELIGIBILITY_CONFIRMED_ONLY", json.readTree("{}")),
                new RuleBlockRequest(1, "SEED_BY_METRIC_ASC_NULLS_LAST",
                        json.readTree("{\"metricKey\":\"seedTimeSeconds\"}")),
                new RuleBlockRequest(2, "GROUP_BY_CAPACITY",
                        json.readTree("{\"capacity\":2,\"unitLabelPrefix\":\"Heat\",\"heatOrder\":\"SLOWEST_FIRST\",\"partialHeat\":\"FIRST\",\"ntPlacement\":\"WEAKEST\"}")),
                new RuleBlockRequest(3, "ASSIGN_CENTER_OUT", json.readTree(
                        "{\"positionOrder\":[0,1],\"allowedPositions\":" + allowed + "}"))));
    }
    @Test void shouldAcceptZeroBasedAllowedPositions() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertEquals(4, new ProtocolRuleSetValidator(factory.getValidator())
                    .validate(request("[0,1]")).size());
        }
    }
    @Test void shouldRejectPositionOutsideAllowedSet() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = new ProtocolRuleSetValidator(factory.getValidator());
            var request = request("[3,4]");
            assertThrows(BusinessException.class, () -> validator.validate(request));
        }
    }
}
