package com.acme.sportplatform.protocol.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.protocol.RegistrationProtocolAccess.ConfirmedRegistrationView;
import com.acme.sportplatform.protocol.api.RuleBlockRequest;
import com.acme.sportplatform.protocol.application.strategy.AssignCenterOutExecutor;
import com.acme.sportplatform.protocol.application.strategy.GroupByCapacityExecutor;
import com.acme.sportplatform.protocol.application.strategy.SeedByMetricAscNullsLastExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;

class PrepareStartProtocolPipelineTest {

    private static final List<Integer> POSITIONS =
            List.of(4, 5, 3, 6);

    private final ObjectMapper json = new ObjectMapper();

    private final RuleBlockExecutor eligibilityStage =
            new RuleBlockExecutor() {
                @Override
                public String code() {
                    return "ELIGIBILITY_CONFIRMED_ONLY";
                }

                @Override
                public void apply(ProtocolGenerationContext context) {
                    // PrepareStartProtocol verifies eligibility before blocks.
                }
            };

    private final RuleExecutorRegistry registry =
            new RuleExecutorRegistry(List.of(
                    eligibilityStage,
                    new SeedByMetricAscNullsLastExecutor(),
                    new GroupByCapacityExecutor(),
                    new AssignCenterOutExecutor()
            ));

    private final PrepareStartProtocol subject =
            new PrepareStartProtocol(registry, json);

    private ConfirmedRegistrationView registration(
            int id,
            String gender,
            String birthDate,
            String metric
    ) throws Exception {
        var registration = mock(ConfirmedRegistrationView.class);

        when(registration.registrationId())
                .thenReturn(new UUID(0L, id));

        var snapshot = json.createObjectNode();
        snapshot.put("gender", gender);
        snapshot.put("birthDate", birthDate);

        when(registration.participantSnapshot())
                .thenReturn(snapshot.toString());

        String meta = metric == null
                ? "{}"
                : "{\"seedTimeSeconds\":" + metric + "}";

        when(registration.registrationMeta()).thenReturn(meta);

        return registration;
    }

    private ResolvedProtocolRules rules(
            String eligibility,
            String grouping
    ) throws Exception {
        var blocks = List.of(
                new RuleBlockRequest(
                        0,
                        "ELIGIBILITY_CONFIRMED_ONLY",
                        json.readTree(eligibility)
                ),
                new RuleBlockRequest(
                        1,
                        "SEED_BY_METRIC_ASC_NULLS_LAST",
                        json.readTree(
                                "{\"metricKey\":\"seedTimeSeconds\"}"
                        )
                ),
                new RuleBlockRequest(
                        2,
                        "GROUP_BY_CAPACITY",
                        json.readTree(grouping)
                ),
                new RuleBlockRequest(
                        3,
                        "ASSIGN_CENTER_OUT",
                        json.readTree("""
                                {
                                  "allowedPositions":[4,5,3,6],
                                  "positionOrder":[4,5,3,6]
                                }
                                """)
                )
        );

        return new ResolvedProtocolRules(
                UUID.randomUUID(),
                4,
                "Заход",
                "seedTimeSeconds",
                POSITIONS,
                POSITIONS,
                "SLOWEST_FIRST",
                "FIRST",
                blocks
        );
    }

    private String separateCategories() {
        return """
                {
                  "capacity":4,
                  "unitLabelPrefix":"Заход",
                  "heatOrder":"SLOWEST_FIRST",
                  "partialHeat":"FIRST",
                  "categories":[
                    {
                      "code":"MEN",
                      "seedingGroupCode":"MEN",
                      "eligibility":{
                        "allowedGenders":["MALE"]
                      }
                    },
                    {
                      "code":"WOMEN",
                      "seedingGroupCode":"WOMEN",
                      "eligibility":{
                        "allowedGenders":["FEMALE"]
                      }
                    }
                  ]
                }
                """;
    }

    private List<Long> ids(GeneratedCompetitionUnit unit) {
        return unit.assignments().stream()
                .map(a -> a.registrationId().getLeastSignificantBits())
                .toList();
    }

    private void assertLanes(GeneratedCompetitionUnit unit) {
        assertEquals(
                POSITIONS.subList(0, unit.assignments().size()),
                unit.assignments().stream()
                        .map(GeneratedAssignment::laneOrPosition)
                        .toList()
        );
    }

