package com.acme.sportplatform.protocol.application;

import java.time.LocalDate;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.acme.sportplatform.common.exception.BusinessException;

/** Configuration of individual-start age/sex eligibility, not category grouping. */
public final class ProtocolEligibilityPolicy {
    public enum AgeMethod { ON_DATE, BY_YEAR }
    private final AgeMethod ageMethod;
    private final LocalDate referenceDate;
    private final Integer minAge,maxAge;
    private final Set<String> allowedGenders;
    private ProtocolEligibilityPolicy(AgeMethod method,LocalDate date,Integer min,Integer max,Set<String> genders) {
        ageMethod=method;referenceDate=date;minAge=min;maxAge=max;allowedGenders=Set.copyOf(genders);
    }
    public AgeMethod ageMethod(){return ageMethod;}
    public LocalDate referenceDate(){return referenceDate;}
    public Integer minAge(){return minAge;}
    public Integer maxAge(){return maxAge;}
    public Set<String> allowedGenders(){return allowedGenders;}
    public boolean checksAge(){return ageMethod!=null;}
    public boolean enabled(){return checksAge() || !allowedGenders.isEmpty();}
    public static ProtocolEligibilityPolicy parse(JsonNode config) {
        if(config==null || !config.isObject()) throw invalid("Eligibility config must be an object");
        Set<String> keys=Set.of("ageMethod","ageReferenceDate","minAge","maxAge","allowedGenders");
        config.fieldNames().forEachRemaining(k->{if(!keys.contains(k)) throw invalid("Unknown eligibility parameter: "+k);});
        boolean age=config.has("ageMethod") || config.has("ageReferenceDate") || config.has("minAge") || config.has("maxAge");
        AgeMethod method=null;LocalDate date=null;Integer min=null,max=null;
        if(age) {
            if(!config.path("ageMethod").isTextual() || !config.path("ageReferenceDate").isTextual()
                    || (!config.has("minAge") && !config.has("maxAge"))) throw invalid("Age method, reference date and at least one age bound are required");
            try { method=AgeMethod.valueOf(config.get("ageMethod").textValue());date=LocalDate.parse(config.get("ageReferenceDate").textValue()); }
            catch(IllegalArgumentException | java.time.DateTimeException ex) { throw invalid("Invalid ageMethod or ageReferenceDate"); }
            min=bound(config,"minAge");max=bound(config,"maxAge");
            if(min!=null && max!=null && min>max) throw invalid("minAge must not exceed maxAge");
        }
        Set<String> genders=new LinkedHashSet<>();
        if(config.has("allowedGenders")) {
            JsonNode node=config.get("allowedGenders");
            if(!node.isArray() || node.isEmpty()) throw invalid("allowedGenders must be a non-empty array");
            for(JsonNode value:node) {
                if(!value.isTextual() || !Set.of("MALE","FEMALE").contains(value.textValue()) || !genders.add(value.textValue()))
                    throw invalid("allowedGenders must contain unique MALE/FEMALE values");
            }
        }
        return new ProtocolEligibilityPolicy(method,date,min,max,genders);
    }
    private static Integer bound(JsonNode config,String key) {
        if(!config.has(key)) return null;
        JsonNode node=config.get(key);
        if(!node.isIntegralNumber() || !node.canConvertToInt() || node.intValue()<0) throw invalid(key+" must be a nonnegative integer");
        return node.intValue();
    }
    private static BusinessException invalid(String message){return new BusinessException("protocol.invalid_rule_config",message);}
}
