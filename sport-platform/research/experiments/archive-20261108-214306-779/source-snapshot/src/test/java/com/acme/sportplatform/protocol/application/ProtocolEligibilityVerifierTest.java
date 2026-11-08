package com.acme.sportplatform.protocol.application;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.*;
import com.acme.sportplatform.protocol.RegistrationProtocolAccess.ConfirmedRegistrationView;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ProtocolEligibilityVerifierTest {
    private final ObjectMapper json=new ObjectMapper();private final ProtocolEligibilityVerifier verifier=new ProtocolEligibilityVerifier(json);
    private final UUID id=UUID.randomUUID();
    private ConfirmedRegistrationView reg(String snapshot){return new ConfirmedRegistrationView(id,"{}",UUID.randomUUID(),snapshot);}
    private ProtocolEligibilityPolicy age(String method,int min,int max) throws Exception {
        return ProtocolEligibilityPolicy.parse(json.readTree("{\"ageMethod\":\""+method+"\",\"ageReferenceDate\":\"2026-11-22\",\"minAge\":"+min+",\"maxAge\":"+max+"}"));
    }
    private String person(String birth,String gender){return "{\"birthDate\":\""+birth+"\",\"gender\":\""+gender+"\"}";}
    private void rejects(String snapshot,ProtocolEligibilityPolicy policy,String reason) {
        var ex=assertThrows(ProtocolEligibilityException.class,()->verifier.verify(List.of(reg(snapshot)),policy));
        assertEquals("protocol.eligibility_violation",ex.getCode());assertTrue(ex.violations().stream().anyMatch(v->v.reason().equals(reason)));
        assertEquals(id,ex.violations().get(0).registrationId());
    }
    @Test void acceptsExactMinimumBirthday() throws Exception {verifier.verify(List.of(reg(person("2008-11-22","MALE"))),age("ON_DATE",18,25));}
    @Test void rejectsDayBeforeMinimumBirthday() throws Exception {rejects(person("2008-11-23","MALE"),age("ON_DATE",18,25),"AGE_OUT_OF_RANGE");}
    @Test void byYearAndOnDateAreDifferentPolicies() throws Exception {
        var r=reg(person("2008-12-31","MALE"));verifier.verify(List.of(r),age("BY_YEAR",18,25));
        assertThrows(ProtocolEligibilityException.class,()->verifier.verify(List.of(r),age("ON_DATE",18,25)));
    }
    @Test void acceptsMaximumAgeInclusively() throws Exception {verifier.verify(List.of(reg(person("2000-11-23","MALE"))),age("ON_DATE",18,25));}
    @Test void rejectsAboveMaximum() throws Exception {rejects(person("2000-11-22","MALE"),age("ON_DATE",18,25),"AGE_OUT_OF_RANGE");}
    @Test void rejectsFutureBirthDate() throws Exception {rejects(person("2026-11-23","MALE"),age("BY_YEAR",0,10),"BIRTH_DATE_AFTER_REFERENCE_DATE");}
    @Test void rejectsMissingBirthDate() throws Exception {rejects("{}",age("ON_DATE",8,11),"MISSING_BIRTH_DATE");}
    @Test void rejectsImpossibleBirthDate() throws Exception {rejects(person("2014-02-30","MALE"),age("ON_DATE",8,18),"INVALID_BIRTH_DATE");}
    @Test void rejectsNonTextualBirthDate() throws Exception {rejects("{\"birthDate\":2014}",age("ON_DATE",8,18),"INVALID_BIRTH_DATE");}
    @Test void acceptsLeapDayBirthDate() throws Exception {verifier.verify(List.of(reg(person("2004-02-29","MALE"))),age("ON_DATE",22,22));}
    @Test void rejectsDisallowedGender() throws Exception {
        var p=ProtocolEligibilityPolicy.parse(json.readTree("{\"allowedGenders\":[\"MALE\"]}"));rejects(person("2004-05-10","FEMALE"),p,"GENDER_NOT_ALLOWED");
    }
    @Test void rejectsUnknownGender() throws Exception {
        var p=ProtocolEligibilityPolicy.parse(json.readTree("{\"allowedGenders\":[\"MALE\"]}"));rejects(person("2004-05-10","unknown"),p,"INVALID_GENDER");
    }
    @Test void genderOnlyDoesNotRequireBirthDate() throws Exception {
        var p=ProtocolEligibilityPolicy.parse(json.readTree("{\"allowedGenders\":[\"FEMALE\"]}"));verifier.verify(List.of(reg("{\"gender\":\"FEMALE\"}")),p);
    }
    @Test void rejectsMalformedSnapshot() throws Exception {rejects("{broken",age("ON_DATE",8,18),"INVALID_PARTICIPANT_SNAPSHOT");}
    @Test void rejectsMissingSnapshotInStrictMode() throws Exception {rejects(null,age("ON_DATE",8,18),"INVALID_PARTICIPANT_SNAPSHOT");}
    @Test void legacyEmptyPolicyPreservesOldConstructorBehaviour() throws Exception {
        verifier.verify(List.of(new ConfirmedRegistrationView(id,"{}")),ProtocolEligibilityPolicy.parse(json.readTree("{}")));
    }
    @Test void collectsMultipleViolationsWithoutRemovingRegistrations() throws Exception {
        var registrations=List.of(reg("{}"),new ConfirmedRegistrationView(UUID.randomUUID(),"{}",UUID.randomUUID(),"{}"));
        var policy=ProtocolEligibilityPolicy.parse(json.readTree("{\"ageMethod\":\"BY_YEAR\",\"ageReferenceDate\":\"2026-11-22\",\"minAge\":8,\"allowedGenders\":[\"MALE\"]}"));
        var ex=assertThrows(ProtocolEligibilityException.class,()->verifier.verify(registrations,policy));
        assertEquals(4,ex.violations().size());assertEquals(2,registrations.size());
    }
    @Test void preparationRejectsBeforeAnyRuleExecutor() throws Exception {
        var registry=mock(RuleExecutorRegistry.class);
        var rules=new ResolvedProtocolRules(UUID.randomUUID(),2,"Heat","seedTimeSeconds",List.of(2,1),List.of(1,2),"FASTEST_FIRST","LAST",
            List.of(new com.acme.sportplatform.protocol.api.RuleBlockRequest(0,"ELIGIBILITY_CONFIRMED_ONLY",json.readTree("{\"allowedGenders\":[\"MALE\"]}"))));
        assertThrows(ProtocolEligibilityException.class,()->new PrepareStartProtocol(registry,json).prepare(UUID.randomUUID(),rules,List.of(reg(person("2004-05-10","FEMALE")))));
        verifyNoInteractions(registry);
    }
}
