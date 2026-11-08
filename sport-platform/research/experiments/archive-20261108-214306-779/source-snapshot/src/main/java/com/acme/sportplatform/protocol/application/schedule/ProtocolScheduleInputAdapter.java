package com.acme.sportplatform.protocol.application.schedule;

import java.time.Duration;
import java.util.*;
import com.acme.sportplatform.protocol.RegistrationProtocolAccess.ConfirmedRegistrationView;
import com.acme.sportplatform.protocol.application.GeneratedCompetitionUnit;
import com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel;
import static com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.*;

/** Pure adapter for individual starts. No repository access, writes or implicit timing defaults. */
public final class ProtocolScheduleInputAdapter {
    public record DisciplinePlan(UUID disciplineId, UUID sessionId,
                                 List<GeneratedCompetitionUnit> units,
                                 List<ConfirmedRegistrationView> registrations,
                                 Duration heatDuration, String durationBasis) {
        public DisciplinePlan {
            Objects.requireNonNull(disciplineId); Objects.requireNonNull(sessionId);
            units = List.copyOf(units); registrations = List.copyOf(registrations);
            Objects.requireNonNull(heatDuration);
            if (units.isEmpty() || heatDuration.isZero() || heatDuration.isNegative()) {
                throw new IllegalArgumentException("Non-empty units and positive heatDuration are required");
            }
            if (durationBasis == null || durationBasis.isBlank()) throw new IllegalArgumentException("Duration basis is required");
        }
    }
    public Input adapt(TimingPolicy policy, List<Session> sessions, List<DisciplinePlan> plans) {
        Objects.requireNonNull(policy); sessions = List.copyOf(sessions); plans = List.copyOf(plans);
        if (plans.isEmpty()) throw new IllegalArgumentException("Discipline plans are required");
        Set<UUID> disciplines = new HashSet<>(); Set<UUID> globalRegistrations = new HashSet<>();
        List<Heat> heats = new ArrayList<>();
        for (DisciplinePlan plan : plans) {
            if (!disciplines.add(plan.disciplineId())) throw new IllegalArgumentException("Duplicate discipline plan: " + plan.disciplineId());
            Map<UUID, ConfirmedRegistrationView> registrations = new HashMap<>();
            for (ConfirmedRegistrationView registration : plan.registrations()) {
                if (registration.registrationId() == null || registrations.putIfAbsent(registration.registrationId(), registration) != null
                        || !globalRegistrations.add(registration.registrationId())) {
                    throw new IllegalArgumentException("Missing or duplicate registration ID");
                }
                registration.requireParticipantProfileId();
            }
            Set<UUID> assigned = new HashSet<>(); int previousSequence = 0;
            for (GeneratedCompetitionUnit unit : plan.units()) {
                Objects.requireNonNull(unit.competitionUnitId(), "competitionUnitId");
                Integer sequence = unit.sequenceNumber();
                if (sequence == null || sequence <= previousSequence) {
                    throw new IllegalArgumentException("Unit sequence numbers must be positive and strictly ascending within discipline");
                }
                previousSequence = sequence;
                if (unit.assignments() == null || unit.assignments().isEmpty()) throw new IllegalArgumentException("Empty unit assignments");
                List<UUID> athletes = new ArrayList<>();
                for (var assignment : unit.assignments()) {
                    ConfirmedRegistrationView registration = registrations.get(assignment.registrationId());
                    if (registration == null || !assigned.add(assignment.registrationId())) {
                        throw new IllegalArgumentException("Unknown or multiply assigned registration: " + assignment.registrationId());
                    }
                    athletes.add(registration.requireParticipantProfileId());
                }
                heats.add(new Heat(unit.competitionUnitId(), plan.sessionId(), plan.disciplineId(),
                        athletes, plan.heatDuration(), plan.durationBasis()));
            }
            if (!assigned.equals(registrations.keySet())) throw new IllegalArgumentException("Some confirmed registrations are not assigned");
        }
        return new ProtocolScheduleModel.Input(policy, sessions, heats);
    }
}
