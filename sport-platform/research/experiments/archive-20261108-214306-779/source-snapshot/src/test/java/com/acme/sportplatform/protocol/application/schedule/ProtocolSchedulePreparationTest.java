package com.acme.sportplatform.protocol.application.schedule;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.acme.sportplatform.protocol.RegistrationProtocolAccess.ConfirmedRegistrationView;
import com.acme.sportplatform.protocol.application.GeneratedAssignment;
import com.acme.sportplatform.protocol.application.GeneratedCompetitionUnit;
import com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleValidator;
import static com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.*;
import static org.junit.jupiter.api.Assertions.*;
import com.acme.sportplatform.protocol.application.schedule.ProtocolScheduleInputAdapter.DisciplinePlan;

class ProtocolSchedulePreparationTest {
    private static UUID id(long n) { return new UUID(0,n); }
    private static final UUID SESSION=id(1), A=id(100), B=id(101), D1=id(200), D2=id(201), R1=id(300), R2=id(301);
    private static final Instant T=Instant.parse("2026-11-22T06:30:00Z");
    private TimingPolicy policy() { return new TimingPolicy(Duration.ofSeconds(600),Duration.ofSeconds(30),Origin.EXPERIMENTAL,"test only"); }
    private List<Session> sessions(long seconds) { return List.of(new Session(SESSION,new Window(T,T.plusSeconds(seconds)),List.of())); }
    private ConfirmedRegistrationView registration(UUID registration, UUID profile) { return new ConfirmedRegistrationView(registration,"{}",profile); }
    private GeneratedCompetitionUnit unit(long heat, int sequence, UUID... registrations) {
        List<GeneratedAssignment> assignments=new ArrayList<>(); int lane=1;
        for(UUID registration:registrations) assignments.add(new GeneratedAssignment(registration,lane++,null));
        return new GeneratedCompetitionUnit(id(heat),"Heat "+sequence,sequence,assignments);
    }
    private DisciplinePlan plan(UUID discipline, GeneratedCompetitionUnit unit, ConfirmedRegistrationView... registrations) {
        return new DisciplinePlan(discipline,SESSION,List.of(unit),List.of(registrations),Duration.ofSeconds(120),"explicit test duration");
    }
    private Input adapt(DisciplinePlan... plans) {
        return new ProtocolScheduleInputAdapter().adapt(policy(),sessions(3600),List.of(plans));
    }
    @Test void translatesRegistrationToProfileWithoutChangingUnitId() {
        var input=adapt(plan(D1,unit(10,1,R1),registration(R1,A)));
        assertEquals(id(10),input.heats().get(0).id()); assertEquals(D1,input.heats().get(0).disciplineId());
        assertEquals(List.of(A),input.heats().get(0).athleteIds()); assertFalse(input.heats().get(0).athleteIds().contains(R1));
    }
    @Test void sameProfileAcrossDisciplinesReceivesRest() {
        var result=new PrepareProtocolSchedule().execute(policy(),sessions(3600),List.of(
                plan(D1,unit(10,1,R1),registration(R1,A)),plan(D2,unit(11,1,R2),registration(R2,A))));
        assertEquals(Status.SCHEDULED,result.result().status());
        assertEquals(T.plusSeconds(720),result.result().heats().get(1).start());
        assertTrue(new ProtocolScheduleValidator().validate(result.input(),result.result()).isEmpty());
    }
    @Test void distinctProfilesNeedOnlyPoolChangeover() {
        var result=new PrepareProtocolSchedule().execute(policy(),sessions(3600),List.of(
                plan(D1,unit(10,1,R1),registration(R1,A)),plan(D2,unit(11,1,R2),registration(R2,B))));
        assertEquals(T.plusSeconds(150),result.result().heats().get(1).start());
    }
    @Test void missingProfileFailsBeforeScheduling() {
        var legacy=new ConfirmedRegistrationView(R1,"{}");
        assertThrows(IllegalStateException.class,()->adapt(plan(D1,unit(10,1,R1),legacy)));
    }
    @Test void unknownRegistrationIsRejected() {
        assertThrows(IllegalArgumentException.class,()->adapt(plan(D1,unit(10,1,R2),registration(R1,A))));
    }
    @Test void duplicateRegistrationInHeatIsRejected() {
        assertThrows(IllegalArgumentException.class,()->adapt(plan(D1,unit(10,1,R1,R1),registration(R1,A))));
    }
    @Test void duplicateRegistrationAcrossHeatsIsRejected() {
        var p=new DisciplinePlan(D1,SESSION,List.of(unit(10,1,R1),unit(11,2,R1)),List.of(registration(R1,A)),Duration.ofSeconds(120),"test");
        assertThrows(IllegalArgumentException.class,()->adapt(p));
    }
    @Test void unassignedConfirmedRegistrationIsRejected() {
        assertThrows(IllegalArgumentException.class,()->adapt(plan(D1,unit(10,1,R1),registration(R1,A),registration(R2,B))));
    }
    @Test void duplicateConfirmedRegistrationIsRejected() {
        assertThrows(IllegalArgumentException.class,()->adapt(plan(D1,unit(10,1,R1),registration(R1,A),registration(R1,A))));
    }
    @Test void twoRegistrationsOfSameProfileInOneHeatAreRejected() {
        assertThrows(IllegalArgumentException.class,()->adapt(plan(D1,unit(10,1,R1,R2),registration(R1,A),registration(R2,A))));
    }
    @Test void duplicateHeatIdsAcrossDisciplinesAreRejected() {
        assertThrows(IllegalArgumentException.class,()->adapt(plan(D1,unit(10,1,R1),registration(R1,A)),plan(D2,unit(10,1,R2),registration(R2,B))));
    }
    @Test void nonAscendingUnitSequenceIsRejected() {
        var p=new DisciplinePlan(D1,SESSION,List.of(unit(10,2,R1),unit(11,1,R2)),List.of(registration(R1,A),registration(R2,B)),Duration.ofSeconds(120),"test");
        assertThrows(IllegalArgumentException.class,()->adapt(p));
    }
    @Test void unknownSessionIsRejected() {
        var p=new DisciplinePlan(D1,id(999),List.of(unit(10,1,R1)),List.of(registration(R1,A)),Duration.ofSeconds(120),"test");
        assertThrows(IllegalArgumentException.class,()->adapt(p));
    }
    @Test void failureKeepsEmptyPlanAndOriginalAssignments() {
        var first=unit(10,1,R1); var second=unit(11,1,R2);
        var result=new PrepareProtocolSchedule().execute(policy(),sessions(300),List.of(
                plan(D1,first,registration(R1,A)),plan(D2,second,registration(R2,A))));
        assertEquals(Status.NOT_FIT_FIXED_ORDER,result.result().status()); assertTrue(result.result().heats().isEmpty());
        assertEquals(id(11),result.result().failure().heatId());
        assertEquals(R1,first.assignments().get(0).registrationId()); assertEquals(R2,second.assignments().get(0).registrationId());
    }
    @Test void seedMetricDoesNotBecomeAnImplicitDuration() {
        var seeded=new GeneratedCompetitionUnit(id(10),"Heat 1",1,List.of(new GeneratedAssignment(R1,1,new BigDecimal("29.25"))));
        var input=adapt(plan(D1,seeded,registration(R1,A)));
        assertEquals(Duration.ofSeconds(120),input.heats().get(0).duration());
        assertEquals("explicit test duration",input.heats().get(0).durationBasis());
    }
    @Test void requiresExplicitDurationBasis() {
        assertThrows(IllegalArgumentException.class,()->new DisciplinePlan(D1,SESSION,List.of(unit(10,1,R1)),List.of(registration(R1,A)),Duration.ofSeconds(120),""));
    }
    @Test void duplicateDisciplinePlansAreRejected() {
        assertThrows(IllegalArgumentException.class,()->adapt(plan(D1,unit(10,1,R1),registration(R1,A)),plan(D1,unit(11,1,R2),registration(R2,B))));
    }
    @Test void emptyProgramIsRejected() {
        assertThrows(IllegalArgumentException.class,()->adapt());
    }
}
