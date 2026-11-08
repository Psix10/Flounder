package com.acme.sportplatform.protocol.application.schedule;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.acme.sportplatform.competition.ProtocolDisciplineLookup;
import static com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.*;

class GenerateSessionProtocolCommandTest {
    private final UUID event=UUID.randomUUID(),discipline=UUID.randomUUID(),version=UUID.randomUUID(),session=UUID.randomUUID();
    private TimingPolicy policy(){return new TimingPolicy(Duration.ZERO,Duration.ZERO,Origin.EXPERIMENTAL,"test");}
    private Session window(){var t=Instant.parse("2026-11-22T06:30:00Z");return new Session(session,new Window(t,t.plusSeconds(3600)),List.of());}
    private GenerateSessionProtocolCommand.Discipline entry(){return new GenerateSessionProtocolCommand.Discipline(discipline,version,session,Duration.ofSeconds(120),"test");}
    @Test void rejectsDuplicateDisciplineIds() {
        assertThrows(IllegalArgumentException.class,()->new GenerateSessionProtocolCommand(event,false,policy(),List.of(window()),List.of(entry(),entry())));
    }
    @Test void rejectsMissingVersion() {
        assertThrows(NullPointerException.class,()->new GenerateSessionProtocolCommand.Discipline(discipline,null,session,Duration.ofSeconds(120),"test"));
    }
    @Test void requiresPositiveDuration() {
        assertThrows(IllegalArgumentException.class,()->new GenerateSessionProtocolCommand.Discipline(discipline,version,session,Duration.ZERO,"test"));
    }
    @Test void defensivelyCopiesCommandLists() {
        var sessions=new ArrayList<>(List.of(window()));var disciplines=new ArrayList<>(List.of(entry()));
        var command=new GenerateSessionProtocolCommand(event,false,policy(),sessions,disciplines);
        sessions.clear();disciplines.clear();assertEquals(1,command.sessions().size());assertEquals(1,command.disciplines().size());
        assertThrows(UnsupportedOperationException.class,()->command.disciplines().clear());
    }
    @Test void legacyLookupConstructorDoesNotInventEventIdentity() {
        var view=new ProtocolDisciplineLookup.View(discipline,UUID.randomUUID(),UUID.randomUUID(),"INDIVIDUAL","HEAT");
        assertNull(view.eventId());assertEquals(discipline,view.id());
    }
}
