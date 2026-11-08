package com.acme.sportplatform.integration.protocol;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.acme.sportplatform.integration.protocol.SessionProtocolExperimentReport.*;
class SessionProtocolExperimentReportTest {
    @Test void smokeRunDoesNotClaimCriterionConfirmed() {
        var summary=summarize("baseline",List.of(new Measurement("baseline",1,1_000_000L,true,"")));
        assertEquals("EXPLORATORY",summary.assessment());assertEquals(1.0,summary.qualifiedShare());
    }
    @Test void nearestRankP95ForThirtyUsesTwentyNinthObservation() {
        List<Measurement> rows=new ArrayList<>();
        for(int i=1;i<=30;i++) rows.add(new Measurement("baseline",i,i*1_000_000L,true,""));
        var summary=summarize("baseline",rows);assertEquals(29.0,summary.p95Ms());assertEquals("MET",summary.assessment());
    }
    @Test void failedValidationCannotCountAsFastCorrectRun() {
        List<Measurement> rows=new ArrayList<>();
        for(int i=1;i<=30;i++) rows.add(new Measurement("baseline",i,1_000_000L,i>2,i<=2?"invalid":""));
        var summary=summarize("baseline",rows);assertEquals(28,summary.correctWithin5s());assertEquals("NOT_MET",summary.assessment());
    }
    @Test void twentyNineOfThirtyMeetsNinetyFivePercentThreshold() {
        List<Measurement> rows=new ArrayList<>();
        for(int i=1;i<=30;i++) rows.add(new Measurement("baseline",i,i==30?6_000_000_000L:1_000_000_000L,true,""));
        var summary=summarize("baseline",rows);assertEquals(29,summary.correctWithin5s());assertEquals("MET",summary.assessment());
    }
}
