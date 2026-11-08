package com.acme.sportplatform.protocol.domain.schedule;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ProtocolScheduleModel {
    private ProtocolScheduleModel() { }
    public enum Origin { EXPERIMENTAL, ORGANIZER_CONFIRMED }
    public enum Status { SCHEDULED, NOT_FIT_FIXED_ORDER }
    public record Window(Instant start, Instant end) {
        public Window {
            Objects.requireNonNull(start); Objects.requireNonNull(end);
            if (!start.isBefore(end)) throw new IllegalArgumentException("Window must have start < end");
        }
    }
    public record TimingPolicy(Duration minimumRest, Duration changeover,
                               Origin origin, String sourceDescription) {
        public TimingPolicy {
            Objects.requireNonNull(minimumRest); Objects.requireNonNull(changeover); Objects.requireNonNull(origin);
            if (minimumRest.isNegative() || changeover.isNegative()) throw new IllegalArgumentException("Negative timing parameter");
            if (sourceDescription == null || sourceDescription.isBlank()) throw new IllegalArgumentException("Timing source is required");
        }
    }
    public record Session(UUID id, Window window, List<Window> breaks) {
        public Session {
            Objects.requireNonNull(id); Objects.requireNonNull(window); breaks = List.copyOf(breaks);
            Instant previousEnd = window.start();
            for (Window pause : breaks) {
                if (pause.start().isBefore(previousEnd) || pause.end().isAfter(window.end())) {
                    throw new IllegalArgumentException("Breaks must be ordered, non-overlapping and within session");
                }
                previousEnd = pause.end();
            }
        }
    }
    /** duration is an explicit conservative planning input, not an observed race result. */
    public record Heat(UUID id, UUID sessionId, UUID disciplineId, List<UUID> athleteIds,
                       Duration duration, String durationBasis) {
        public Heat {
            Objects.requireNonNull(id); Objects.requireNonNull(sessionId); Objects.requireNonNull(disciplineId);
            athleteIds = List.copyOf(athleteIds); Objects.requireNonNull(duration);
            if (athleteIds.isEmpty() || duration.isZero() || duration.isNegative()) throw new IllegalArgumentException("Empty heat or non-positive duration");
            if (durationBasis == null || durationBasis.isBlank()) throw new IllegalArgumentException("Duration basis is required");
            if (new java.util.HashSet<>(athleteIds).size() != athleteIds.size()) throw new IllegalArgumentException("Duplicate athlete in heat");
        }
    }
    public record Input(TimingPolicy policy, List<Session> sessions, List<Heat> heats) {
        public Input {
            Objects.requireNonNull(policy); sessions = List.copyOf(sessions); heats = List.copyOf(heats);
            if (sessions.isEmpty()) throw new IllegalArgumentException("At least one session is required");
            java.util.Set<UUID> sessionIds = new java.util.HashSet<>(); Instant previousEnd = null;
            for (Session session : sessions) {
                if (!sessionIds.add(session.id())) throw new IllegalArgumentException("Duplicate session ID");
                if (previousEnd != null && session.window().start().isBefore(previousEnd)) {
                    throw new IllegalArgumentException("Sessions must be chronological and non-overlapping for one pool");
                }
                previousEnd = session.window().end();
            }
            java.util.Set<UUID> heatIds = new java.util.HashSet<>();
            for (Heat heat : heats) {
                if (!sessionIds.contains(heat.sessionId()) || !heatIds.add(heat.id())) {
                    throw new IllegalArgumentException("Unknown session or duplicate heat ID");
                }
            }
        }
    }
    public record ScheduledHeat(UUID heatId, UUID sessionId, UUID disciplineId, Instant start, Instant end) {
        public ScheduledHeat {
            Objects.requireNonNull(heatId); Objects.requireNonNull(sessionId); Objects.requireNonNull(disciplineId);
            Objects.requireNonNull(start); Objects.requireNonNull(end);
        }
    }
    public record Failure(String code, UUID heatId, UUID sessionId, Instant earliestStart,
                          Instant requiredEnd, Instant sessionEnd) { }
    public record Result(Status status, Origin origin, String timingSource,
                         List<ScheduledHeat> heats, Failure failure) {
        public Result {
            Objects.requireNonNull(status); Objects.requireNonNull(origin);
            Objects.requireNonNull(timingSource); heats = List.copyOf(heats);
            if ((status == Status.SCHEDULED && failure != null)
                    || (status == Status.NOT_FIT_FIXED_ORDER && (failure == null || !heats.isEmpty()))) {
                throw new IllegalArgumentException("Inconsistent schedule result");
            }
        }
    }
}
