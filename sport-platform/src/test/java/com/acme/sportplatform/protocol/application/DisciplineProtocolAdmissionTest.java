package com.acme.sportplatform.protocol.application;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.protocol.RegistrationProtocolAccess.ConfirmedRegistrationView;
import com.acme.sportplatform.protocol.api.RuleBlockRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

class DisciplineProtocolAdmissionTest {
    private final ObjectMapper json = new ObjectMapper();
    private final UUID version = UUID.randomUUID();
    private ConfirmedRegistrationView person(String gender, String birth) {
        var row=mock(ConfirmedRegistrationView.class);
        when(row.registrationId()).thenReturn(UUID.randomUUID());
        when(row.participantSnapshot()).thenReturn("{\"gender\":\""+gender+"\",\"birthDate\":\""+birth+"\"}");
        return row;
    }
    private ResolvedProtocolRules rules() throws Exception {
        var group=json.readTree("{\"capacity\":4,\"unitLabelPrefix\":\"Heat\",\"categories\":[{\"code\":\"M_2014_2016\",\"seedingGroupCode\":\"M_2014_2016\",\"eligibility\":{\"allowedGenders\":[\"MALE\"],\"ageMethod\":\"BY_YEAR\",\"ageReferenceDate\":\"2026-11-16\",\"minAge\":10,\"maxAge\":12}},{\"code\":\"F_2014_2016\",\"seedingGroupCode\":\"F_2014_2016\",\"eligibility\":{\"allowedGenders\":[\"FEMALE\"],\"ageMethod\":\"BY_YEAR\",\"ageReferenceDate\":\"2026-11-16\",\"minAge\":10,\"maxAge\":12}}]}");
        var blocks=List.of(new RuleBlockRequest(0,"ELIGIBILITY_CONFIRMED_ONLY",json.createObjectNode()),new RuleBlockRequest(1,"SEED_BY_METRIC_ASC_NULLS_LAST",json.createObjectNode()),new RuleBlockRequest(2,"GROUP_BY_CAPACITY",group),new RuleBlockRequest(3,"ASSIGN_CENTER_OUT",json.createObjectNode()));
        return new ResolvedProtocolRules(version,4,"Heat","seedTimeSeconds",List.of(2,3,1,4),List.of(1,2,3,4),"SLOWEST_FIRST","FIRST",blocks);
    }
    @Test void legacyMetadataDoesNotInventRestrictions() throws Exception {
        var rules=rules();assertSame(rules,DisciplineProtocolAdmission.apply("{}",rules,List.of(),json,version));
    }
    @Test void disciplineGenderBlocksAnOtherwiseMatchingEventCategory() throws Exception {
        var rules=rules();var female=person("FEMALE","2014-01-01");
        assertThrows(ProtocolEligibilityException.class,()->DisciplineProtocolAdmission.apply("{\"gender\":\"MALE\"}",rules,List.of(female),json,version));
    }
    @Test void selectedCategoriesNarrowRulesWithoutMutatingPublishedConfiguration() throws Exception {
        var rules=rules();var male=person("MALE","2014-12-31");var before=rules.blocks().get(2).config().deepCopy();
        var narrowed=DisciplineProtocolAdmission.apply("{\"gender\":\"MALE\",\"allowedCategoryCodes\":[\"M_2014_2016\"]}",rules,List.of(male),json,version);
        assertEquals(1,narrowed.blocks().get(2).config().path("categories").size());assertEquals(before,rules.blocks().get(2).config());
        assertEquals("M_2014_2016",narrowed.blocks().get(2).config().path("categories").get(0).path("code").asText());
    }
    @Test void unknownCategoryCodeIsRejected() throws Exception {
        var rules=rules();assertThrows(BusinessException.class,()->DisciplineProtocolAdmission.apply("{\"allowedCategoryCodes\":[\"UNKNOWN\"]}",rules,List.of(),json,version));
    }
    @Test void participantOutsideSelectedAgeCategoryIsNotSilentlyDropped() throws Exception {
        var rules=rules();var adult=person("MALE","2000-01-01");
        assertThrows(ProtocolCategoryException.class,()->DisciplineProtocolAdmission.apply("{\"allowedCategoryCodes\":[\"M_2014_2016\"]}",rules,List.of(adult),json,version));
    }
    @Test void missingGenderIsRejectedWhenDisciplineRestrictsSex() throws Exception {
        var rules=rules();var row=mock(ConfirmedRegistrationView.class);when(row.registrationId()).thenReturn(UUID.randomUUID());when(row.participantSnapshot()).thenReturn("{}");
        assertThrows(ProtocolEligibilityException.class,()->DisciplineProtocolAdmission.apply("{\"gender\":\"MALE\"}",rules,List.of(row),json,version));
    }
    @Test void snapshotMetadataIsAnIndependentCopy() {
        var first=DisciplineProtocolAdmission.snapshotSettings("{\"distanceMeters\":50}");
        ((com.fasterxml.jackson.databind.node.ObjectNode)first).put("distanceMeters",100);
        assertEquals(50,DisciplineProtocolAdmission.snapshotSettings("{\"distanceMeters\":50}").path("distanceMeters").asInt());
    }
}
