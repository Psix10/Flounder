package com.acme.sportplatform.protocol.application;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.protocol.RegistrationProtocolAccess.ConfirmedRegistrationView;
import com.acme.sportplatform.protocol.api.*;
import com.acme.sportplatform.protocol.application.strategy.*;
import jakarta.validation.Validation;

class ProtocolCategoryTest {
    private final ObjectMapper json = new ObjectMapper();
    private JsonNode node(String raw) throws Exception { return json.readTree(raw); }
    private ObjectNode config(String maleGroup, String femaleGroup) throws Exception {
        return (ObjectNode) node("""
            {"capacity":2,"unitLabelPrefix":"Heat","categories":[
              {"code":"MEN","seedingGroupCode":"M","eligibility":{"allowedGenders":["MALE"]}},
              {"code":"WOMEN","seedingGroupCode":"F","eligibility":{"allowedGenders":["FEMALE"]}}
            ]}
            """);
    }
    private ObjectNode groups(String male, String female) throws Exception {
        var c = config(male, female);
        ((ObjectNode)c.path("categories").get(0)).put("seedingGroupCode", male);
        ((ObjectNode)c.path("categories").get(1)).put("seedingGroupCode", female);
        return c;
    }
    private ConfirmedRegistrationView reg(String gender, Integer metric) {
        return new ConfirmedRegistrationView(UUID.randomUUID(), metric == null ? "{}" : "{\"seed\":" + metric + "}",
                UUID.randomUUID(), gender == null ? "{}" : "{\"gender\":\"" + gender + "\",\"birthDate\":\"2004-05-10\"}");
    }
    private ResolvedProtocolRules rules(JsonNode grouping) throws Exception {
        return new ResolvedProtocolRules(UUID.randomUUID(), 2, "Heat", "seed", List.of(1,2), List.of(1,2), "FASTEST_FIRST", "LAST",
            List.of(new RuleBlockRequest(0,"ELIGIBILITY_CONFIRMED_ONLY",node("{}")),
                    new RuleBlockRequest(1,"SEED_BY_METRIC_ASC_NULLS_LAST",node("{\"metricKey\":\"seed\"}")),
                    new RuleBlockRequest(2,"GROUP_BY_CAPACITY",grouping),
                    new RuleBlockRequest(3,"ASSIGN_CENTER_OUT",node("{\"positionOrder\":[1,2]}"))));
    }
    private PrepareStartProtocol preparer() {
        return new PrepareStartProtocol(new RuleExecutorRegistry(List.of(new EligibilityConfirmedOnlyExecutor(),
                new SeedByMetricAscNullsLastExecutor(), new GroupByCapacityExecutor(), new AssignCenterOutExecutor())), json);
    }
    private PreparedStartProtocol prepare(JsonNode c, ConfirmedRegistrationView... registrations) throws Exception {
        return preparer().prepare(UUID.randomUUID(),rules(c),List.of(registrations));
    }
    private void invalid(JsonNode c) {
        assertEquals("protocol.invalid_rule_config", assertThrows(BusinessException.class, () -> ProtocolCategoryPolicy.parse(c)).getCode());
    }
    @Test void omissionRetainsLegacyModeAndDoesNotRequireSnapshot() throws Exception {
        var registration = new ConfirmedRegistrationView(UUID.randomUUID(),"{}");
        var p = prepare(node("{}"), registration);
        assertEquals(1,p.units().size()); assertNull(p.units().get(0).assignments().get(0).categoryCode());
    }
    @Test void rejectsEmptyArray() throws Exception { invalid(node("{\"categories\":[]}")); }
    @Test void rejectsNullCategories() throws Exception { invalid(node("{\"categories\":null}")); }
    @Test void rejectsNonArrayCategories() throws Exception { invalid(node("{\"categories\":{}}")); }
    @Test void rejectsDuplicateCategoryCodes() throws Exception {
        var c=groups("M","F"); ((ObjectNode)c.path("categories").get(1)).put("code","MEN"); invalid(c);
    }
    @Test void rejectsMissingExplicitGroup() throws Exception {
        var c=groups("M","F"); ((ObjectNode)c.path("categories").get(0)).remove("seedingGroupCode"); invalid(c);
    }
    @Test void rejectsUnknownCategoryParameter() throws Exception {
        var c=groups("M","F"); ((ObjectNode)c.path("categories").get(0)).put("priority",1); invalid(c);
    }
    @Test void rejectsInvalidIdentifier() throws Exception {
        var c=groups("M","F"); ((ObjectNode)c.path("categories").get(0)).put("code","bad code"); invalid(c);
    }
    @Test void reusesStrictEligibilityConfigValidation() throws Exception {
        var c=groups("M","F"); ((ObjectNode)c.path("categories").get(0)).set("eligibility",node("{\"minAge\":12}")); invalid(c);
    }
    @Test void classifiesCapturedGender() throws Exception {
        var r=reg("FEMALE",10);var m=ProtocolCategoryPolicy.parse(groups("M","F")).classify(List.of(r),json).get(r.registrationId());
        assertEquals("WOMEN",m.categoryCode());assertEquals("F",m.seedingGroupCode());
    }
    @Test void missingGenderIsNotSilentlyDropped() throws Exception {
        var r=reg(null,10);var policy=ProtocolCategoryPolicy.parse(groups("M","F"));
        var ex=assertThrows(ProtocolCategoryException.class,()->policy.classify(List.of(r),json));
        assertEquals(r.registrationId(),ex.violations().get(0).registrationId());
        assertEquals("NO_MATCHING_CATEGORY",ex.violations().get(0).reason());
        assertTrue(ex.violations().get(0).message().contains("MISSING_GENDER"));
    }
    @Test void overlapIsAnErrorEvenForSharedGroup() throws Exception {
        var c=groups("OPEN","OPEN"); ((ObjectNode)c.path("categories").get(1)).set("eligibility",node("{}"));
        var policy=ProtocolCategoryPolicy.parse(c);var r=reg("MALE",10);
        var ex=assertThrows(ProtocolCategoryException.class,()->policy.classify(List.of(r),json));
        assertEquals("AMBIGUOUS_CATEGORY",ex.violations().get(0).reason());
        assertTrue(ex.violations().get(0).message().contains("MEN"));assertTrue(ex.violations().get(0).message().contains("WOMEN"));
    }
    @Test void separatesGroupsEvenWhenCapacityWouldAllowMixing() throws Exception {
        var p=prepare(groups("M","F"),reg("MALE",20),reg("FEMALE",10));
        assertEquals(2,p.units().size());
        assertEquals("M",p.units().get(0).assignments().get(0).seedingGroupCode());
        assertEquals("F",p.units().get(1).assignments().get(0).seedingGroupCode());
        assertEquals(List.of(1,2),p.units().stream().map(GeneratedCompetitionUnit::sequenceNumber).toList());
    }
    @Test void explicitSharedGroupAllowsDifferentScoringCategories() throws Exception {
        var p=prepare(groups("OPEN","OPEN"),reg("MALE",20),reg("FEMALE",10));
        assertEquals(1,p.units().size());assertEquals(List.of("WOMEN","MEN"),p.units().get(0).assignments().stream().map(GeneratedAssignment::categoryCode).toList());
    }
    @Test void partialHeatsAreComputedWithinEachGroup() throws Exception {
        var p=prepare(groups("M","F"),reg("MALE",30),reg("FEMALE",10),reg("MALE",20),reg("MALE",40));
        assertEquals(List.of(2,1,1),p.units().stream().map(u->u.assignments().size()).toList());
        for(var u:p.units())assertEquals(1,u.assignments().stream().map(GeneratedAssignment::seedingGroupCode).distinct().count());
    }
    @Test void noTimeIsLastWithinItsGroup() throws Exception {
        var nt=reg("MALE",null);var timed=reg("MALE",20);
        var p=prepare(groups("M","F"),nt,timed,reg("FEMALE",10));
        assertEquals(List.of(timed.registrationId(),nt.registrationId()),p.units().get(0).assignments().stream().map(GeneratedAssignment::registrationId).toList());
    }
    @Test void draftsRetainCategoryAndGroup() throws Exception {
        var p=prepare(groups("M","F"),reg("MALE",20));var a=p.toDrafts().get(0).assignments().get(0);
        assertEquals("MEN",a.categoryCode());assertEquals("M",a.seedingGroupCode());
    }
    @Test void generatedSnapshotContainsCategoryAndGroup() throws Exception {
        var p=prepare(groups("M","F"),reg("FEMALE",20));var a=json.valueToTree(p).path("units").get(0).path("assignments").get(0);
        assertEquals("WOMEN",a.path("categoryCode").asText());assertEquals("F",a.path("seedingGroupCode").asText());
    }
    @Test void yearBasedAgeUsesInclusiveBoundary() throws Exception {
        var c=node("""
          {"categories":[{"code":"AGE22","seedingGroupCode":"A","eligibility":{
          "ageMethod":"BY_YEAR","ageReferenceDate":"2026-01-01","minAge":22,"maxAge":22}}]}
          """);
        var p=prepare(c,reg("MALE",20));assertEquals("AGE22",p.units().get(0).assignments().get(0).categoryCode());
    }
    @Test void dateBasedAgeDoesNotRoundUpBeforeBirthday() throws Exception {
        var c=node("""
          {"categories":[{"code":"AGE22","seedingGroupCode":"A","eligibility":{
          "ageMethod":"ON_DATE","ageReferenceDate":"2026-05-09","minAge":22,"maxAge":22}}]}
          """);
        var r=reg("MALE",20);var rules=rules(c);
        assertThrows(ProtocolCategoryException.class,()->preparer().prepare(UUID.randomUUID(),rules,List.of(r)));
    }
    @Test void ruleValidatorAcceptsCategoriesInGroupingStage() throws Exception {
        var r=rules(groups("M","F"));
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var request=new CreateProtocolRuleSetRequest(UUID.randomUUID(),"category-test","Category test","INDIVIDUAL","HEAT",r.blocks());
            assertEquals(4,new ProtocolRuleSetValidator(factory.getValidator()).validate(request).size());
        }
    }
}
