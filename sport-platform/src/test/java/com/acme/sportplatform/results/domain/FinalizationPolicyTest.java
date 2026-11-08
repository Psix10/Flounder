package com.acme.sportplatform.results.domain;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.acme.sportplatform.results.api.ConfirmFinalStandingsRequest;
import com.acme.sportplatform.common.exception.BusinessException;
class FinalizationPolicyTest {
    @Test void requiresExplicitProgrammeConfirmation() {
        for(Boolean value:Arrays.asList(null,false)) assertThrows(BusinessException.class,
                ()->FinalizationPolicy.confirmationNote(new ConfirmFinalStandingsRequest(UUID.randomUUID(),value,null)));
    }
    @Test void requiresRequestId() {
        assertThrows(BusinessException.class,()->FinalizationPolicy.confirmationNote(new ConfirmFinalStandingsRequest(null,true,null)));
        assertThrows(BusinessException.class,()->FinalizationPolicy.confirmationNote(null));
    }
    @Test void normalizesOptionalNote() {
        assertEquals("",FinalizationPolicy.confirmationNote(new ConfirmFinalStandingsRequest(UUID.randomUUID(),true,null)));
        assertEquals("checked",FinalizationPolicy.confirmationNote(new ConfirmFinalStandingsRequest(UUID.randomUUID(),true," checked ")));
    }
    @Test void limitsNoteLength() {
        assertThrows(BusinessException.class,()->FinalizationPolicy.confirmationNote(new ConfirmFinalStandingsRequest(UUID.randomUUID(),true,"x".repeat(501))));
    }
    @Test void requiresNonblankRevocationReason() {
        for(String value:Arrays.asList(null,"","  ")) assertThrows(BusinessException.class,()->FinalizationPolicy.revocationReason(value));
    }
    @Test void normalizesAndLimitsRevocationReason() {
        assertEquals("Correction",FinalizationPolicy.revocationReason(" Correction "));
        assertThrows(BusinessException.class,()->FinalizationPolicy.revocationReason("x".repeat(1001)));
    }
    @Test void rejectsEmptyAndUnpublishedProgramme() {
        assertThrows(BusinessException.class,()->FinalizationPolicy.requirePublished(List.of()));
        assertThrows(BusinessException.class,()->FinalizationPolicy.requirePublished(List.of("PUBLISHED","DRAFT")));
    }
    @Test void acceptsOnlyPublishedProgramme() { assertDoesNotThrow(()->FinalizationPolicy.requirePublished(List.of("PUBLISHED","PUBLISHED"))); }
}
