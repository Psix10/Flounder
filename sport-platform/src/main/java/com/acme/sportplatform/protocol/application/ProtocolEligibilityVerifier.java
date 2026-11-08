package com.acme.sportplatform.protocol.application;

import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import com.acme.sportplatform.protocol.RegistrationProtocolAccess.ConfirmedRegistrationView;
import com.acme.sportplatform.protocol.application.ProtocolEligibilityException.Violation;

/** Validates captured registration snapshots; does not exclude or mutate registrations. */
public final class ProtocolEligibilityVerifier {
    private final ObjectMapper json;
    public ProtocolEligibilityVerifier(ObjectMapper json){this.json=Objects.requireNonNull(json);}
    public void verify(List<ConfirmedRegistrationView> registrations,ProtocolEligibilityPolicy policy) {
        if(!policy.enabled()) return;
        List<Violation> errors=new ArrayList<>();
        for(var registration:registrations) {
            JsonNode snapshot;
            try {
                snapshot=registration.participantSnapshot()==null?null:json.readTree(registration.participantSnapshot());
                if(snapshot==null || !snapshot.isObject()) throw new IllegalArgumentException();
            } catch(Exception ex) {
                errors.add(new Violation(registration.registrationId(),"participantSnapshot","INVALID_PARTICIPANT_SNAPSHOT","An object snapshot of the participant is required"));
                continue;
            }
            if(policy.checksAge()) checkAge(registration.registrationId(),snapshot,policy,errors);
            if(!policy.allowedGenders().isEmpty()) {
                JsonNode gender=snapshot.path("gender");String reason=null,message=null;
                if(gender.isMissingNode() || gender.isNull()) {reason="MISSING_GENDER";message="Participant gender is required";}
                else if(!gender.isTextual() || !Set.of("MALE","FEMALE").contains(gender.textValue())) {reason="INVALID_GENDER";message="Expected a known MALE/FEMALE gender value";}
                else if(!policy.allowedGenders().contains(gender.textValue())) {reason="GENDER_NOT_ALLOWED";message="Participant gender is not allowed by selected rules";}
                if(reason!=null) errors.add(new Violation(registration.registrationId(),"participantSnapshot.gender",reason,message));
            }
        }
        if(!errors.isEmpty()) throw new ProtocolEligibilityException(errors);
    }
    private void checkAge(UUID id,JsonNode snapshot,ProtocolEligibilityPolicy policy,List<Violation> errors) {
        JsonNode node=snapshot.path("birthDate");LocalDate birth;
        if(node.isMissingNode() || node.isNull()) {errors.add(new Violation(id,"participantSnapshot.birthDate","MISSING_BIRTH_DATE","Participant birth date is required"));return;}
        try {
            if(!node.isTextual()) throw new IllegalArgumentException();
            birth=LocalDate.parse(node.textValue());
        } catch(IllegalArgumentException | DateTimeException ex) {
            errors.add(new Violation(id,"participantSnapshot.birthDate","INVALID_BIRTH_DATE","Expected an ISO local date"));return;
        }
        if(birth.isAfter(policy.referenceDate())) {
            errors.add(new Violation(id,"participantSnapshot.birthDate","BIRTH_DATE_AFTER_REFERENCE_DATE","Birth date is after the configured reference date"));return;
        }
        int age=policy.ageMethod()==ProtocolEligibilityPolicy.AgeMethod.BY_YEAR
                ?policy.referenceDate().getYear()-birth.getYear():Period.between(birth,policy.referenceDate()).getYears();
        if((policy.minAge()!=null && age<policy.minAge()) || (policy.maxAge()!=null && age>policy.maxAge()))
            errors.add(new Violation(id,"participantSnapshot.birthDate","AGE_OUT_OF_RANGE","Computed age "+age+" is outside the configured inclusive bounds"));
    }
}
