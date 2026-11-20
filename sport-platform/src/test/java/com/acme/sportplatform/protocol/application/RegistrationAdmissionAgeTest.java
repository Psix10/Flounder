package com.acme.sportplatform.protocol.application;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.acme.sportplatform.protocol.api.RuleBlockRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationAdmissionAgeTest {
    private final ObjectMapper json = new ObjectMapper();
    private final RegistrationAdmissionPolicy policy = new RegistrationAdmissionPolicy(json);
    private final UUID registration = UUID.randomUUID();

    private ObjectNode age(String method, int min, int max) {
        return json.createObjectNode()
                .put("ageMethod", method)
                .put("ageReferenceDate", "2026-11-22")
                .put("minAge", min)
                .put("maxAge", max);
    }

    private ResolvedProtocolRules rules(ObjectNode eligibility, ObjectNode grouping) {
        return new ResolvedProtocolRules(
                UUID.randomUUID(), 4, "Heat", "seedTimeSeconds",
                List.of(2, 3, 1, 4), List.of(1, 2, 3, 4),
                "FASTEST_FIRST", "LAST",
                List.of(
                        new RuleBlockRequest(0, "ELIGIBILITY_CONFIRMED_ONLY", eligibility),
                        new RuleBlockRequest(2, "GROUP_BY_CAPACITY", grouping)
                )
        );
    }

    private ResolvedProtocolRules general(String method, int min, int max) {
        return rules(age(method, min, max), json.createObjectNode());
    }

    private String person(String birthDate) {
        return json.createObjectNode()
                .put("birthDate", birthDate)
                .put("gender", "MALE")
                .toString();
    }

    private void rejected(
            String snapshot,
            ResolvedProtocolRules rules,
            String reason
    ) {
        var error = assertThrows(ProtocolEligibilityException.class, () ->
                policy.verify(registration, snapshot, "{}", rules));

        assertTrue(error.violations().stream().anyMatch(violation ->
                registration.equals(violation.registrationId())
                        && "participantSnapshot.birthDate".equals(violation.field())
                        && reason.equals(violation.reason())));
    }

    @Test
    void acceptsExactMinimumBirthday() {
        assertDoesNotThrow(() -> policy.verify(
                registration, person("2008-11-22"), "{}",
                general("ON_DATE", 18, 25)));
    }

    @Test
    void rejectsDayBeforeMinimumBirthday() {
        rejected(person("2008-11-23"), general("ON_DATE", 18, 25),
                "AGE_OUT_OF_RANGE");
    }

    @Test
    void acceptsMaximumAgeInclusively() {
        assertDoesNotThrow(() -> policy.verify(
                registration, person("2000-11-23"), "{}",
                general("ON_DATE", 18, 25)));
    }

    @Test
    void rejectsAboveMaximumAge() {
        rejected(person("2000-11-22"), general("ON_DATE", 18, 25),
                "AGE_OUT_OF_RANGE");
    }

    @Test
    void byYearAndOnDateKeepDifferentSemantics() {
        String snapshot = person("2008-12-31");

        assertDoesNotThrow(() -> policy.verify(
                registration, snapshot, "{}",
                general("BY_YEAR", 18, 25)));

        rejected(snapshot, general("ON_DATE", 18, 25),
                "AGE_OUT_OF_RANGE");
    }

    @Test
    void rejectsMissingBirthDate() {
        rejected("{\"gender\":\"MALE\"}", general("ON_DATE", 18, 25),
                "MISSING_BIRTH_DATE");
    }

    @Test
    void rejectsImpossibleBirthDate() {
        rejected(person("2008-02-30"), general("ON_DATE", 18, 25),
                "INVALID_BIRTH_DATE");
    }

    @Test
    void rejectsNonTextualBirthDate() {
        rejected("{\"birthDate\":2008,\"gender\":\"MALE\"}",
                general("ON_DATE", 18, 25), "INVALID_BIRTH_DATE");
    }

    @Test
    void rejectsFutureBirthDateEvenWithZeroMinimum() {
        rejected(person("2026-11-23"), general("BY_YEAR", 0, 25),
                "BIRTH_DATE_AFTER_REFERENCE_DATE");
    }

    @Test
    void categoryAgeIsCheckedWhenGeneralEligibilityIsEmpty() {
        var group = json.createObjectNode();
        var category = group.putArray("categories").addObject();
        category.put("code", "JUNIOR");
        category.put("seedingGroupCode", "JUNIOR");
        category.set("eligibility", age("BY_YEAR", 10, 12));

        var rules = rules(json.createObjectNode(), group);

        for (String birthDate : List.of("2014-01-01", "2016-12-31")) {
            assertDoesNotThrow(() -> policy.verify(
                    registration, person(birthDate), "{}", rules));
        }

        for (String birthDate : List.of("2013-12-31", "2017-01-01")) {
            var error = assertThrows(ProtocolCategoryException.class, () ->
                    policy.verify(registration, person(birthDate), "{}", rules));

            assertTrue(error.violations().stream().anyMatch(violation ->
                    registration.equals(violation.registrationId())
                            && "NO_MATCHING_CATEGORY".equals(violation.reason())));
        }
    }
}