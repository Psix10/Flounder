package com.acme.sportplatform.protocol.application;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.acme.sportplatform.protocol.CompetitionUnitProtocolAccess.*;
import com.acme.sportplatform.results.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompetitionUnitTimingMappingTest {
    private final ResultsProtocolGateway gateway=mock(ResultsProtocolGateway.class);
    private final CompetitionUnitProtocolAccessService service=new CompetitionUnitProtocolAccessService(gateway);
    private final UUID discipline=UUID.randomUUID(), registration=UUID.randomUUID(), unit=UUID.randomUUID();
    private final OffsetDateTime time=OffsetDateTime.parse("2026-11-22T09:30:00+03:00");
    private CompetitionUnitDraftView draft() {
        return new CompetitionUnitDraftView("Heat 1",1,"DRAFT",List.of(new AssignmentDraftView(registration,2)),time);
    }
    private ResultsCompetitionUnitView result() {
        return new ResultsCompetitionUnitView(unit,"Heat 1",1,"DRAFT",List.of(
                new ResultsCompetitionUnitEntryView(UUID.randomUUID(),registration,2)),time);
    }
    @Test void createMapsTimeBothDirections() {
        when(gateway.createDraftStartProtocol(eq(discipline),anyList())).thenAnswer(i->{
            List<ResultsCompetitionUnitDraft> drafts=i.getArgument(1);
            assertEquals(time,drafts.get(0).scheduledAt());assertEquals(registration,drafts.get(0).entries().get(0).registrationId());
            return List.of(result());
        });
        var saved=service.createDraftStartProtocol(discipline,List.of(draft()));
        assertEquals(time,saved.get(0).scheduledAt());assertEquals(unit,saved.get(0).competitionUnitId());
    }
    @Test void replaceMapsTimeBothDirections() {
        when(gateway.replaceDraftStartProtocol(eq(discipline),anyList())).thenAnswer(i->{
            List<ResultsCompetitionUnitDraft> drafts=i.getArgument(1);assertEquals(time,drafts.get(0).scheduledAt());
            return List.of(result());
        });
        assertEquals(time,service.replaceDraftStartProtocol(discipline,List.of(draft())).get(0).scheduledAt());
    }
    @Test void readMapsTime() {
        when(gateway.findCompetitionUnitsByEventDisciplineId(discipline)).thenReturn(List.of(result()));
        assertEquals(time,service.findStartProtocol(discipline).orElseThrow().units().get(0).scheduledAt());
    }
    @Test void legacyConstructorsKeepNullTime() {
        assertNull(new CompetitionUnitDraftView("Heat 1",1,"DRAFT",List.of()).scheduledAt());
        assertNull(new SavedCompetitionUnitView(unit,"Heat 1",1,"DRAFT",List.of()).scheduledAt());
        assertNull(new CompetitionUnitView(unit,"Heat 1",1,"DRAFT",List.of()).scheduledAt());
    }
}
