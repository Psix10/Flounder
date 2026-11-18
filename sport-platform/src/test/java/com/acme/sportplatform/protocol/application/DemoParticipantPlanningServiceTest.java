package com.acme.sportplatform.protocol.application;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.acme.sportplatform.common.DemoDataException;
import com.acme.sportplatform.protocol.DemoParticipantPlanning.Candidate;
import com.acme.sportplatform.protocol.infrastructure.jpa.ProtocolRuleBlockEntity;
import com.acme.sportplatform.protocol.infrastructure.jpa.ProtocolRuleBlockRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class DemoParticipantPlanningServiceTest {
    private final UUID version=UUID.randomUUID();
    private final ObjectMapper json=new ObjectMapper();
    private final ProtocolRuleBlockRepository blocks=mock(ProtocolRuleBlockRepository.class);
    private final DemoParticipantPlanningService service=new DemoParticipantPlanningService(blocks,json);
    private List<ProtocolRuleBlockEntity> pipeline;
    @BeforeEach void setup() throws Exception { configure("{}","{}"); }
    private void configure(String global,String grouping) throws Exception {
        pipeline=new ArrayList<>();
        String[] codes={"ELIGIBILITY_CONFIRMED_ONLY","SEED_BY_METRIC_ASC_NULLS_LAST","GROUP_BY_CAPACITY","ASSIGN_CENTER_OUT"};
        for(int i=0;i<4;i++) {
            var block=new ProtocolRuleBlockEntity(); block.setExecutorCode(codes[i]); block.setOrderIndex(i);
            block.setConfig(json.readTree(i==0?global:i==2?grouping:"{}")); pipeline.add(block);
        }
        when(blocks.findByRuleSetVersionIdOrderByOrderIndexAsc(version)).thenReturn(pipeline);
    }
    private String category(String code,String eligibility) {
        return "{\"code\":\""+code+"\",\"seedingGroupCode\":\""+code+"\",\"eligibility\":"+eligibility+"}";
    }
    private String categories(String... definitions) { return "{\"categories\":["+String.join(",",definitions)+"]}"; }
    private List<Candidate> plan(String settings) { return service.plan(version,settings,8); }
    @Test void unrestrictedPlanAlternatesGendersAndIsDeterministic() {
        var result=plan("{}"); assertEquals(8,result.size()); assertEquals(result,plan("{}"));
        assertEquals("MALE",result.get(0).gender()); assertEquals("FEMALE",result.get(1).gender());
        assertTrue(result.stream().allMatch(c->c.birthDate().equals(LocalDate.of(2000,1,1))&&c.categoryCode()==null));
    }
    @Test void maleDisciplineContainsOnlyMen() {
        assertTrue(plan("{\"gender\":\"MALE\"}").stream().allMatch(c->"MALE".equals(c.gender())));
    }
    @Test void femaleDisciplineContainsOnlyWomen() {
        assertTrue(plan("{\"gender\":\"FEMALE\"}").stream().allMatch(c->"FEMALE".equals(c.gender())));
    }
    @Test void openAliasesDoNotRestrictGender() {
        for(String code:List.of("OPEN","ANY","MIXED")) assertEquals(2,
                plan("{\"gender\":\""+code+"\"}").stream().map(Candidate::gender).distinct().count());
    }
    @Test void globalGenderCannotBeBypassedByDiscipline() throws Exception {
        configure("{\"allowedGenders\":[\"MALE\"]}","{}");
        assertThrows(DemoDataException.class,()->plan("{\"gender\":\"FEMALE\"}"));
    }
    @Test void globalAgeIsAppliedWithoutCategories() throws Exception {
        configure("{\"ageMethod\":\"ON_DATE\",\"ageReferenceDate\":\"2026-11-22\",\"minAge\":12,\"maxAge\":15}","{}");
        for(var candidate:plan("{}")) {
            int age=Period.between(candidate.birthDate(),LocalDate.of(2026,11,22)).getYears();
            assertTrue(age>=12&&age<=15);
        }
    }
    @Test void selectedAgeCategoryUsesOnDatePolicy() throws Exception {
        configure("{}",categories(category("JUNIOR","{\"ageMethod\":\"ON_DATE\",\"ageReferenceDate\":\"2026-11-22\",\"minAge\":13,\"maxAge\":15}"),
                category("SENIOR","{\"ageMethod\":\"ON_DATE\",\"ageReferenceDate\":\"2026-11-22\",\"minAge\":18}")));
        for(var candidate:plan("{\"allowedCategoryCodes\":[\"JUNIOR\"]}")) {
            assertEquals("JUNIOR",candidate.categoryCode());
            int age=Period.between(candidate.birthDate(),LocalDate.of(2026,11,22)).getYears();assertTrue(age>=13&&age<=15);
        }
    }
    @Test void selectedAgeCategoryUsesByYearPolicy() throws Exception {
        configure("{}",categories(category("Y15","{\"ageMethod\":\"BY_YEAR\",\"ageReferenceDate\":\"2026-01-01\",\"minAge\":15,\"maxAge\":15}")));
        for(var candidate:plan("{}")) assertEquals(2011,candidate.birthDate().getYear());
    }
    @Test void ageCategoryAndDisciplineGenderAreCombined() throws Exception {
        configure("{}",categories(category("J","{\"ageMethod\":\"BY_YEAR\",\"ageReferenceDate\":\"2026-01-01\",\"minAge\":14,\"maxAge\":14}")));
        for(var candidate:plan("{\"gender\":\"FEMALE\"}")) {
            assertEquals("FEMALE",candidate.gender());assertEquals(2012,candidate.birthDate().getYear());assertEquals("J",candidate.categoryCode());
        }
    }
    @Test void disjointCategoriesAreBothRepresented() throws Exception {
        configure("{}",categories(category("MEN","{\"allowedGenders\":[\"MALE\"]}"),category("WOMEN","{\"allowedGenders\":[\"FEMALE\"]}")));
        assertEquals(List.of("MEN","WOMEN"),plan("{}").stream().map(Candidate::categoryCode).distinct().toList());
    }
    @Test void incompatibleGenderCategoriesAreNotForcedIntoMaleDiscipline() throws Exception {
        configure("{}",categories(category("MEN","{\"allowedGenders\":[\"MALE\"]}"),category("WOMEN","{\"allowedGenders\":[\"FEMALE\"]}")));
        assertTrue(plan("{\"gender\":\"MALE\"}").stream().allMatch(c->"MEN".equals(c.categoryCode())));
    }
    @Test void unknownSelectedCategoryIsRejected() throws Exception {
        configure("{}",categories(category("A","{}")));
        assertThrows(DemoDataException.class,()->plan("{\"allowedCategoryCodes\":[\"MISSING\"]}"));
    }
    @Test void categorySelectionWithoutConfiguredCategoriesIsRejected() {
        assertThrows(DemoDataException.class,()->plan("{\"allowedCategoryCodes\":[\"J\"]}"));
    }
    @Test void fullyAmbiguousCategoriesAreRejected() throws Exception {
        configure("{}",categories(category("A","{}"),category("B","{}")));
        assertThrows(DemoDataException.class,()->plan("{}"));
    }
    @Test void selectionIsAppliedBeforeAmbiguityCheckLikeProductionAdmission() throws Exception {
        configure("{}",categories(category("A","{}"),category("B","{}")));
        assertTrue(plan("{\"allowedCategoryCodes\":[\"A\"]}").stream().allMatch(c->"A".equals(c.categoryCode())));
    }
    @Test void overlappingAgeRangesUseOnlyUniquelyClassifiedDates() throws Exception {
        configure("{}",categories(category("A","{\"ageMethod\":\"BY_YEAR\",\"ageReferenceDate\":\"2026-01-01\",\"minAge\":10,\"maxAge\":15}"),
                category("B","{\"ageMethod\":\"BY_YEAR\",\"ageReferenceDate\":\"2026-01-01\",\"minAge\":14,\"maxAge\":20}")));
        for(var c:plan("{}")) {int age=2026-c.birthDate().getYear();assertTrue("A".equals(c.categoryCode())?age<14:age>15);}
    }
    @Test void leapDayAgeMatchesProductionPeriodCalculation() throws Exception {
        configure("{\"ageMethod\":\"ON_DATE\",\"ageReferenceDate\":\"2021-02-28\",\"minAge\":0,\"maxAge\":0}","{}");
        for(var c:plan("{}")) assertEquals(0,Period.between(c.birthDate(),LocalDate.of(2021,2,28)).getYears());
    }
    @Test void invalidAgeConfigurationIsNotSilentlyIgnored() throws Exception {
        configure("{}",categories(category("BROKEN","{\"minAge\":15}")));
        assertThrows(DemoDataException.class,()->plan("{}"));
    }
    @Test void globalAndCategoryAgeIntersectionMustExist() throws Exception {
        configure("{\"ageMethod\":\"BY_YEAR\",\"ageReferenceDate\":\"2026-01-01\",\"minAge\":18}",
                categories(category("CHILD","{\"ageMethod\":\"BY_YEAR\",\"ageReferenceDate\":\"2026-01-01\",\"maxAge\":12}")));
        assertThrows(DemoDataException.class,()->plan("{}"));
    }
    @Test void cannotClaimCoverageWhenCategoriesOutnumberParticipants() throws Exception {
        configure("{}",categories(category("A","{\"ageMethod\":\"BY_YEAR\",\"ageReferenceDate\":\"2026-01-01\",\"minAge\":20,\"maxAge\":20}"),
                category("B","{\"ageMethod\":\"BY_YEAR\",\"ageReferenceDate\":\"2026-01-01\",\"minAge\":30,\"maxAge\":30}"),
                category("C","{\"ageMethod\":\"BY_YEAR\",\"ageReferenceDate\":\"2026-01-01\",\"minAge\":40,\"maxAge\":40}")));
        assertThrows(DemoDataException.class,()->service.plan(version,"{}",2));
    }
    @Test void unexpectedPipelineIsRejected() {
        pipeline.get(0).setExecutorCode("CUSTOM_ELIGIBILITY");
        assertThrows(DemoDataException.class,()->plan("{}"));
    }
    @Test void malformedDisciplineSettingsAreRejected() {
        assertThrows(DemoDataException.class,()->plan("not-json"));
        assertThrows(DemoDataException.class,()->plan("{\"gender\":17}"));
    }
    @Test void invalidCountIsRejected() {
        for(int count:new int[]{0,1,65}) assertThrows(DemoDataException.class,()->service.plan(version,"{}",count));
    }
}
