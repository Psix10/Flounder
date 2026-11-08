package com.acme.sportplatform.protocol.application.schedule;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import com.acme.sportplatform.protocol.CompetitionUnitProtocolAccess.*;
import com.acme.sportplatform.protocol.application.PreparedStartProtocol;
import com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleValidator;
import static com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.*;
import com.acme.sportplatform.protocol.application.schedule.PrepareProtocolSchedule.PreparedSchedule;

/** Pure conversion/binding, no persistence. Caller must use one transaction around future writes and binding. */
public final class ScheduledProtocolBinding {
    public record BoundHeat(UUID temporaryHeatId, UUID savedHeatId, UUID disciplineId, UUID sessionId,
                            Integer sequenceNumber, Instant start, Instant end) { }
    public List<CompetitionUnitDraftView> toTimedDrafts(PreparedStartProtocol prepared, PreparedSchedule schedule) {
        Map<UUID,ScheduledHeat> timed = checkedTimes(prepared,schedule);
        return prepared.units().stream().map(unit -> new CompetitionUnitDraftView(
                unit.label(),unit.sequenceNumber(),"DRAFT",unit.assignments().stream().map(a ->
                new AssignmentDraftView(a.registrationId(),a.laneOrPosition(),a.categoryCode(),a.seedingGroupCode())).toList(),
                timed.get(unit.competitionUnitId()).start().atOffset(ZoneOffset.UTC))).toList();
    }
    public List<BoundHeat> bindSavedUnits(PreparedStartProtocol prepared, PreparedSchedule schedule,
                                         List<SavedCompetitionUnitView> saved) {
        var drafts=toTimedDrafts(prepared,schedule);
        Map<UUID,ScheduledHeat> timed=checkedTimes(prepared,schedule);
        Map<Integer,SavedCompetitionUnitView> bySequence=new HashMap<>(); Set<UUID> ids=new HashSet<>();
        for(var unit:List.copyOf(saved)) {
            if(unit.competitionUnitId()==null || !ids.add(unit.competitionUnitId()) || unit.sequenceNumber()==null
                    || bySequence.putIfAbsent(unit.sequenceNumber(),unit)!=null) throw invalid("Duplicate/missing saved ID or sequence");
        }
        if(saved.size()!=drafts.size()) throw invalid("Saved unit count differs");
        List<BoundHeat> bindings=new ArrayList<>();
        for(int i=0;i<prepared.units().size();i++) {
            var source=prepared.units().get(i); var draft=drafts.get(i); var actual=bySequence.get(source.sequenceNumber());
            if(actual==null || !Objects.equals(draft.label(),actual.label()) || !"DRAFT".equals(actual.status())
                    || actual.scheduledAt()==null || !draft.scheduledAt().toInstant().equals(actual.scheduledAt().toInstant())) {
                throw invalid("Saved label/status/sequence/time differs from planned draft");
            }
            Map<UUID,Integer> expected=new HashMap<>();
            for(var assignment:draft.assignments()) {
                if(assignment.registrationId()==null || assignment.laneOrPosition()==null
                        || expected.putIfAbsent(assignment.registrationId(),assignment.laneOrPosition())!=null) throw invalid("Invalid planned assignment");
            }
            Map<UUID,Integer> actualAssignments=new HashMap<>();
            for(var assignment:actual.assignments()) {
                if(assignment.registrationId()==null || assignment.laneOrPosition()==null
                        || actualAssignments.putIfAbsent(assignment.registrationId(),assignment.laneOrPosition())!=null) throw invalid("Invalid saved assignment");
            }
            if(!expected.equals(actualAssignments)) throw invalid("Saved assignments differ");
            for (var planned : draft.assignments()) {
                var stored = actual.assignments().stream().filter(a -> a.registrationId().equals(planned.registrationId())).findFirst().orElseThrow();
                if (!Objects.equals(planned.categoryCode(), stored.categoryCode())
                        || !Objects.equals(planned.seedingGroupCode(), stored.seedingGroupCode()))
                    throw invalid("Saved category or seeding group differs");
            }
            var time=timed.get(source.competitionUnitId());
            bindings.add(new BoundHeat(source.competitionUnitId(),actual.competitionUnitId(),prepared.eventDisciplineId(),
                    time.sessionId(),source.sequenceNumber(),time.start(),time.end()));
        }
        return List.copyOf(bindings);
    }
    private Map<UUID,ScheduledHeat> checkedTimes(PreparedStartProtocol prepared, PreparedSchedule schedule) {
        var violations=new ProtocolScheduleValidator().validate(schedule.input(),schedule.result());
        if(!violations.isEmpty()) throw invalid("Schedule is not valid: "+violations);
        Set<UUID> expected=new HashSet<>(); Set<Integer> sequence=new HashSet<>(); int previousSequence=0;
        for(var unit:prepared.units()) {
            if(unit.competitionUnitId()==null || !expected.add(unit.competitionUnitId()) || unit.sequenceNumber()==null
                    || unit.sequenceNumber()<=previousSequence || !sequence.add(unit.sequenceNumber())) throw invalid("Invalid prepared ID/sequence");
            previousSequence=unit.sequenceNumber();
        }
        Set<UUID> inInput=new HashSet<>();
        for(var heat:schedule.input().heats()) if(heat.disciplineId().equals(prepared.eventDisciplineId())) inInput.add(heat.id());
        if(expected.isEmpty() || !expected.equals(inInput)) throw invalid("Prepared units do not match schedule discipline heat IDs");
        Map<UUID,ScheduledHeat> times=new HashMap<>();
        for(var heat:schedule.result().heats()) if(heat.disciplineId().equals(prepared.eventDisciplineId())) {
            if(heat.start().getNano()%1000!=0 || heat.end().getNano()%1000!=0) throw invalid("Planned timestamps must have at most microsecond precision");
            times.put(heat.heatId(),heat);
        }
        var chronological=schedule.result().heats().stream().filter(h->h.disciplineId().equals(prepared.eventDisciplineId()))
                .sorted(Comparator.comparing(ScheduledHeat::start)).map(ScheduledHeat::heatId).toList();
        if(!chronological.equals(prepared.units().stream().map(u->u.competitionUnitId()).toList())) throw invalid("Prepared order differs from schedule");
        return times;
    }
    private IllegalArgumentException invalid(String detail) { return new IllegalArgumentException("Schedule binding: "+detail); }
}
