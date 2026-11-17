package com.acme.sportplatform.protocol.application.schedule;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.Origin;
import com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.Session;
import com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.TimingPolicy;
import com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.Window;

class GenerateSessionProtocolCommandTest {

    private final UUID eventId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID disciplineId = UUID.randomUUID();

    private Session session() {
        return new Session(
                sessionId,
                new Window(
                        Instant.parse("2026-11-22T06:30:00Z"),
                        Instant.parse("2026-11-22T08:30:00Z")
                ),
                List.of()
        );
    }

    private GenerateSessionProtocolCommand.Discipline discipline(
            UUID targetSessionId,
            Duration duration
    ) {
        return new GenerateSessionProtocolCommand.Discipline(
                disciplineId,
                null,
                targetSessionId,
                duration,
                "Organizer-confirmed planning estimate"
        );
    }

    private GenerateSessionProtocolCommand command(
            List<Session> sessions,
            List<GenerateSessionProtocolCommand.Discipline> disciplines
    ) {
        return new GenerateSessionProtocolCommand(
                eventId,
                false,
                new TimingPolicy(
                        Duration.ofMinutes(10),
                        Duration.ofSeconds(30),
                        Origin.ORGANIZER_CONFIRMED,
                        "Organizer-confirmed timing policy"
                ),
                sessions,
                disciplines
        );
    }

    @Test
    void allowsRuleVersionToBeResolvedFromRegulation() {
        var result = assertDoesNotThrow(() -> command(
                List.of(session()),
                List.of(discipline(sessionId, Duration.ofMinutes(2)))
        ));

        assertNull(result.disciplines().get(0).ruleSetVersionId());
    }

    @Test
    void rejectsUnknownSession() {
        assertThrows(IllegalArgumentException.class, () -> command(
                List.of(session()),
                List.of(discipline(UUID.randomUUID(), Duration.ofMinutes(2)))
        ));
    }

    @Test
    void rejectsDuplicateSessions() {
        var session = session();

        assertThrows(IllegalArgumentException.class, () -> command(
                List.of(session, session),
                List.of(discipline(sessionId, Duration.ofMinutes(2)))
        ));
    }

    @Test
    void rejectsDuplicateDisciplines() {
        var discipline = discipline(sessionId, Duration.ofMinutes(2));

        assertThrows(IllegalArgumentException.class, () -> command(
                List.of(session()),
                List.of(discipline, discipline)
        ));
    }

    @Test
    void rejectsNonPositiveHeatDuration() {
        assertThrows(IllegalArgumentException.class, () ->
                discipline(sessionId, Duration.ZERO)
        );

        assertThrows(IllegalArgumentException.class, () ->
                discipline(sessionId, Duration.ofSeconds(-1))
        );
    }

    @Test
    void rejectsEmptySessionsOrDisciplines() {
        assertThrows(IllegalArgumentException.class, () -> command(
                List.of(),
                List.of(discipline(sessionId, Duration.ofMinutes(2)))
        ));

        assertThrows(IllegalArgumentException.class, () -> command(
                List.of(session()),
                List.of()
        ));
    }
}