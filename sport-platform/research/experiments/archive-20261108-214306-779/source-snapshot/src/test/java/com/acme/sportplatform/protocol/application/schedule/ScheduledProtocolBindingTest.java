package com.acme.sportplatform.protocol.application.schedule;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.acme.sportplatform.protocol.CompetitionUnitProtocolAccess.*;
import com.acme.sportplatform.protocol.application.*;
import com.acme.sportplatform.protocol.application.schedule.PrepareProtocolSchedule.PreparedSchedule;
import static com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.*;
import static org.junit.jupiter.api.Assertions.*;

class ScheduledProtocolBindingTest {
    private static UUID id(long n){return new UUID(0,n);}
    private final Instant t=Instant.parse("2026-11-22T06:30:00Z");
    private final ScheduledProtocolBinding binding=new ScheduledProtocolBinding();
    private PreparedStartProtocol prepared() {
        return new PreparedStartProtocol(id(200),List.of(new SeedParticipant(id(1),null),new SeedParticipant(id(2),null)),List.of(
                new GeneratedCompetitionUnit(id(10),"Heat 1",1,List.of(new GeneratedAssignment(id(1),2,null))),
                new GeneratedCompetitionUnit(id(11),"Heat 2",2,List.of(new GeneratedAssignment(id(2),2,null)))));
    }
    private PreparedSchedule schedule() {
        var input=new Input(new TimingPolicy(Duration.ZERO,Duration.ofSeconds(30),Origin.EXPERIMENTAL,"test"),
                List.of(new Session(id(300),new Window(t,t.plusSeconds(600)),List.of())),List.of(
                new Heat(id(10),id(300),id(200),List.of(id(101)),Duration.ofSeconds(120),"test"),
                new Heat(id(11),id(300),id(200),List.of(id(102)),Duration.ofSeconds(120),"test")));
        return new PreparedSchedule(input,new com.acme.sportplatform.protocol.domain.schedule.FixedOrderProtocolScheduler().schedule(input));
    }
    private SavedCompetitionUnitView saved(long actual,int sequence,OffsetDateTime time,List<AssignmentView> assignments) {
        return new SavedCompetitionUnitView(id(actual),"Heat "+sequence,sequence,"DRAFT",assignments,time);
    }
    private List<SavedCompetitionUnitView> saved() {
        return List.of(saved(20,1,t.atOffset(ZoneOffset.UTC),List.of(new AssignmentView(id(1),2))),
                saved(21,2,t.plusSeconds(150).atOffset(ZoneOffset.UTC),List.of(new AssignmentView(id(2),2))));
    }
    @Test void createsTimedDraftsWithoutReplacingPreparedIds() {
        var p=prepared();var drafts=binding.toTimedDrafts(p,schedule());
        assertEquals(t,drafts.get(0).scheduledAt().toInstant());assertEquals(t.plusSeconds(150),drafts.get(1).scheduledAt().toInstant());
        assertEquals(id(10),p.units().get(0).competitionUnitId());assertEquals(id(1),drafts.get(0).assignments().get(0).registrationId());
    }
    @Test void mapsTemporaryToSavedIdAndRetainsPlannedEnd() {
        var result=binding.bindSavedUnits(prepared(),schedule(),saved());
        assertEquals(id(10),result.get(0).temporaryHeatId());assertEquals(id(20),result.get(0).savedHeatId());
        assertEquals(t.plusSeconds(120),result.get(0).end());assertEquals(id(300),result.get(0).sessionId());
    }
    @Test void bindsBySequenceNotReturnedListOrder() {
        var list=saved();var result=binding.bindSavedUnits(prepared(),schedule(),List.of(list.get(1),list.get(0)));
        assertEquals(id(20),result.get(0).savedHeatId());assertEquals(id(21),result.get(1).savedHeatId());
    }
    @Test void acceptsSameInstantWithDifferentUtcOffset() {
        var list=saved();var first=saved(20,1,t.atOffset(ZoneOffset.ofHours(3)),list.get(0).assignments());
        assertEquals(2,binding.bindSavedUnits(prepared(),schedule(),List.of(first,list.get(1))).size());
    }
    @Test void rejectsMissingSavedHeat() {
        assertThrows(IllegalArgumentException.class,()->binding.bindSavedUnits(prepared(),schedule(),List.of(saved().get(0))));
    }
    @Test void rejectsDuplicateSavedIds() {
        var list=saved();var second=saved(20,2,list.get(1).scheduledAt(),list.get(1).assignments());
        assertThrows(IllegalArgumentException.class,()->binding.bindSavedUnits(prepared(),schedule(),List.of(list.get(0),second)));
    }
    @Test void rejectsDuplicateSequence() {
        assertThrows(IllegalArgumentException.class,()->binding.bindSavedUnits(prepared(),schedule(),List.of(saved().get(0),saved().get(0))));
    }
    @Test void rejectsChangedTime() {
        var list=saved();var first=saved(20,1,t.plusSeconds(1).atOffset(ZoneOffset.UTC),list.get(0).assignments());
        assertThrows(IllegalArgumentException.class,()->binding.bindSavedUnits(prepared(),schedule(),List.of(first,list.get(1))));
    }
    @Test void rejectsMissingTime() {
        var list=saved();var first=saved(20,1,null,list.get(0).assignments());
        assertThrows(IllegalArgumentException.class,()->binding.bindSavedUnits(prepared(),schedule(),List.of(first,list.get(1))));
    }
    @Test void rejectsChangedAssignment() {
        var list=saved();var first=saved(20,1,list.get(0).scheduledAt(),List.of(new AssignmentView(id(999),2)));
        assertThrows(IllegalArgumentException.class,()->binding.bindSavedUnits(prepared(),schedule(),List.of(first,list.get(1))));
    }
    @Test void rejectsChangedLane() {
        var list=saved();var first=saved(20,1,list.get(0).scheduledAt(),List.of(new AssignmentView(id(1),1)));
        assertThrows(IllegalArgumentException.class,()->binding.bindSavedUnits(prepared(),schedule(),List.of(first,list.get(1))));
    }
    @Test void rejectsUnrelatedPreparedHeatIds() {
        var p=new PreparedStartProtocol(id(200),List.of(new SeedParticipant(id(1),null)),List.of(
                new GeneratedCompetitionUnit(id(999),"Heat 1",1,List.of(new GeneratedAssignment(id(1),2,null)))));
        assertThrows(IllegalArgumentException.class,()->binding.toTimedDrafts(p,schedule()));
    }
    @Test void rejectsFailedSchedule() {
        var s=schedule();var result=new Result(Status.NOT_FIT_FIXED_ORDER,Origin.EXPERIMENTAL,"test",List.of(),
                new Failure("SESSION_WINDOW_EXCEEDED",id(10),id(300),t,t.plusSeconds(120),t.plusSeconds(60)));
        assertThrows(IllegalArgumentException.class,()->binding.toTimedDrafts(prepared(),new PreparedSchedule(s.input(),result)));
    }
    @Test void rejectsTamperedScheduleDuration() {
        var s=schedule();var bad=new Result(Status.SCHEDULED,Origin.EXPERIMENTAL,"test",List.of(
                new ScheduledHeat(id(10),id(300),id(200),t,t.plusSeconds(1)),s.result().heats().get(1)),null);
        assertThrows(IllegalArgumentException.class,()->binding.toTimedDrafts(prepared(),new PreparedSchedule(s.input(),bad)));
    }
    @Test void rejectsChangedSavedStatus() {
        var list=saved();var first=new SavedCompetitionUnitView(id(20),"Heat 1",1,"PUBLISHED",list.get(0).assignments(),list.get(0).scheduledAt());
        assertThrows(IllegalArgumentException.class,()->binding.bindSavedUnits(prepared(),schedule(),List.of(first,list.get(1))));
    }
    @Test void rejectsSubMicrosecondPlanWithoutRoundingIt() {
        var original=schedule();var input=new Input(original.input().policy(),original.input().sessions(),List.of(
                new Heat(id(10),id(300),id(200),List.of(id(101)),Duration.ofSeconds(120).plusNanos(1),"test"),
                original.input().heats().get(1)));
        var result=new com.acme.sportplatform.protocol.domain.schedule.FixedOrderProtocolScheduler().schedule(input);
        assertThrows(IllegalArgumentException.class,()->binding.toTimedDrafts(prepared(),new PreparedSchedule(input,result)));
    }
}
