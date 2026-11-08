package com.acme.sportplatform.protocol.application;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.protocol.RegistrationProtocolAccess.ConfirmedRegistrationView;
import com.fasterxml.jackson.databind.ObjectMapper;

class ProtocolEligibilityAndCategoryTest {

    private final ObjectMapper json = new ObjectMapper();

    private final ProtocolEligibilityVerifier verifier =
            new ProtocolEligibilityVerifier(json);

    private ProtocolEligibilityPolicy policy(String config)
            throws Exception {
        return ProtocolEligibilityPolicy.parse(json.readTree(config));
    }

    private ConfirmedRegistrationView registration(
            int id,
            String snapshot
    ) {
        var registration = mock(ConfirmedRegistrationView.class);

        when(registration.registrationId())
                .thenReturn(new UUID(0L, id));

        when(registration.participantSnapshot())
                .thenReturn(snapshot);

        return registration;
    }

    private void assertViolation(
            ProtocolEligibilityPolicy policy,
            String snapshot,
            String expectedReason
    ) {
        var registration = registration(1, snapshot);

        var exception = assertThrows(
                ProtocolEligibilityException.class,
                () -> verifier.verify(
                        List.of(registration),
                        policy
                )
        );

        assertTrue(
                exception.violations().stream()
                        .anyMatch(v ->
                                expectedReason.equals(v.reason())
                        ),
                "Expected violation: " + expectedReason
        );
    }

    @Test
    void emptyPolicyDisablesChecks() throws Exception {
        var policy = policy("{}");

        assertFalse(policy.enabled());

        var registration = registration(1, null);

        assertDoesNotThrow(
                () -> verifier.verify(
                        List.of(registration),
                        policy
                )
        );
    }

    @Test
    void rejectsUnknownPolicyParameters() throws Exception {
        var config = json.readTree("""
                {"minimumAge":18}
                """);

        assertThrows(
                BusinessException.class,
                () -> ProtocolEligibilityPolicy.parse(config)
        );
    }

    @Test
    void rejectsInvalidAgeConfigurations() throws Exception {
        List<String> configs = List.of(
                """
                {"minAge":18}
                """,
                """
                {
                  "ageMethod":"ON_DATE",
                  "ageReferenceDate":"2026-11-07"
                }
                """,
                """
                {
                  "ageMethod":"UNKNOWN",
                  "ageReferenceDate":"2026-11-07",
                  "minAge":18
                }
                """,
                """
                {
                  "ageMethod":"ON_DATE",
                  "ageReferenceDate":"not-a-date",
                  "minAge":18
                }
                """,
                """
                {
                  "ageMethod":"ON_DATE",
                  "ageReferenceDate":"2026-11-07",
                  "minAge":-1
                }
                """,
                """
                {
                  "ageMethod":"ON_DATE",
                  "ageReferenceDate":"2026-11-07",
                  "minAge":18.5
                }
                """,
                """
                {
                  "ageMethod":"ON_DATE",
                  "ageReferenceDate":"2026-11-07",
                  "minAge":30,
                  "maxAge":18
                }
                """
        );

        for (String config : configs) {
            var node = json.readTree(config);

            assertThrows(
                    BusinessException.class,
                    () -> ProtocolEligibilityPolicy.parse(node),
                    config
            );
        }
    }

    @Test
    void rejectsEmptyDuplicateAndUnknownGenders() throws Exception {
        for (String config : List.of(
                """
                {"allowedGenders":[]}
                """,
                """
                {"allowedGenders":["MALE","MALE"]}
                """,
                """
                {"allowedGenders":["UNKNOWN"]}
                """,
                """
                {"allowedGenders":"MALE"}
                """
        )) {
            var node = json.readTree(config);

            assertThrows(
                    BusinessException.class,
                    () -> ProtocolEligibilityPolicy.parse(node),
                    config
            );
        }
    }

    @Test
    void ageBoundsAreInclusive() throws Exception {
        var policy = policy("""
                {
                  "ageMethod":"ON_DATE",
                  "ageReferenceDate":"2026-11-07",
                  "minAge":18,
                  "maxAge":29
                }
                """);

        assertDoesNotThrow(
                () -> verifier.verify(
                        List.of(
                                registration(
                                        1,
                                        "{\"birthDate\":\"2008-11-07\"}"
                                ),
                                registration(
                                        2,
                                        "{\"birthDate\":\"1997-11-07\"}"
                                )
                        ),
                        policy
                )
        );

        assertViolation(
                policy,
                "{\"birthDate\":\"2008-11-08\"}",
                "AGE_OUT_OF_RANGE"
        );

        assertViolation(
                policy,
                "{\"birthDate\":\"1996-11-07\"}",
                "AGE_OUT_OF_RANGE"
        );
    }