    @Test
    void separatesMenAndWomenAndPreservesCategoriesAfterLaneAssignment()
            throws Exception {
        var prepared = subject.prepare(
                UUID.randomUUID(),
                rules("{}", separateCategories()),
                List.of(
                        registration(4, "FEMALE", "2000-01-01", "74"),
                        registration(2, "MALE", "2000-01-01", "66"),
                        registration(3, "FEMALE", "2000-01-01", "72"),
                        registration(1, "MALE", "2000-01-01", "64")
                )
        );

        assertEquals(2, prepared.units().size());

        var men = prepared.units().get(0);
        var women = prepared.units().get(1);

        assertEquals(List.of(1L, 2L), ids(men));
        assertEquals(List.of(3L, 4L), ids(women));

        assertLanes(men);
        assertLanes(women);

        for (var assignment : men.assignments()) {
            assertEquals("MEN", assignment.categoryCode());
            assertEquals("MEN", assignment.seedingGroupCode());
        }

        for (var assignment : women.assignments()) {
            assertEquals("WOMEN", assignment.categoryCode());
            assertEquals("WOMEN", assignment.seedingGroupCode());
        }
    }

    @Test
    void differentAgeCategoriesCanShareAHeatWithoutLosingCategory()
            throws Exception {
        var rules = rules(
                "{}",
                """
                {
                  "categories":[
                    {
                      "code":"MEN_18_29",
                      "seedingGroupCode":"MEN",
                      "eligibility":{
                        "allowedGenders":["MALE"],
                        "ageMethod":"ON_DATE",
                        "ageReferenceDate":"2026-11-07",
                        "minAge":18,
                        "maxAge":29
                      }
                    },
                    {
                      "code":"MEN_30_PLUS",
                      "seedingGroupCode":"MEN",
                      "eligibility":{
                        "allowedGenders":["MALE"],
                        "ageMethod":"ON_DATE",
                        "ageReferenceDate":"2026-11-07",
                        "minAge":30
                      }
                    }
                  ]
                }
                """
        );

        var prepared = subject.prepare(
                UUID.randomUUID(),
                rules,
                List.of(
                        registration(1, "MALE", "2000-01-01", "65"),
                        registration(2, "MALE", "1990-01-01", "60")
                )
        );

        assertEquals(1, prepared.units().size());

        var unit = prepared.units().get(0);

        assertEquals(List.of(2L, 1L), ids(unit));
        assertLanes(unit);

        assertEquals(
                List.of("MEN_30_PLUS", "MEN_18_29"),
                unit.assignments().stream()
                        .map(GeneratedAssignment::categoryCode)
                        .toList()
        );

        assertTrue(
                unit.assignments().stream()
                        .allMatch(a ->
                                "MEN".equals(a.seedingGroupCode())
                        )
        );
    }

    @Test
    void appliesCapacityAndPartialHeatPlacementInsideEachGroup()
            throws Exception {
        List<ConfirmedRegistrationView> registrations =
                new ArrayList<>();

        for (int i = 1; i <= 5; i++) {
            registrations.add(
                    registration(
                            i,
                            "MALE",
                            "2000-01-01",
                            Integer.toString(60 + i)
                    )
            );

            registrations.add(
                    registration(
                            10 + i,
                            "FEMALE",
                            "2000-01-01",
                            Integer.toString(70 + i)
                    )
            );
        }

        var prepared = subject.prepare(
                UUID.randomUUID(),
                rules("{}", separateCategories()),
                registrations
        );

        assertEquals(4, prepared.units().size());

        assertEquals(
                List.of(
                        List.of(5L),
                        List.of(1L, 2L, 3L, 4L),
                        List.of(15L),
                        List.of(11L, 12L, 13L, 14L)
                ),
                prepared.units().stream()
                        .map(this::ids)
                        .toList()
        );

        assertEquals(
                List.of("MEN", "MEN", "WOMEN", "WOMEN"),
                prepared.units().stream()
                        .map(u -> u.assignments().get(0)
                                .seedingGroupCode())
                        .toList()
        );

        for (int i = 0; i < prepared.units().size(); i++) {
            var unit = prepared.units().get(i);

            assertEquals(i + 1, unit.sequenceNumber());
            assertEquals("Заход " + (i + 1), unit.label());
            assertLanes(unit);

            assertEquals(
                    1L,
                    unit.assignments().stream()
                            .map(GeneratedAssignment::seedingGroupCode)
                            .distinct()
                            .count()
            );
        }

        var allIds = prepared.units().stream()
                .flatMap(u -> u.assignments().stream())
                .map(GeneratedAssignment::registrationId)
                .toList();

        assertEquals(10, allIds.size());
        assertEquals(10, new HashSet<>(allIds).size());
    }

