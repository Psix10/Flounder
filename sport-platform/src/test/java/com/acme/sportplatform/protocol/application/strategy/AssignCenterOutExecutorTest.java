package com.acme.sportplatform.protocol.application.strategy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.protocol.application.GeneratedAssignment;
import com.acme.sportplatform.protocol.application.ProtocolGenerationContext;

class AssignCenterOutExecutorTest {

    private static final List<Integer> STANDARD_ORDER =
            List.of(4, 5, 3, 6, 2, 7, 1, 8);

    private final AssignCenterOutExecutor executor =
            new AssignCenterOutExecutor();

    private GeneratedAssignment assignment(int id, String metric) {
        return new GeneratedAssignment(
                new UUID(0L, id),
                null,
                metric == null ? null : new BigDecimal(metric)
        );
    }

    private List<GeneratedAssignment> group(int firstId, int size) {
        List<GeneratedAssignment> result = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            result.add(assignment(
                    firstId + i,
                    Integer.toString(60 + i)
            ));
        }

        return result;
    }

    private ProtocolGenerationContext context(
            List<Integer> order,
            List<List<GeneratedAssignment>> groups
    ) {
        var context = mock(ProtocolGenerationContext.class);

        when(context.getPositionOrder()).thenReturn(order);
        when(context.getGroupedAssignments()).thenReturn(groups);

        return context;
    }

    private List<Integer> lanes(List<GeneratedAssignment> group) {
        return group.stream()
                .map(GeneratedAssignment::laneOrPosition)
                .toList();
    }

    @Test
    void exposesExpectedExecutorCode() {
        assertEquals("ASSIGN_CENTER_OUT", executor.code());
    }

    @Test
    void assignsAllEightLanesInConfiguredOrder() {
        List<List<GeneratedAssignment>> groups = new ArrayList<>();
        groups.add(group(1, 8));

        executor.apply(context(STANDARD_ORDER, groups));

        assertEquals(STANDARD_ORDER, lanes(groups.get(0)));
    }

    @Test
    void partialHeatUsesBeginningOfConfiguredOrder() {
        List<List<GeneratedAssignment>> groups = new ArrayList<>();
        groups.add(group(1, 3));

        executor.apply(context(STANDARD_ORDER, groups));

        assertEquals(List.of(4, 5, 3), lanes(groups.get(0)));
    }

    @Test
    void restartsLaneOrderForEveryHeat() {
        List<List<GeneratedAssignment>> groups = new ArrayList<>();
        groups.add(group(1, 8));
        groups.add(group(9, 2));

        executor.apply(context(STANDARD_ORDER, groups));

        assertEquals(STANDARD_ORDER, lanes(groups.get(0)));
        assertEquals(List.of(4, 5), lanes(groups.get(1)));
    }

    @Test
    void respectsCustomOrderInsteadOfCalculatingItsOwn() {
        List<Integer> customOrder = List.of(2, 4, 1, 3);

        List<List<GeneratedAssignment>> groups = new ArrayList<>();
        groups.add(group(1, 4));

        executor.apply(context(customOrder, groups));

        assertEquals(customOrder, lanes(groups.get(0)));
    }

    @Test
    void preservesParticipantOrderAndMetricsIncludingMissingTime() {
        var first = assignment(10, "60.25");
        var second = assignment(20, null);
        var third = assignment(30, "70.00");

        List<GeneratedAssignment> original =
                List.of(first, second, third);

        List<List<GeneratedAssignment>> groups = new ArrayList<>();
        groups.add(new ArrayList<>(original));

        executor.apply(context(STANDARD_ORDER, groups));

        var updated = groups.get(0);

        assertEquals(original.size(), updated.size());

        for (int i = 0; i < original.size(); i++) {
            assertEquals(
                    original.get(i).registrationId(),
                    updated.get(i).registrationId()
            );
            assertEquals(
                    original.get(i).seedMetric(),
                    updated.get(i).seedMetric()
            );
            assertEquals(
                    STANDARD_ORDER.get(i),
                    updated.get(i).laneOrPosition()
            );
        }

        assertNull(updated.get(1).seedMetric());
    }

    @Test
    void rejectsOrderTooShortForAHeat() {
        List<GeneratedAssignment> original = group(1, 3);

        List<List<GeneratedAssignment>> groups = new ArrayList<>();
        groups.add(original);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> executor.apply(
                        context(List.of(4, 5), groups)
                )
        );

        assertEquals(
                "Position order does not cover all participants in a unit",
                exception.getMessage()
        );

        assertSame(original, groups.get(0));

        assertTrue(
                groups.get(0).stream()
                        .allMatch(a -> a.laneOrPosition() == null)
        );
    }

    @Test
    void handlesNoHeats() {
        List<List<GeneratedAssignment>> groups = new ArrayList<>();

        executor.apply(context(STANDARD_ORDER, groups));

        assertTrue(groups.isEmpty());
    }

    @Test
    void repeatedExecutionKeepsSameAssignments() {
        List<List<GeneratedAssignment>> groups = new ArrayList<>();
        groups.add(group(1, 4));
        groups.add(group(5, 2));

        var context = context(STANDARD_ORDER, groups);

        executor.apply(context);

        List<List<GeneratedAssignment>> firstResult =
                groups.stream()
                        .map(List::copyOf)
                        .toList();

        executor.apply(context);

        for (int groupIndex = 0;
             groupIndex < groups.size();
             groupIndex++) {

            var before = firstResult.get(groupIndex);
            var after = groups.get(groupIndex);

            assertEquals(before.size(), after.size());

            for (int i = 0; i < before.size(); i++) {
                assertEquals(
                        before.get(i).registrationId(),
                        after.get(i).registrationId()
                );
                assertEquals(
                        before.get(i).laneOrPosition(),
                        after.get(i).laneOrPosition()
                );
                assertEquals(
                        before.get(i).seedMetric(),
                        after.get(i).seedMetric()
                );
            }
        }
    }

    @Test
    void doesNotModifyOriginalAssignmentObjects() {
        List<GeneratedAssignment> original = group(1, 3);

        List<List<GeneratedAssignment>> groups = new ArrayList<>();
        groups.add(original);

        executor.apply(context(STANDARD_ORDER, groups));

        assertNotSame(original, groups.get(0));

        assertTrue(
                original.stream()
                        .allMatch(a -> a.laneOrPosition() == null)
        );

        assertEquals(
                List.of(4, 5, 3),
                lanes(groups.get(0))
        );
    }
}