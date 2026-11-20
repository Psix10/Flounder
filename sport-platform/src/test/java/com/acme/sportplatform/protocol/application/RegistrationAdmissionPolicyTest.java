package com.acme.sportplatform.protocol.application;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.protocol.api.RuleBlockRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationAdmissionPolicyTest {
    private final ObjectMapper json = new ObjectMapper();
    private final RegistrationAdmissionPolicy policy = new RegistrationAdmissionPolicy(json);
    private final UUID registration = UUID.randomUUID();

    private ResolvedProtocolRules rules(String eligibility, String grouping) throws Exception {
        return new ResolvedProtocolRules(
                UUID.randomUUID(), 4, "Heat", "seedTimeSeconds",
                List.of(2, 3, 1, 4), List.of(1, 2, 3, 4),
                "FASTEST_FIRST", "LAST",
                List.of(
                        new RuleBlockRequest(0, "ELIGIBILITY_CONFIRMED_ONLY", json.readTree(eligibility)),
                        new RuleBlockRequest(2, "GROUP_BY_CAPACITY", json.readTree(grouping))
                )
        );
    }

    private String categories() {
        return """
                {"categories":[
                  {"code":"MEN","seedingGroupCode":"OPEN",
                   "eligibility":{"allowedGenders":["MALE"]}},
                  {"code":"WOMEN","seedingGroupCode":"OPEN",
                   "eligibility":{"allowedGenders":["FEMALE"]}}
                ]}
                """;
    }

    @Test
    void legacyWithoutRestrictionsDoesNotInventSnapshotRequirements() {
        assertDoesNotThrow(() -> policy.verify(registration, "{}", "{}", null));
    }

    @Test
    void legacyStillChecksDisciplineGender() {
        assertThrows(ProtocolEligibilityException.class, () ->
                policy.verify(registration, "{\"gender\":\"FEMALE\"}",
                        "{\"gender\":\"MALE\"}", null));
    }

    @Test
    void legacyAcceptsCompatibleHistoricalGenderCase() {
        assertDoesNotThrow(() ->
                policy.verify(registration, "{\"gender\":\"male\"}",
                        "{\"gender\":\"MALE\"}", null));
    }

    @Test
    void categoryFilterWithoutBindingFailsClosed() {
        assertThrows(BusinessException.class, () ->
                policy.verify(registration, "{}",
                        "{\"allowedCategoryCodes\":[\"MEN\"]}", null));
    }

    @Test
    void boundGeneralEligibilityIsNotSkipped() throws Exception {
        var rules = rules("{\"allowedGenders\":[\"MALE\"]}", "{}");
        assertThrows(ProtocolEligibilityException.class, () ->
                policy.verify(registration, "{\"gender\":\"FEMALE\"}", "{}", rules));
    }

    @Test
    void boundCategoriesAreCheckedEvenWithoutDisciplineFilter() throws Exception {
        var rules = rules("{}", categories());
        assertThrows(ProtocolCategoryException.class, () ->
                policy.verify(registration, "{}", "{}", rules));
    }

    @Test
    void allowedCategoryIsAccepted() throws Exception {
        var rules = rules("{}", categories());
        assertDoesNotThrow(() ->
                policy.verify(registration, "{\"gender\":\"MALE\"}",
                        "{\"allowedCategoryCodes\":[\"MEN\"]}", rules));
    }

    @Test
    void excludedCategoryIsRejected() throws Exception {
        var rules = rules("{}", categories());
        assertThrows(ProtocolCategoryException.class, () ->
                policy.verify(registration, "{\"gender\":\"FEMALE\"}",
                        "{\"allowedCategoryCodes\":[\"MEN\"]}", rules));
    }

    @Test
    void unknownCategoryCodeIsConfigurationError() throws Exception {
        var rules = rules("{}", categories());
        assertThrows(BusinessException.class, () ->
                policy.verify(registration, "{\"gender\":\"MALE\"}",
                        "{\"allowedCategoryCodes\":[\"UNKNOWN\"]}", rules));
    }

    @Test
    void overlappingCategoriesAreRejected() throws Exception {
        var rules = rules("{}", """
                {"categories":[
                  {"code":"A","seedingGroupCode":"OPEN","eligibility":{}},
                  {"code":"B","seedingGroupCode":"OPEN","eligibility":{}}
                ]}
                """);
        assertThrows(ProtocolCategoryException.class, () ->
                policy.verify(registration, "{}", "{}", rules));
    }

    @Test
    void rulesAndSnapshotRemainUnchanged() throws Exception {
        var rules = rules("{}", categories());
        String before = json.writeValueAsString(rules);
        var snapshot = json.createObjectNode().put("gender", "MALE");
        String captured = snapshot.toString();

        policy.verify(registration, captured,
                "{\"allowedCategoryCodes\":[\"MEN\"]}", rules);

        assertEquals(before, json.writeValueAsString(rules));
        assertEquals(captured, snapshot.toString());
    }

    @Test
    void duplicateEligibilityBlocksAreRejected() throws Exception {
        var base = rules("{}", "{}");
        var invalid = new ResolvedProtocolRules(
                base.versionId(), base.capacity(), base.prefix(), base.metricKey(),
                base.order(), base.allowed(), base.heatOrder(), base.partialHeat(),
                List.of(base.blocks().get(0), base.blocks().get(0), base.blocks().get(1))
        );
        assertThrows(BusinessException.class, () ->
                policy.verify(registration, "{}", "{}", invalid));
    }
}