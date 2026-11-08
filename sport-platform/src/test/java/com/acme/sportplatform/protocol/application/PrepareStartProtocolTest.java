package com.acme.sportplatform.protocol.application;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.protocol.RegistrationProtocolAccess.ConfirmedRegistrationView;
import com.acme.sportplatform.protocol.api.RuleBlockRequest;
import com.acme.sportplatform.protocol.application.schedule.PrepareProtocolSchedule;
import com.acme.sportplatform.protocol.application.schedule.ProtocolScheduleInputAdapter.DisciplinePlan;
import static com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrepareStartProtocolTest {
    private final ObjectMapper json = new ObjectMapper();
    private final RuleExecutorRegistry registry = mock(RuleExecutorRegistry.class);
    private static UUID id(long n) { return new UUID(0,n); }
    private ResolvedProtocolRules rules() {
        return new ResolvedProtocolRules(id(900),2,"Heat","seedTimeSeconds",List.of(2,1),List.of(1,2),
                "FASTEST_FIRST","LAST",List.of(new RuleBlockRequest(0,"TEST",json.createObjectNode())));
    }
    private ConfirmedRegistrationView registration(long n, String meta) {
        return new ConfirmedRegistrationView(id(n),meta);
    }
    private PrepareStartProtocol preparer(Consumer<ProtocolGenerationContext> action) {
        when(registry.get("TEST")).thenReturn(new RuleBlockExecutor() {
            @Override public String code() { return "TEST"; }
            @Override public void apply(ProtocolGenerationContext context) { action.accept(context); }
        });
        return new PrepareStartProtocol(registry,json);
    }
    private void buildGroups(ProtocolGenerationContext context) {
        var participants=context.getParticipants();
        for (int i=0;i<participants.size();i+=context.getUnitCapacity()) {
            List<GeneratedAssignment> group=new ArrayList<>();
            for(int j=0;j<Math.min(context.getUnitCapacity(),participants.size()-i);j++) {
                var p=participants.get(i+j);
                group.add(new GeneratedAssignment(p.registrationId(),context.getPositionOrder().get(j),p.seedMetric()));
            }
            context.getGroupedAssignments().add(group);
        }
    }
    private void expectInvalid(Consumer<ProtocolGenerationContext> action, List<ConfirmedRegistrationView> registrations) {
        assertThrows(BusinessException.class,()->preparer(action).prepare(id(200),rules(),registrations));
    }
    @Test void preparesUnitsAndPreservesMetricsAndDraftLabels() {
        var result=preparer(this::buildGroups).prepare(id(200),rules(),List.of(
                registration(1,"{\"seedTimeSeconds\":29.25}"),registration(2,"{\"seedTimeSeconds\":30}"),registration(3,"{}")));
        assertEquals(id(200),result.eventDisciplineId()); assertEquals(2,result.units().size());
        assertEquals("Heat 1",result.units().get(0).label()); assertEquals(2,result.units().get(1).sequenceNumber());
        assertEquals(new BigDecimal("29.25"),result.units().get(0).assignments().get(0).seedMetric());
        assertNull(result.units().get(1).assignments().get(0).seedMetric());
        assertNotNull(result.units().get(0).competitionUnitId());
    }
    @Test void acceptsNullSeedMetricsAndLegacyProfileForSeedingOnly() {
        var result=preparer(this::buildGroups).prepare(id(200),rules(),List.of(registration(1,"{}")));
        assertNull(result.participants().get(0).seedMetric()); assertEquals(1,result.units().size());
    }
    @Test void rejectsEmptyConfirmedBeforeExecutingRules() {
        var preparation=new PrepareStartProtocol(registry,json);
        assertThrows(BusinessException.class,()->preparation.prepare(id(200),rules(),List.of()));
        verifyNoInteractions(registry);
    }
    @Test void rejectsMalformedMeta() {
        expectInvalid(this::buildGroups,List.of(registration(1,"{broken")));
    }
    @Test void rejectsNonNumericMetric() {
        expectInvalid(this::buildGroups,List.of(registration(1,"{\"seedTimeSeconds\":\"30\"}")));
    }
    @Test void rejectsMissingRegistrationId() {
        expectInvalid(this::buildGroups,List.of(new ConfirmedRegistrationView(null,"{}")));
    }
    @Test void rejectsDuplicateRegistrationEvenWhenMetricIsNull() {
        expectInvalid(this::buildGroups,List.of(registration(1,"{}"),registration(1,"{}")));
    }
    @Test void rejectsUnknownOutputRegistration() {
        expectInvalid(c->c.getGroupedAssignments().add(List.of(new GeneratedAssignment(id(999),2,null))),List.of(registration(1,"{}")));
    }
    @Test void rejectsDuplicateOutputRegistration() {
        expectInvalid(c->c.getGroupedAssignments().add(List.of(new GeneratedAssignment(id(1),2,null),new GeneratedAssignment(id(1),1,null))),
                List.of(registration(1,"{}"),registration(2,"{}")));
    }
    @Test void rejectsOversizedHeat() {
        expectInvalid(c->c.getGroupedAssignments().add(List.of(new GeneratedAssignment(id(1),2,null),
                new GeneratedAssignment(id(2),1,null),new GeneratedAssignment(id(3),2,null))),
                List.of(registration(1,"{}"),registration(2,"{}"),registration(3,"{}")));
    }
    @Test void rejectsDisallowedLane() {
        expectInvalid(c->c.getGroupedAssignments().add(List.of(new GeneratedAssignment(id(1),99,null))),List.of(registration(1,"{}")));
    }
    @Test void rejectsWrongPositionOrder() {
        expectInvalid(c->c.getGroupedAssignments().add(List.of(new GeneratedAssignment(id(1),1,null))),List.of(registration(1,"{}")));
    }
    @Test void rejectsMissingAssignments() {
        expectInvalid(c->c.getGroupedAssignments().add(List.of(new GeneratedAssignment(id(1),2,null))),
                List.of(registration(1,"{}"),registration(2,"{}")));
    }
    @Test void rejectsEmptyHeat() {
        expectInvalid(c->c.getGroupedAssignments().add(List.of()),List.of(registration(1,"{}")));
    }
    @Test void copiedOutputIsIndependentOfMutableExecutorContext() {
        ProtocolGenerationContext[] captured=new ProtocolGenerationContext[1];
        var result=preparer(c->{captured[0]=c;buildGroups(c);}).prepare(id(200),rules(),List.of(registration(1,"{}")));
        captured[0].getGroupedAssignments().get(0).clear(); captured[0].getGroupedAssignments().clear();
        captured[0].getParticipants().clear();
        assertEquals(1,result.units().get(0).assignments().size()); assertEquals(1,result.participants().size());
        assertThrows(UnsupportedOperationException.class,()->result.units().clear());
        assertThrows(UnsupportedOperationException.class,()->result.units().get(0).assignments().clear());
        assertThrows(UnsupportedOperationException.class,()->result.participants().clear());
    }
    @Test void producesExistingPersistenceDraftContract() {
        var result=preparer(this::buildGroups).prepare(id(200),rules(),List.of(registration(1,"{}")));
        var draft=result.toDrafts().get(0);
        assertEquals("Heat 1",draft.label()); assertEquals(1,draft.sequenceNumber()); assertEquals("DRAFT",draft.status());
        assertEquals(id(1),draft.assignments().get(0).registrationId()); assertEquals(2,draft.assignments().get(0).laneOrPosition());
    }
    @Test void passesAllResolvedGroupingParametersToExecutor() {
        var r=rules();
        preparer(c->{
            assertEquals(id(200),c.getEventDisciplineId()); assertEquals(r.capacity(),c.getUnitCapacity());
            assertEquals(r.prefix(),c.getUnitLabelPrefix()); assertEquals(r.order(),c.getPositionOrder());
            assertEquals(r.allowed(),c.getAllowedPositions()); assertEquals(r.heatOrder(),c.getHeatOrder());
            assertEquals(r.partialHeat(),c.getPartialHeat()); buildGroups(c);
        }).prepare(id(200),r,List.of(registration(1,"{}")));
        verify(registry).get("TEST");
    }
    @Test void endToEndPreparationThenScheduleRespectsSameProfileRest() {
        var preparation=preparer(this::buildGroups); UUID profile=id(100), session=id(400);
        var registrations1=List.of(new ConfirmedRegistrationView(id(1),"{}",profile));
        var registrations2=List.of(new ConfirmedRegistrationView(id(2),"{}",profile));
        var first=preparation.prepare(id(200),rules(),registrations1);
        var second=preparation.prepare(id(201),rules(),registrations2);
        var t=Instant.parse("2026-11-22T06:30:00Z");
        var result=new PrepareProtocolSchedule().execute(
                new TimingPolicy(Duration.ofSeconds(600),Duration.ofSeconds(30),Origin.EXPERIMENTAL,"test"),
                List.of(new Session(session,new Window(t,t.plusSeconds(3600)),List.of())),
                List.of(new DisciplinePlan(id(200),session,first.units(),registrations1,Duration.ofSeconds(120),"test"),
                        new DisciplinePlan(id(201),session,second.units(),registrations2,Duration.ofSeconds(120),"test")));
        assertEquals(Status.SCHEDULED,result.result().status());
        assertEquals(t.plusSeconds(720),result.result().heats().get(1).start());
    }
    @Test void endToEndWindowFailureDoesNotChangePreparedAssignments() {
        var preparation=preparer(this::buildGroups); UUID profile=id(100), session=id(400);
        var registrations1=List.of(new ConfirmedRegistrationView(id(1),"{}",profile));
        var registrations2=List.of(new ConfirmedRegistrationView(id(2),"{}",profile));
        var first=preparation.prepare(id(200),rules(),registrations1);
        var second=preparation.prepare(id(201),rules(),registrations2);
        var t=Instant.parse("2026-11-22T06:30:00Z");
        var result=new PrepareProtocolSchedule().execute(
                new TimingPolicy(Duration.ofSeconds(600),Duration.ofSeconds(30),Origin.EXPERIMENTAL,"test"),
                List.of(new Session(session,new Window(t,t.plusSeconds(300)),List.of())),
                List.of(new DisciplinePlan(id(200),session,first.units(),registrations1,Duration.ofSeconds(120),"test"),
                        new DisciplinePlan(id(201),session,second.units(),registrations2,Duration.ofSeconds(120),"test")));
        assertEquals(Status.NOT_FIT_FIXED_ORDER,result.result().status()); assertTrue(result.result().heats().isEmpty());
        assertEquals(id(1),first.units().get(0).assignments().get(0).registrationId());
        assertEquals(id(2),second.units().get(0).assignments().get(0).registrationId());
    }
}
