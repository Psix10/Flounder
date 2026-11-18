package com.acme.sportplatform.demo;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.acme.sportplatform.common.*;
import com.acme.sportplatform.competition.*;
import com.acme.sportplatform.events.*;
import com.acme.sportplatform.identity.*;
import com.acme.sportplatform.protocol.*;
import com.acme.sportplatform.registrations.DemoRegistrationAccess;
import com.acme.sportplatform.regulations.RegulationBundleAccess;
import com.fasterxml.jackson.databind.ObjectMapper;

class DemoDataServiceTest {
    private final UUID event=UUID.randomUUID(), discipline=UUID.randomUUID(), actor=UUID.randomUUID(),
            version=UUID.randomUUID(), requestId=UUID.randomUUID();
    private final EventOperationAccess access=mock(EventOperationAccess.class);
    private final ProtocolDisciplineLookup disciplines=mock(ProtocolDisciplineLookup.class);
    private final DemoDisciplineConstraints constraints=mock(DemoDisciplineConstraints.class);
    private final EventLookup events=mock(EventLookup.class);
    private final DemoEventAccess eventLock=mock(DemoEventAccess.class);
    private final EventRegulationLookup eventRegulations=mock(EventRegulationLookup.class);
    private final RegulationBundleAccess regulations=mock(RegulationBundleAccess.class);
    private final ProtocolConfigurationPublication configs=mock(ProtocolConfigurationPublication.class);
    private final CompetitionUnitProtocolAccess units=mock(CompetitionUnitProtocolAccess.class);
    private final DemoRegistrationAccess registrations=mock(DemoRegistrationAccess.class);
    private final DemoParticipantProvisioning participants=mock(DemoParticipantProvisioning.class);
    private final DemoBatchStore batches=mock(DemoBatchStore.class);
    private final DemoParticipantPlanning planning=mock(DemoParticipantPlanning.class);
    private final ObjectMapper json=new ObjectMapper();
    private DemoDataService service;
    @BeforeEach void setUp() {
        authenticate("ROLE_ORGANIZER");
        service=new DemoDataService(new DemoDataMode(true,event.toString()),access,disciplines,constraints,
                events,eventLock,eventRegulations,regulations,configs,units,registrations,participants,batches,json,planning);
        when(access.canManageEvent(event,actor)).thenReturn(true);
        when(disciplines.lock(discipline)).thenReturn(new ProtocolDisciplineLookup.View(discipline,
                UUID.randomUUID(),UUID.randomUUID(),"INDIVIDUAL","HEAT",event));
        when(events.getById(event)).thenReturn(new EventLookupResult(event,UUID.randomUUID(),"DRAFT"));
        when(constraints.read(discipline)).thenReturn(new DemoDisciplineConstraints.View(12,"TIME","ASC"));
        when(eventRegulations.versionId(event)).thenReturn(version);
        when(regulations.binding(version)).thenReturn(new RegulationBundleAccess.Binding(version,"INDIVIDUAL","HEAT"));
        when(configs.read(version)).thenReturn(new ProtocolConfigurationPublication.Settings(4,List.of(2,3,1,4),
                "seedTimeSeconds","SLOWEST_FIRST","FIRST"));
        when(batches.byDiscipline(any())).thenReturn(Optional.empty());
        when(batches.byRequest(any())).thenReturn(Optional.empty());
        when(planning.plan(eq(version),anyString(),eq(8))).thenAnswer(call ->
                IntStream.rangeClosed(1,8).mapToObj(i -> new DemoParticipantPlanning.Candidate(
                        i%2==1?"MALE":"FEMALE", LocalDate.of(2000,1,1),null,null)).toList());
        when(participants.create(eq(event),eq(requestId),anyInt(),any(DemoParticipantProvisioning.Fixture.class))).thenAnswer(call -> {
            int i=call.getArgument(2);
            DemoParticipantProvisioning.Fixture fixture=call.getArgument(3);
            return new ProfileLookupResult(UUID.randomUUID(),UUID.randomUUID(),
                    "MALE".equals(fixture.gender())?"Демо-участник":"Демо-участница",String.format("Тестовый-%03d",i),null,
                    fixture.birthDate(),"MALE".equals(fixture.gender())?"male":"female","Moscow","RU","Demo",
                    Map.of("demoFixture",true,"demoBatchId",requestId.toString()));
        });
        when(registrations.createConfirmed(eq(event),eq(discipline),eq(requestId),eq(actor),anyList()))
                .thenAnswer(call -> {
                    List<DemoRegistrationAccess.Entry> entries=call.getArgument(4);
                    return IntStream.range(0,entries.size()).mapToObj(i->UUID.randomUUID()).toList();
                });
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private void authenticate(String role) {
        AuthenticatedUserPrincipal principal=()->actor;
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal,"unused",List.of(new SimpleGrantedAuthority(role))));
    }
    private DemoDataService.Request request() {
        return new DemoDataService.Request(requestId,version,4,8,service.preview(event,discipline).datasetVersion(),true);
    }
    private DemoBatchStore.Stored stored(DemoDataService.Receipt receipt) throws Exception {
        return new DemoBatchStore.Stored(requestId,event,discipline,actor,version,4,8,json.writeValueAsString(receipt));
    }
    @Test void previewDoesNotCreateParticipantsOrAcquireEventWriteLock() {
        var plan=service.preview(event,discipline);
        assertEquals("AVAILABLE",plan.state()); assertEquals(8,plan.count());
        verifyNoInteractions(participants,eventLock);
        verify(batches,never()).reserve(any(),any(),any(),any(),any(),anyInt(),anyInt());
    }
    @Test void createsEightRowsAndOneMissingTimeAndStoresReceipt() {
        var receipt=service.create(event,discipline,request());
        assertEquals(8,receipt.participants().size());
        assertEquals(1,receipt.participants().stream().filter(r->r.seedTimeSeconds()==null).count());
        verify(eventLock).lockDraft(event);
        verify(participants,times(8)).create(eq(event),eq(requestId),anyInt(),any(DemoParticipantProvisioning.Fixture.class));
        verify(batches).finish(eq(requestId),contains("SWIM_ELIGIBLE_V2:"));
    }
    @Test void identicalRequestReplaysReceiptWithoutAnotherWrite() throws Exception {
        var first=service.create(event,discipline,request());
        when(batches.byRequest(requestId)).thenReturn(Optional.of(stored(first)));
        assertEquals(first,service.create(event,discipline,request()));
        verify(participants,times(8)).create(eq(event),eq(requestId),anyInt(),any(DemoParticipantProvisioning.Fixture.class));
        verify(batches,times(1)).reserve(any(),any(),any(),any(),any(),anyInt(),anyInt());
    }
    @Test void reusedRequestCannotChangeCapacity() throws Exception {
        var first=service.create(event,discipline,request());
        when(batches.byRequest(requestId)).thenReturn(Optional.of(stored(first)));
        assertThrows(DemoDataException.class,()->service.create(event,discipline,
                new DemoDataService.Request(requestId,version,5,10,DemoSample.VERSION,true)));
    }
    @Test void anotherRequestCannotAppendToAnExistingBatch() throws Exception {
        var first=service.create(event,discipline,request());
        when(batches.byDiscipline(discipline)).thenReturn(Optional.of(stored(first)));
        assertThrows(DemoDataException.class,()->service.create(event,discipline,
                new DemoDataService.Request(UUID.randomUUID(),version,4,8,DemoSample.VERSION,true)));
        verify(participants,times(8)).create(eq(event),eq(requestId),anyInt(),any(DemoParticipantProvisioning.Fixture.class));
    }
    @Test void rejectsOperator() {
        authenticate("ROLE_OPERATOR");
        assertThrows(AccessDeniedException.class,()->service.create(event,discipline,request()));
        verifyNoInteractions(participants);
    }
    @Test void rejectsOrganizerWithoutEventPermission() {
        when(access.canManageEvent(event,actor)).thenReturn(false);
        assertThrows(AccessDeniedException.class,()->service.create(event,discipline,request()));
        verifyNoInteractions(participants);
    }
    @Test void rejectsWrongEventInDisciplineRoute() {
        when(disciplines.lock(discipline)).thenReturn(new ProtocolDisciplineLookup.View(discipline,
                UUID.randomUUID(),UUID.randomUUID(),"INDIVIDUAL","HEAT",UUID.randomUUID()));
        assertThrows(DemoDataException.class,()->service.create(event,discipline,request()));
        verifyNoInteractions(participants);
    }
    @Test void rejectsUnconfirmedPayload() {
        assertThrows(DemoDataException.class,()->service.create(event,discipline,
                new DemoDataService.Request(requestId,version,4,8,DemoSample.VERSION,false)));
        verifyNoInteractions(participants);
    }
    @Test void rejectsChangedPlanBeforeProvisioning() {
        assertThrows(DemoDataException.class,()->service.create(event,discipline,
                new DemoDataService.Request(requestId,UUID.randomUUID(),4,8,DemoSample.VERSION,true)));
        verifyNoInteractions(participants);
    }
    @Test void blocksNonEmptyDiscipline() {
        when(registrations.count(discipline)).thenReturn(1L);
        assertEquals("BLOCKED",service.preview(event,discipline).state());
        assertThrows(DemoDataException.class,()->service.create(event,discipline,request()));
        verifyNoInteractions(participants);
    }
    @Test void blocksExistingCompetitionUnits() {
        when(units.hasCompetitionUnits(discipline)).thenReturn(true);
        assertThrows(DemoDataException.class,()->service.create(event,discipline,request()));
        verifyNoInteractions(participants);
    }
    @Test void blocksPublishedEventAndInsufficientLimit() {
        when(events.getById(event)).thenReturn(new EventLookupResult(event,UUID.randomUUID(),"PUBLISHED"));
        assertEquals("BLOCKED",service.preview(event,discipline).state());
        when(events.getById(event)).thenReturn(new EventLookupResult(event,UUID.randomUUID(),"DRAFT"));
        when(constraints.read(discipline)).thenReturn(new DemoDisciplineConstraints.View(7,"TIME","ASC"));
        assertEquals("BLOCKED",service.preview(event,discipline).state());
    }
    @Test void uniqueReservationFailureCreatesNoParticipants() {
        doThrow(new DuplicateKeyException("concurrent request")).when(batches)
                .reserve(any(),any(),any(),any(),any(),anyInt(),anyInt());
        assertThrows(DemoDataException.class,()->service.create(event,discipline,request()));
        verifyNoInteractions(participants);
    }
    @Test void provisioningFailureDoesNotPublishReceipt() {
        when(participants.create(eq(event),eq(requestId),eq(2),any(DemoParticipantProvisioning.Fixture.class))).thenThrow(new IllegalStateException("test failure"));
        assertThrows(IllegalStateException.class,()->service.create(event,discipline,request()));
        verify(batches,never()).finish(any(),anyString());
        verify(registrations,never()).createConfirmed(any(),any(),any(),any(),anyList());
    }

    @Test void previewAndReceiptUseExactlyTheSameParticipantDetails() {
        var plan=service.preview(event,discipline);
        var receipt=service.create(event,discipline,request());
        assertEquals(plan.datasetVersion(),receipt.datasetVersion());
        for(int i=0;i<8;i++) {
            var shown=plan.participants().get(i); var created=receipt.participants().get(i);
            assertEquals(shown.name(),created.name()); assertEquals(shown.gender(),created.gender());
            assertEquals(shown.birthDate(),created.birthDate()); assertEquals(shown.categoryCode(),created.categoryCode());
            assertEquals(shown.seedTimeSeconds(),created.seedTimeSeconds()); assertNotNull(created.registrationId());
        }
    }
    @Test void changedParticipantPlanIsRejectedBeforeAnyProvisioning() {
        var approved=request();
        when(planning.plan(eq(version),anyString(),eq(8))).thenReturn(IntStream.rangeClosed(1,8)
                .mapToObj(i->new DemoParticipantPlanning.Candidate("FEMALE",LocalDate.of(2010,1,1),"JUNIOR","JUNIOR")).toList());
        var error=assertThrows(DemoDataException.class,()->service.create(event,discipline,approved));
        assertEquals("demo.plan_changed",error.code()); verifyNoInteractions(participants);
        verify(batches,never()).reserve(any(),any(),any(),any(),any(),anyInt(),anyInt());
    }
    @Test void unsupportedPlanningBlocksPreviewAndCreationBeforeReservation() {
        when(planning.plan(eq(version),anyString(),eq(8))).thenThrow(
                new DemoDataException(409,"demo.unsupported_configuration","No eligible category"));
        var plan=service.preview(event,discipline);
        assertEquals("BLOCKED",plan.state()); assertTrue(plan.participants().isEmpty());
        assertThrows(DemoDataException.class,()->service.create(event,discipline,request()));
        verifyNoInteractions(participants);
        verify(batches,never()).reserve(any(),any(),any(),any(),any(),anyInt(),anyInt());
    }
    @Test void legacyReceiptCanBeReadAndReplayedWithoutFreshPlanning() throws Exception {
        var legacy=new DemoDataService.Receipt(requestId,event,discipline,version,4,8,"SWIM_MIXED_V1","old",
                List.of(new DemoDataService.Row(1,"Legacy","MALE",null,UUID.randomUUID())));
        // Simulate historical JSON without the newly added row properties.
        var node=json.valueToTree(legacy);
        var row=(com.fasterxml.jackson.databind.node.ObjectNode)node.path("participants").get(0);
        row.remove(List.of("birthDate","categoryCode","seedingGroupCode"));
        var stored=new DemoBatchStore.Stored(requestId,event,discipline,actor,version,4,8,json.writeValueAsString(node));
        when(batches.byDiscipline(discipline)).thenReturn(Optional.of(stored));
        when(batches.byRequest(requestId)).thenReturn(Optional.of(stored));
        assertEquals("PREPARED",service.preview(event,discipline).state());
        assertEquals(legacy,service.create(event,discipline,
                new DemoDataService.Request(requestId,version,4,8,"SWIM_MIXED_V1",true)));
        verifyNoInteractions(planning,participants,eventLock);
    }
    @Test void legacyDatasetCannotCreateANewBatch() {
        var error=assertThrows(DemoDataException.class,()->service.create(event,discipline,
                new DemoDataService.Request(requestId,version,4,8,"SWIM_MIXED_V1",true)));
        assertEquals("demo.plan_changed",error.code()); verifyNoInteractions(participants);
    }
    @Test void replayCannotChangeDatasetVersion() throws Exception {
        var receipt=service.create(event,discipline,request());
        when(batches.byRequest(requestId)).thenReturn(Optional.of(stored(receipt)));
        var error=assertThrows(DemoDataException.class,()->service.create(event,discipline,
                new DemoDataService.Request(requestId,version,4,8,"SWIM_MIXED_V1",true)));
        assertEquals("demo.request_conflict",error.code());
        verify(participants,times(8)).create(eq(event),eq(requestId),anyInt(),any(DemoParticipantProvisioning.Fixture.class));
    }

    @Test void realPlannerFeedsFemaleAgeFixturesIntoRegistrationCreation() throws Exception {
        var repository=mock(com.acme.sportplatform.protocol.infrastructure.jpa.ProtocolRuleBlockRepository.class);
        var actualPlanning=new com.acme.sportplatform.protocol.application.DemoParticipantPlanningService(repository,json);
        var ruleBlocks=new java.util.ArrayList<com.acme.sportplatform.protocol.infrastructure.jpa.ProtocolRuleBlockEntity>();
        String[] executors={"ELIGIBILITY_CONFIRMED_ONLY","SEED_BY_METRIC_ASC_NULLS_LAST","GROUP_BY_CAPACITY","ASSIGN_CENTER_OUT"};
        for(int i=0;i<4;i++) {
            var block=new com.acme.sportplatform.protocol.infrastructure.jpa.ProtocolRuleBlockEntity();
            block.setExecutorCode(executors[i]);block.setConfig(json.createObjectNode());ruleBlocks.add(block);
        }
        ruleBlocks.get(2).setConfig(json.readTree("""
                {"categories":[{"code":"JUNIOR","seedingGroupCode":"JUNIOR",
                 "eligibility":{"ageMethod":"BY_YEAR","ageReferenceDate":"2026-01-01","minAge":14,"maxAge":14}}]}
                """));
        when(repository.findByRuleSetVersionIdOrderByOrderIndexAsc(version)).thenReturn(ruleBlocks);
        when(constraints.read(discipline)).thenReturn(new DemoDisciplineConstraints.View(12,"TIME","ASC",
                "{\"gender\":\"FEMALE\",\"allowedCategoryCodes\":[\"JUNIOR\"]}"));
        var actual=new DemoDataService(new DemoDataMode(true,event.toString()),access,disciplines,constraints,
                events,eventLock,eventRegulations,regulations,configs,units,registrations,participants,batches,json,actualPlanning);
        var plan=actual.preview(event,discipline);
        assertEquals("AVAILABLE",plan.state());
        var receipt=actual.create(event,discipline,new DemoDataService.Request(requestId,version,4,8,plan.datasetVersion(),true));
        for(var row:receipt.participants()) {
            assertEquals("FEMALE",row.gender());assertEquals(2012,LocalDate.parse(row.birthDate()).getYear());
            assertEquals("JUNIOR",row.categoryCode());assertEquals("JUNIOR",row.seedingGroupCode());
            assertTrue(row.name().endsWith("Демо-участница"));
        }
        verify(participants,times(8)).create(eq(event),eq(requestId),anyInt(),argThat(f ->
                "FEMALE".equals(f.gender())&&f.birthDate().getYear()==2012));
        assertEquals(1,receipt.participants().stream().filter(r->r.seedTimeSeconds()==null).count());
    }
}