    @Test
    void ageMethodsDifferBeforeBirthday() throws Exception {
        var onDate = policy("""
                {
                  "ageMethod":"ON_DATE",
                  "ageReferenceDate":"2026-06-01",
                  "minAge":18
                }
                """);

        var byYear = policy("""
                {
                  "ageMethod":"BY_YEAR",
                  "ageReferenceDate":"2026-06-01",
                  "minAge":18
                }
                """);

        String snapshot = "{\"birthDate\":\"2008-12-31\"}";

        assertViolation(
                onDate,
                snapshot,
                "AGE_OUT_OF_RANGE"
        );

        assertDoesNotThrow(
                () -> verifier.verify(
                        List.of(registration(1, snapshot)),
                        byYear
                )
        );
    }

    @Test
    void acceptsUpperAgeBoundWithoutLowerBound() throws Exception {
        var policy = policy("""
                {
                  "ageMethod":"BY_YEAR",
                  "ageReferenceDate":"2026-11-07",
                  "maxAge":17
                }
                """);

        assertDoesNotThrow(
                () -> verifier.verify(
                        List.of(registration(
                                1,
                                "{\"birthDate\":\"2009-01-01\"}"
                        )),
                        policy
                )
        );

        assertViolation(
                policy,
                "{\"birthDate\":\"2008-01-01\"}",
                "AGE_OUT_OF_RANGE"
        );
    }

    @Test
    void detectsMissingInvalidAndFutureBirthDates() throws Exception {
        var policy = policy("""
                {
                  "ageMethod":"ON_DATE",
                  "ageReferenceDate":"2026-11-07",
                  "minAge":0
                }
                """);

        assertViolation(
                policy,
                "{}",
                "MISSING_BIRTH_DATE"
        );

        assertViolation(
                policy,
                "{\"birthDate\":\"invalid\"}",
                "INVALID_BIRTH_DATE"
        );

        assertViolation(
                policy,
                "{\"birthDate\":\"2026-11-08\"}",
                "BIRTH_DATE_AFTER_REFERENCE_DATE"
        );
    }

    @Test
    void detectsMissingInvalidAndDisallowedGender() throws Exception {
        var policy = policy("""
                {"allowedGenders":["MALE"]}
                """);

        assertViolation(policy, "{}", "MISSING_GENDER");

        assertViolation(
                policy,
                "{\"gender\":\"UNKNOWN\"}",
                "INVALID_GENDER"
        );

        assertViolation(
                policy,
                "{\"gender\":\"FEMALE\"}",
                "GENDER_NOT_ALLOWED"
        );

        assertDoesNotThrow(
                () -> verifier.verify(
                        List.of(registration(
                                1,
                                "{\"gender\":\"MALE\"}"
                        )),
                        policy
                )
        );
    }

    @Test
    void rejectsInvalidParticipantSnapshots() throws Exception {
        var policy = policy("""
                {"allowedGenders":["MALE","FEMALE"]}
                """);

        assertViolation(
                policy,
                null,
                "INVALID_PARTICIPANT_SNAPSHOT"
        );

        for (String snapshot : List.of(
                "not-json",
                "[]",
                "null",
                "\"text\""
        )) {
            assertViolation(
                    policy,
                    snapshot,
                    "INVALID_PARTICIPANT_SNAPSHOT"
            );
        }
    }

    @Test
    void collectsAgeAndGenderViolations() throws Exception {
        var policy = policy("""
                {
                  "ageMethod":"ON_DATE",
                  "ageReferenceDate":"2026-11-07",
                  "minAge":18,
                  "allowedGenders":["MALE"]
                }
                """);

        var exception = assertThrows(
                ProtocolEligibilityException.class,
                () -> verifier.verify(
                        List.of(
                                registration(
                                        1,
                                        """
                                        {
                                          "birthDate":"2010-01-01",
                                          "gender":"FEMALE"
                                        }
                                        """
                                ),
                                registration(2, "{}")
                        ),
                        policy
                )
        );

        assertEquals(4, exception.violations().size());

        var reasons = exception.violations().stream()
                .map(v -> v.reason())
                .toList();

        assertTrue(reasons.contains("AGE_OUT_OF_RANGE"));
        assertTrue(reasons.contains("GENDER_NOT_ALLOWED"));
        assertTrue(reasons.contains("MISSING_BIRTH_DATE"));
        assertTrue(reasons.contains("MISSING_GENDER"));
    }

