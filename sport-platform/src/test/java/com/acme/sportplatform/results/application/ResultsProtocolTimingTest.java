package com.acme.sportplatform.results.application;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.results.*;
import com.acme.sportplatform.results.infrastructure.jpa.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResultsProtocolTimingTest {
    private final CompetitionUnitRepository units=mock(CompetitionUnitRepository.class);
    private final CompetitionUnitEntryRepository entries=mock(CompetitionUnitEntryRepository.class);
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final ResultsProtocolGatewayService service=new ResultsProtocolGatewayService(units,entries,jdbc);
    private final UUID discipline=UUID.randomUUID(), registration=UUID.randomUUID();
    private final OffsetDateTime time=OffsetDateTime.parse("2026-11-22T09:30:00+03:00");
    private ResultsCompetitionUnitDraft draft(OffsetDateTime at) {
        return new ResultsCompetitionUnitDraft("Heat 1",1,"DRAFT",List.of(new ResultsCompetitionUnitEntryDraft(registration,2)),at);
    }
    private void saving() {
        when(units.save(any(CompetitionUnitEntity.class))).thenAnswer(i->i.getArgument(0));
        when(entries.save(any(CompetitionUnitEntryEntity.class))).thenAnswer(i->i.getArgument(0));
    }
    @Test void createPassesTimeIntoEntityAndReturnedView() {
        saving();var result=service.createDraftStartProtocol(discipline,List.of(draft(time)));
        var capture=org.mockito.ArgumentCaptor.forClass(CompetitionUnitEntity.class);
        verify(units).save(capture.capture());assertEquals(time,capture.getValue().getScheduledAt());
        assertEquals(time,result.get(0).scheduledAt());assertNotNull(result.get(0).id());
    }
    @Test void legacyDraftKeepsNullTime() {
        saving();var old=new ResultsCompetitionUnitDraft("Heat 1",1,"DRAFT",List.of(new ResultsCompetitionUnitEntryDraft(registration,2)));
        assertNull(service.createDraftStartProtocol(discipline,List.of(old)).get(0).scheduledAt());
    }
    @Test void readingIncludesExistingScheduledTime() {
        var unit=new CompetitionUnitEntity();unit.setId(UUID.randomUUID());unit.setLabel("Heat 1");
        unit.setSequenceNumber(1);unit.setStatus("DRAFT");unit.setScheduledAt(time);
        when(units.findByEventDisciplineIdOrderBySequenceNumberAsc(discipline)).thenReturn(List.of(unit));
        when(entries.findByCompetitionUnitId(unit.getId())).thenReturn(List.of());
        assertEquals(time,service.findCompetitionUnitsByEventDisciplineId(discipline).get(0).scheduledAt());
    }
    @Test void oldViewConstructorHasNullTime() {
        assertNull(new ResultsCompetitionUnitView(UUID.randomUUID(),"Heat 1",1,"DRAFT",List.of()).scheduledAt());
    }
    @Test void rejectsSubMicrosecondTimeBeforeAnyWrite() {
        assertThrows(BusinessException.class,()->service.createDraftStartProtocol(discipline,List.of(draft(time.plusNanos(1)))));
        verifyNoInteractions(units,entries,jdbc);
    }
    @Test void acceptsExplicitMicrosecondTime() {
        saving();var at=time.plusNanos(123000);
        assertEquals(at,service.createDraftStartProtocol(discipline,List.of(draft(at))).get(0).scheduledAt());
    }
}
