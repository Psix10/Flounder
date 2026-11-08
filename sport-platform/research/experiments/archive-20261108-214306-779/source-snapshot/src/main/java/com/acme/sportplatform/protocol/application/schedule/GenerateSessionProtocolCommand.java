package com.acme.sportplatform.protocol.application.schedule;
import java.time.Duration;
import java.util.*;
import static com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.*;

/** Internal command for one supplied batch, not a declaration of the complete event programme. */
public record GenerateSessionProtocolCommand(UUID eventId, boolean regenerate, TimingPolicy timingPolicy,
        List<Session> sessions, List<Discipline> disciplines) {
    public GenerateSessionProtocolCommand {
        Objects.requireNonNull(eventId); Objects.requireNonNull(timingPolicy);
        sessions=List.copyOf(sessions); disciplines=List.copyOf(disciplines);
        if(sessions.isEmpty() || disciplines.isEmpty()) throw new IllegalArgumentException("Sessions and disciplines are required");
        Set<UUID> ids=new HashSet<>();
        for(var discipline:disciplines) if(!ids.add(discipline.disciplineId())) throw new IllegalArgumentException("Duplicate discipline");
    }
    public record Discipline(UUID disciplineId, UUID ruleSetVersionId, UUID sessionId, Duration heatDuration, String durationBasis) {
        public Discipline {
            Objects.requireNonNull(disciplineId); Objects.requireNonNull(ruleSetVersionId); Objects.requireNonNull(sessionId);
            Objects.requireNonNull(heatDuration);
            if(heatDuration.isNegative() || heatDuration.isZero() || durationBasis==null || durationBasis.isBlank())
                throw new IllegalArgumentException("Explicit positive heat duration and basis required");
        }
    }
}