    @Test
    void classifiesMenAndWomenIntoSeparateGroups() throws Exception {
        var policy = ProtocolCategoryPolicy.parse(
                json.readTree("""
                        {
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
                        """)
        );

        var man = registration(1, "{\"gender\":\"MALE\"}");
        var woman = registration(2, "{\"gender\":\"FEMALE\"}");

        var result = policy.classify(
                List.of(man, woman),
                json
        );

        assertEquals(2, result.size());

        assertEquals(
                new ProtocolCategoryPolicy.Membership("MEN", "MEN"),
                result.get(man.registrationId())
        );

        assertEquals(
                new ProtocolCategoryPolicy.Membership("WOMEN", "WOMEN"),
                result.get(woman.registrationId())
        );

        assertEquals(
                List.of("MEN", "WOMEN"),
                policy.groupOrder()
        );
    }

    @Test
    void ageCategoriesCanShareOneSeedingGroup() throws Exception {
        var policy = ProtocolCategoryPolicy.parse(
                json.readTree("""
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
                        """)
        );

        var younger = registration(
                1,
                """
                {"gender":"MALE","birthDate":"2000-01-01"}
                """
        );

        var older = registration(
                2,
                """
                {"gender":"MALE","birthDate":"1990-01-01"}
                """
        );

        var result = policy.classify(
                List.of(younger, older),
                json
        );

        assertEquals(
                new ProtocolCategoryPolicy.Membership(
                        "MEN_18_29",
                        "MEN"
                ),
                result.get(younger.registrationId())
        );

        assertEquals(
                new ProtocolCategoryPolicy.Membership(
                        "MEN_30_PLUS",
                        "MEN"
                ),
                result.get(older.registrationId())
        );

        assertEquals(List.of("MEN"), policy.groupOrder());
    }

    @Test
    void rejectsParticipantWithoutMatchingCategory() throws Exception {
        var policy = ProtocolCategoryPolicy.parse(
                json.readTree("""
                        {
                          "categories":[
                            {
                              "code":"MEN",
                              "seedingGroupCode":"MEN",
                              "eligibility":{
                                "allowedGenders":["MALE"]
                              }
                            }
                          ]
                        }
                        """)
        );

        assertThrows(
                ProtocolCategoryException.class,
                () -> policy.classify(
                        List.of(registration(
                                1,
                                "{\"gender\":\"FEMALE\"}"
                        )),
                        json
                )
        );
    }

    @Test
    void rejectsParticipantMatchingSeveralCategories() throws Exception {
        var policy = ProtocolCategoryPolicy.parse(
                json.readTree("""
                        {
                          "categories":[
                            {
                              "code":"MEN_A",
                              "seedingGroupCode":"MEN",
                              "eligibility":{
                                "allowedGenders":["MALE"]
                              }
                            },
                            {
                              "code":"MEN_B",
                              "seedingGroupCode":"MEN",
                              "eligibility":{
                                "allowedGenders":["MALE"]
                              }
                            }
                          ]
                        }
                        """)
        );

        assertThrows(
                ProtocolCategoryException.class,
                () -> policy.classify(
                        List.of(registration(
                                1,
                                "{\"gender\":\"MALE\"}"
                        )),
                        json
                )
        );
    }

    @Test
    void rejectsDuplicateCategoryCodes() throws Exception {
        var config = json.readTree("""
                {
                  "categories":[
                    {
                      "code":"MEN",
                      "seedingGroupCode":"GROUP_A",
                      "eligibility":{}
                    },
                    {
                      "code":"MEN",
                      "seedingGroupCode":"GROUP_B",
                      "eligibility":{}
                    }
                  ]
                }
                """);

        assertThrows(
                BusinessException.class,
                () -> ProtocolCategoryPolicy.parse(config)
        );
    }
}