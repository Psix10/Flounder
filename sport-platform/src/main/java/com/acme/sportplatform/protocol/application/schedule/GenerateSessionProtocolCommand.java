package com.acme.sportplatform.protocol.application.schedule;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.Session;
import com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.TimingPolicy;

/**
 * Internal command for one supplied batch.
 * Does not declare or validate the complete event programme.
 */
public record GenerateSessionProtocolCommand(
        UUID eventId,
        boolean regenerate,
        TimingPolicy timingPolicy,
        List<Session> sessions,
        List<Discipline> disciplines
) {
    public GenerateSessionProtocolCommand {
        Objects.requireNonNull(eventId, "Event ID is required");
        Objects.requireNonNull(timingPolicy, "Timing policy is required");
        Objects.requireNonNull(sessions, "Sessions are required");
        Objects.requireNonNull(disciplines, "Disciplines are required");

        sessions = List.copyOf(sessions);
        disciplines = List.copyOf(disciplines);

        if (sessions.isEmpty() || disciplines.isEmpty()) {
            throw new IllegalArgumentException(
                    "Sessions and disciplines are required"
            );
        }

        Set<UUID> sessionIds = new HashSet<>();

        for (Session session : sessions) {
            if (!sessionIds.add(session.id())) {
                throw new IllegalArgumentException(
                        "Duplicate session ID"
                );
            }
        }

        Set<UUID> disciplineIds = new HashSet<>();

        for (Discipline discipline : disciplines) {
            if (!disciplineIds.add(discipline.disciplineId())) {
                throw new IllegalArgumentException(
                        "Duplicate discipline"
                );
            }

            if (!sessionIds.contains(discipline.sessionId())) {
                throw new IllegalArgumentException(
                        "Discipline references an unknown session"
                );
            }
        }
    }

    public record Discipline(
            UUID disciplineId,
            UUID ruleSetVersionId,
            UUID sessionId,
            Duration heatDuration,
            String durationBasis
    ) {
        public Discipline {
            Objects.requireNonNull(
                    disciplineId,
                    "Discipline ID is required"
            );
            Objects.requireNonNull(
                    sessionId,
                    "Session ID is required"
            );
            Objects.requireNonNull(
                    heatDuration,
                    "Heat duration is required"
            );

            // A null version is resolved from the event regulation.
            if (heatDuration.isNegative()
                    || heatDuration.isZero()
                    || durationBasis == null
                    || durationBasis.isBlank()) {
                throw new IllegalArgumentException(
                        "Explicit positive heat duration and basis required"
                );
            }
        }
    }
}