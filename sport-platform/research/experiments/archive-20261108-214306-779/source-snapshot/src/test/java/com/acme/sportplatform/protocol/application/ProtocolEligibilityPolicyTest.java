package com.acme.sportplatform.protocol.application;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.*;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.protocol.api.*;
import jakarta.validation.Validation;
import static org.junit.jupiter.api.Assertions.*;
class ProtocolEligibilityPolicyTest {
    private final ObjectMapper json=new ObjectMapper();
    private ProtocolEligibilityPolicy policy(String raw) throws Exception {return ProtocolEligibilityPolicy.parse(json.readTree(raw));}
    private void rejects(String raw) throws Exception {
        var node=json.readTree(raw);var ex=assertThrows(BusinessException.class,()->ProtocolEligibilityPolicy.parse(node));
        assertEquals("protocol.invalid_rule_config",ex.getCode());
    }
    @Test void emptyConfigurationIsLegacyMode() throws Exception {assertFalse(policy("{}").enabled());}
    @Test void acceptsExplicitInclusiveBounds() throws Exception {
        var p=policy("{\"ageMethod\":\"ON_DATE\",\"ageReferenceDate\":\"2026-11-22\",\"minAge\":8,\"maxAge\":11,\"allowedGenders\":[\"MALE\"]}");
        assertTrue(p.checksAge());assertEquals(8,p.minAge());assertEquals(11,p.maxAge());assertEquals(Set.of("MALE"),p.allowedGenders());
    }
    @Test void acceptsGenderOnlyWithoutInventingAnAgeDate() throws Exception {
        var p=policy("{\"allowedGenders\":[\"FEMALE\"]}");assertTrue(p.enabled());assertFalse(p.checksAge());
    }
    @Test void rejectsAgeWithoutReferenceDate() throws Exception {rejects("{\"ageMethod\":\"BY_YEAR\",\"minAge\":8}");}
    @Test void rejectsAgeWithoutMethod() throws Exception {rejects("{\"ageReferenceDate\":\"2026-11-22\",\"minAge\":8}");}
    @Test void rejectsAgeWithoutBounds() throws Exception {rejects("{\"ageMethod\":\"BY_YEAR\",\"ageReferenceDate\":\"2026-11-22\"}");}
    @Test void rejectsUnknownMethod() throws Exception {rejects("{\"ageMethod\":\"AUTO\",\"ageReferenceDate\":\"2026-11-22\",\"minAge\":8}");}
    @Test void rejectsInvalidReferenceDate() throws Exception {rejects("{\"ageMethod\":\"ON_DATE\",\"ageReferenceDate\":\"2026-02-30\",\"minAge\":8}");}
    @Test void rejectsReversedBounds() throws Exception {rejects("{\"ageMethod\":\"BY_YEAR\",\"ageReferenceDate\":\"2026-11-22\",\"minAge\":12,\"maxAge\":8}");}
    @Test void rejectsNegativeFractionalAndNullBounds() throws Exception {
        for(String value:List.of("-1","8.5","null")) rejects("{\"ageMethod\":\"BY_YEAR\",\"ageReferenceDate\":\"2026-11-22\",\"minAge\":"+value+"}");
    }
    @Test void rejectsEmptyDuplicateAndUnknownGenders() throws Exception {
        rejects("{\"allowedGenders\":[]}");rejects("{\"allowedGenders\":[\"MALE\",\"MALE\"]}");rejects("{\"allowedGenders\":[\"UNKNOWN\"]}");
    }
    @Test void rejectsUnknownParameters() throws Exception {rejects("{\"minimumAge\":8}");}
    @Test void existingRuleValidatorAcceptsEligibilityConfiguration() throws Exception {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var validator=new ProtocolRuleSetValidator(factory.getValidator());
            var config=json.readTree("{\"allowedGenders\":[\"MALE\"]}");
            var blocks=List.of(new RuleBlockRequest(0,"ELIGIBILITY_CONFIRMED_ONLY",config),
                new RuleBlockRequest(1,"SEED_BY_METRIC_ASC_NULLS_LAST",json.readTree("{\"metricKey\":\"seedTimeSeconds\"}")),
                new RuleBlockRequest(2,"GROUP_BY_CAPACITY",json.readTree("{\"capacity\":2,\"unitLabelPrefix\":\"Heat\"}")),
                new RuleBlockRequest(3,"ASSIGN_CENTER_OUT",json.readTree("{\"positionOrder\":[2,1]}")));
            assertEquals(4,validator.validate(new CreateProtocolRuleSetRequest(UUID.randomUUID(),"eligible-test","Eligible test","INDIVIDUAL","HEAT",blocks)).size());
        }
    }
}
