package com.acme.sportplatform.competition;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.acme.sportplatform.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
class DisciplineSettingsPolicyTest {
    private final ObjectMapper json = new ObjectMapper();
    @Test void explicitMetadataDoesNotEraseOtherTemplateSettings() throws Exception {
        var result=DisciplineSettingsPolicy.mergeAndValidate("{\"distanceMeters\":50,\"poolLengthMeters\":25,\"protocol\":{\"unitCapacity\":4}}",json.readTree("{\"stroke\":\"FREESTYLE\",\"gender\":\"MALE\"}"));
        assertEquals(50,result.path("distanceMeters").asInt());assertEquals(25,result.path("poolLengthMeters").asInt());assertEquals(4,result.path("protocol").path("unitCapacity").asInt());
    }
    @Test void overridesReplaceOnlySelectedTopLevelFields() throws Exception {
        var result=DisciplineSettingsPolicy.mergeAndValidate("{\"distanceMeters\":50,\"gender\":\"MALE\"}",json.readTree("{\"distanceMeters\":100,\"gender\":\"OPEN\"}"));
        assertEquals(100,result.path("distanceMeters").asInt());assertNull(DisciplineSettingsPolicy.gender(result));
    }
    @Test void emptyLegacySettingsRemainUsable() { assertTrue(DisciplineSettingsPolicy.mergeAndValidate("{}",null).isObject()); }
    @Test void arrayOverrideIsRejected() throws Exception {var override=json.readTree("[]");assertThrows(BusinessException.class,()->DisciplineSettingsPolicy.mergeAndValidate("{}",override));}
    @Test void invalidPositiveIntegerIsRejected() {assertThrows(BusinessException.class,()->DisciplineSettingsPolicy.validate(DisciplineSettingsPolicy.read("{\"distanceMeters\":-50}")));}
    @Test void duplicateCategoryCodesAreRejected() {assertThrows(BusinessException.class,()->DisciplineSettingsPolicy.validate(DisciplineSettingsPolicy.read("{\"allowedCategoryCodes\":[\"M\",\"M\"]}")));}
    @Test void emptyCategoryArrayIsNotSilentlyTreatedAsUnrestricted() {assertThrows(BusinessException.class,()->DisciplineSettingsPolicy.validate(DisciplineSettingsPolicy.read("{\"allowedCategoryCodes\":[]}")));}
    @Test void unknownGenderIsRejected() {assertThrows(BusinessException.class,()->DisciplineSettingsPolicy.validate(DisciplineSettingsPolicy.read("{\"gender\":\"BOYS\"}")));}
}
