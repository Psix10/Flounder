package com.acme.sportplatform.protocol.application.strategy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.protocol.application.GeneratedAssignment;
import com.acme.sportplatform.protocol.application.ProtocolGenerationContext;
import com.acme.sportplatform.protocol.application.SeedParticipant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class GroupByCapacityExecutorTest {

    private final SeedByMetricAscNullsLastExecutor seeding =
            new SeedByMetricAscNullsLastExecutor();

    private final GroupByCapacityExecutor grouping =
            new GroupByCapacityExecutor();

    private SeedParticipant participant(int id, String metric) {
        return new SeedParticipant(
                new UUID(0L, id),
                metric == null ? null : new BigDecimal(metric)
        );
    }

    private List<SeedParticipant> numberedParticipants(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(id -> participant(
                        id,
                        Integer.toString(50 + id)
                ))
                .toList();
    }

    private List<List<GeneratedAssignment>> generate(
            List<SeedParticipant> input,
            int capacity,
            String heatOrder,
            String partialHeat
    ) {
        ProtocolGenerationContext context =
                mock(ProtocolGenerationContext.class);

        AtomicReference<List<SeedParticipant>> current =
                new AtomicReference<>(new ArrayList<>(input));

        List<List<GeneratedAssignment>> groups =
                new ArrayList<>();

        when(context.getParticipants())
                .thenAnswer(invocation -> current.get());

        doAnswer(invocation -> {
            List<SeedParticipant> sorted =
                    invocation.getArgument(0);

            current.set(sorted);
            return null;
        }).when(context).setParticipants(anyList());

        when(context.getUnitCapacity()).thenReturn(capacity);
        when(context.getHeatOrder()).thenReturn(heatOrder);
        when(context.getPartialHeat()).thenReturn(partialHeat);
        when(context.getGroupedAssignments()).thenReturn(groups);

        seeding.apply(context);
        grouping.apply(context);

        return groups;
    }

    private List<List<Integer>> ids(
            List<List<GeneratedAssignment>> groups
    ) {
        return groups.stream()
                .map(group -> group.stream()
                        .map(assignment -> Math.toIntExact(
                                assignment.registrationId()
                                        .getLeastSignificantBits()
                        ))
                        .toList())
                .toList();
    }

    @Test
    void exposesExpectedExecutorCode() {
        assertEquals("GROUP_BY_CAPACITY", grouping.code());
    }

    @Test
    void fastestFirstPlacesPartialHeatFirst() {
        var result = generate(
                numberedParticipants(10),
                4,
                "FASTEST_FIRST",
                "FIRST"
        );

        assertEquals(
                List.of(
                        List.of(1, 2),
                        List.of(3, 4, 5, 6),
                        List.of(7, 8, 9, 10)
                ),
                ids(result)
        );
    }

    @Test
    void fastestFirstPlacesPartialHeatLast() {
        var result = generate(
                numberedParticipants(10),
                4,
                "FASTEST_FIRST",
                "LAST"
        );

        assertEquals(
                List.of(
                        List.of(1, 2, 3, 4),
                        List.of(5, 6, 7, 8),
                        List.of(9, 10)
                ),
                ids(result)
        );
    }

    @Test
    void slowestFirstPlacesPartialHeatFirst() {
        var result = generate(
                numberedParticipants(10),
                4,
                "SLOWEST_FIRST",
                "FIRST"
        );

        assertEquals(
                List.of(
                        List.of(9, 10),
                        List.of(5, 6, 7, 8),
                        List.of(1, 2, 3, 4)
                ),
                ids(result)
        );
    }

    @Test
    void slowestFirstPlacesPartialHeatLast() {
        var result = generate(
                numberedParticipants(10),
                4,
                "SLOWEST_FIRST",
                "LAST"
        );

        assertEquals(
                List.of(
                        List.of(7, 8, 9, 10),
                        List.of(3, 4, 5, 6),
                        List.of(1, 2)
                ),
                ids(result)
        );
    }

    @Test
    void exactMultipleProducesOnlyFullHeatsForEveryMode() {
        for (String order : List.of(
                "FASTEST_FIRST",
                "SLOWEST_FIRST"
        )) {
            for (String partial : List.of("FIRST", "LAST")) {
                var result = generate(
                        numberedParticipants(8),
                        4,
                        order,
                        partial
                );

                String mode = order + "/" + partial;

                assertEquals(2, result.size(), mode);

                assertEquals(
                        List.of(4, 4),
                        result.stream().map(List::size).toList(),
                        mode
                );

                var expected = order.equals("FASTEST_FIRST")
                        ? List.of(
                                List.of(1, 2, 3, 4),
                                List.of(5, 6, 7, 8)
                        )
                        : List.of(
                                List.of(5, 6, 7, 8),
                                List.of(1, 2, 3, 4)
                        );

                assertEquals(expected, ids(result), mode);
            }
        }
    }

    @Test
    void fewerParticipantsThanCapacityProduceOneHeat() {
        for (String order : List.of(
                "FASTEST_FIRST",
                "SLOWEST_FIRST"
        )) {
            for (String partial : List.of("FIRST", "LAST")) {
                var result = generate(
                        numberedParticipants(3),
                        8,
                        order,
                        partial
                );

                assertEquals(
                        List.of(List.of(1, 2, 3)),
                        ids(result),
                        order + "/" + partial
                );
            }
        }
    }

    @Test
    void emptyInputProducesNoHeats() {
        assertTrue(generate(
                List.of(),
                8,
                "SLOWEST_FIRST",
                "FIRST"
        ).isEmpty());
    }

    @Test
    void invalidCapacityIsRejected() {
        for (int capacity : List.of(0, -1)) {
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> generate(
                            numberedParticipants(3),
                            capacity,
                            "SLOWEST_FIRST",
                            "FIRST"
                    )
            );

            assertEquals(
                    "Invalid capacity",
                    exception.getMessage()
            );
        }
    }

    @Test
    void missingTimesOccupyWeakEndAcrossSeveralHeats() {
        List<SeedParticipant> input = new ArrayList<>();

        input.add(participant(1, "60"));
        input.add(participant(2, "61"));

        for (int id = 3; id <= 10; id++) {
            input.add(participant(id, null));
        }

        Collections.reverse(input);

        var fastestFirst = generate(
                input,
                4,
                "FASTEST_FIRST",
                "LAST"
        );

        assertEquals(
                List.of(
                        List.of(1, 2, 3, 4),
                        List.of(5, 6, 7, 8),
                        List.of(9, 10)
                ),
                ids(fastestFirst)
        );

        var slowestFirst = generate(
                input,
                4,
                "SLOWEST_FIRST",
                "FIRST"
        );

        assertEquals(
                List.of(
                        List.of(9, 10),
                        List.of(5, 6, 7, 8),
                        List.of(1, 2, 3, 4)
                ),
                ids(slowestFirst)
        );

        for (var result : List.of(fastestFirst, slowestFirst)) {
            for (var group : result) {
                for (var assignment : group) {
                    long id = assignment.registrationId()
                            .getLeastSignificantBits();

                    if (id >= 3) {
                        assertNull(assignment.seedMetric());
                    }
                }
            }
        }
    }

    @Test
    void assignsEveryParticipantOnceAndPreservesMetrics() {
        var input = new ArrayList<>(numberedParticipants(19));
        input.add(participant(20, null));
        Collections.reverse(input);

        for (String order : List.of(
                "FASTEST_FIRST",
                "SLOWEST_FIRST"
        )) {
            for (String partial : List.of("FIRST", "LAST")) {
                var result = generate(
                        input,
                        8,
                        order,
                        partial
                );

                var assignments = result.stream()
                        .flatMap(List::stream)
                        .toList();

                assertEquals(input.size(), assignments.size());

                assertEquals(
                        input.size(),
                        assignments.stream()
                                .map(GeneratedAssignment::registrationId)
                                .distinct()
                                .count()
                );

                assertEquals(
                        new java.util.HashSet<>(
                                input.stream()
                                        .map(SeedParticipant::registrationId)
                                        .toList()
                        ),
                        new java.util.HashSet<>(
                                assignments.stream()
                                        .map(GeneratedAssignment::registrationId)
                                        .toList()
                        )
                );

                for (var group : result) {
                    assertFalse(group.isEmpty());
                    assertTrue(group.size() <= 8);
                }

                for (var assignment : assignments) {
                    var source = input.stream()
                            .filter(p -> p.registrationId().equals(
                                    assignment.registrationId()
                            ))
                            .findFirst()
                            .orElseThrow();

                    if (source.seedMetric() == null) {
                        assertNull(assignment.seedMetric());
                    } else {
                        assertNotNull(assignment.seedMetric());

                        assertEquals(
                                0,
                                source.seedMetric().compareTo(
                                        assignment.seedMetric()
                                )
                        );
                    }

                    assertNull(
                            assignment.laneOrPosition(),
                            "Grouping must not assign lanes"
                    );
                }
            }
        }
    }

    @Test
    void repeatedGroupingReplacesPreviousGroups() {
        ProtocolGenerationContext context =
                mock(ProtocolGenerationContext.class);

        List<List<GeneratedAssignment>> groups =
                new ArrayList<>();

        when(context.getUnitCapacity()).thenReturn(4);
        when(context.getHeatOrder()).thenReturn("FASTEST_FIRST");
        when(context.getPartialHeat()).thenReturn("LAST");
        when(context.getGroupedAssignments()).thenReturn(groups);

        when(context.getParticipants())
                .thenReturn(numberedParticipants(8));

        grouping.apply(context);
        assertEquals(2, groups.size());

        when(context.getParticipants())
                .thenReturn(numberedParticipants(3));

        grouping.apply(context);

        assertEquals(
                List.of(List.of(1, 2, 3)),
                ids(groups)
        );
    }
}