    @Test
    void keepsMissingTimeOnWeakSideWithinItsOwnGroup()
            throws Exception {
        var prepared = subject.prepare(
                UUID.randomUUID(),
                rules("{}", separateCategories()),
                List.of(
                        registration(1, "MALE", "2000-01-01", "60"),
                        registration(2, "MALE", "2000-01-01", "61"),
                        registration(3, "MALE", "2000-01-01", "62"),
                        registration(4, "MALE", "2000-01-01", "63"),
                        registration(5, "MALE", "2000-01-01", null),
                        registration(6, "FEMALE", "2000-01-01", "55")
                )
        );

        assertEquals(
                List.of(
                        List.of(5L),
                        List.of(1L, 2L, 3L, 4L),
                        List.of(6L)
                ),
                prepared.units().stream()
                        .map(this::ids)
                        .toList()
        );

        var missing = prepared.units().get(0)
                .assignments().get(0);

        assertNull(missing.seedMetric());
        assertEquals("MEN", missing.categoryCode());
        assertEquals("MEN", missing.seedingGroupCode());
        assertEquals(Integer.valueOf(4), missing.laneOrPosition());
    }

    @Test
    void rejectsGlobalEligibilityViolationBeforeRunningBlocks()
            throws Exception {
        var rules = rules(
                """
                {
                  "ageMethod":"ON_DATE",
                  "ageReferenceDate":"2026-11-07",
                  "minAge":18
                }
                """,
                separateCategories()
        );

        RuleExecutorRegistry unusedRegistry =
                mock(RuleExecutorRegistry.class);

        var preparation = new PrepareStartProtocol(
                unusedRegistry,
                json
        );

        var tooYoung = registration(
                1,
                "MALE",
                "2010-01-01",
                "60"
        );

        assertThrows(
                ProtocolEligibilityException.class,
                () -> preparation.prepare(
                        UUID.randomUUID(),
                        rules,
                        List.of(tooYoung)
                )
        );

        verifyNoInteractions(unusedRegistry);
    }

    @Test
    void rejectsAmbiguousCategoriesBeforeRunningBlocks()
            throws Exception {
        var rules = rules(
                "{}",
                """
                {
                  "categories":[
                    {
                      "code":"MEN_A",
                      "seedingGroupCode":"MEN",
                      "eligibility":{"allowedGenders":["MALE"]}
                    },
                    {
                      "code":"MEN_B",
                      "seedingGroupCode":"MEN",
                      "eligibility":{"allowedGenders":["MALE"]}
                    }
                  ]
                }
                """
        );

        RuleExecutorRegistry unusedRegistry =
                mock(RuleExecutorRegistry.class);

        var preparation = new PrepareStartProtocol(
                unusedRegistry,
                json
        );

        var man = registration(
                1,
                "MALE",
                "2000-01-01",
                "60"
        );

        assertThrows(
                ProtocolCategoryException.class,
                () -> preparation.prepare(
                        UUID.randomUUID(),
                        rules,
                        List.of(man)
                )
        );

        verifyNoInteractions(unusedRegistry);
    }

    @Test
    void rejectsDuplicateRegistrationIds()
            throws Exception {
        var rules = rules("{}", "{}");

        var first = registration(
                1,
                "MALE",
                "2000-01-01",
                "60"
        );

        var duplicate = registration(
                1,
                "MALE",
                "2000-01-01",
                "65"
        );

        assertThrows(
                BusinessException.class,
                () -> subject.prepare(
                        UUID.randomUUID(),
                        rules,
                        List.of(first, duplicate)
                )
        );
    }

    @Test
    void legacyModeDoesNotInventCategories()
            throws Exception {
        var prepared = subject.prepare(
                UUID.randomUUID(),
                rules("{}", "{}"),
                List.of(
                        registration(1, "MALE", "2000-01-01", "65"),
                        registration(2, "FEMALE", "2000-01-01", "60")
                )
        );

        assertEquals(1, prepared.units().size());

        var unit = prepared.units().get(0);

        assertEquals(List.of(2L, 1L), ids(unit));
        assertLanes(unit);

        for (var assignment : unit.assignments()) {
            assertNull(assignment.categoryCode());
            assertNull(assignment.seedingGroupCode());
        }
    }
}