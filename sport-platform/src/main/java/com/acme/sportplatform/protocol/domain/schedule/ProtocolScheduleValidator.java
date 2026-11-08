package com.acme.sportplatform.protocol.domain.schedule;

import java.time.Duration;
import java.util.*;
import static com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.*;

/** Audits a supplied plan without invoking or re-running the scheduler. */
public final class ProtocolScheduleValidator {
    public record Violation(String code, UUID heatId, UUID athleteId, String detail) { }
    public List<Violation> validate(Input input, Result result) {
        List<Violation> errors = new ArrayList<>();
        if (result.status() != Status.SCHEDULED) {
            errors.add(new Violation("SCHEDULE_NOT_BUILT", null, null, "No accepted schedule to audit"));
            return List.copyOf(errors);
        }
        if (result.origin() != input.policy().origin() || !result.timingSource().equals(input.policy().sourceDescription())) {
            errors.add(new Violation("TIMING_PROVENANCE_MISMATCH", null, null, "Timing source differs from input"));
        }
        Map<UUID, Heat> heats = new HashMap<>(); input.heats().forEach(h -> heats.put(h.id(), h));
        Map<UUID, Session> sessions = new HashMap<>(); input.sessions().forEach(s -> sessions.put(s.id(), s));
        Set<UUID> seen = new HashSet<>(); Map<UUID, List<ScheduledHeat>> byAthlete = new HashMap<>();
        for (ScheduledHeat item : result.heats()) {
            if (!seen.add(item.heatId())) errors.add(new Violation("DUPLICATE_HEAT", item.heatId(), null, "Heat occurs more than once"));
            Heat source = heats.get(item.heatId());
            if (source == null) {
                errors.add(new Violation("UNKNOWN_HEAT", item.heatId(), null, "Heat not in input")); continue;
            }
            if (!source.sessionId().equals(item.sessionId()) || !source.disciplineId().equals(item.disciplineId())) {
                errors.add(new Violation("HEAT_IDENTITY_MISMATCH", item.heatId(), null, "Session or discipline changed"));
            }
            if (!Duration.between(item.start(), item.end()).equals(source.duration())) {
                errors.add(new Violation("DURATION_MISMATCH", item.heatId(), null, "Duration differs from declared planning input"));
            }
            Session session = sessions.get(source.sessionId());
            if (item.start().isBefore(session.window().start()) || item.end().isAfter(session.window().end())
                    || !item.start().isBefore(item.end())) {
                errors.add(new Violation("OUTSIDE_SESSION", item.heatId(), null, "Heat outside session or invalid interval"));
            }
            for (Window pause : session.breaks()) {
                if (item.start().isBefore(pause.end()) && item.end().isAfter(pause.start())) {
                    errors.add(new Violation("BREAK_OVERLAP", item.heatId(), null, "Heat overlaps mandatory break"));
                }
            }
            for (UUID athlete : source.athleteIds()) byAthlete.computeIfAbsent(athlete, k -> new ArrayList<>()).add(item);
        }
        for (Heat source : input.heats()) if (!seen.contains(source.id())) {
            errors.add(new Violation("MISSING_HEAT", source.id(), null, "Expected heat missing"));
        }
        List<ScheduledHeat> chronological = result.heats().stream()
                .sorted(Comparator.comparing(ScheduledHeat::start).thenComparing(ScheduledHeat::end)).toList();
        List<UUID> expectedOrder = new ArrayList<>();
        for (Session session : input.sessions()) for (Heat heat : input.heats()) {
            if (heat.sessionId().equals(session.id())) expectedOrder.add(heat.id());
        }
        if (!chronological.stream().map(ScheduledHeat::heatId).toList().equals(expectedOrder)) {
            errors.add(new Violation("PROGRAM_ORDER_MISMATCH", null, null, "Actual order differs from fixed program"));
        }
        for (int i = 1; i < chronological.size(); i++) {
            ScheduledHeat a = chronological.get(i - 1), b = chronological.get(i);
            if (Duration.between(a.end(), b.start()).compareTo(input.policy().changeover()) < 0) {
                errors.add(new Violation("POOL_GAP_TOO_SHORT", b.heatId(), null, "Overlap or insufficient changeover"));
            }
        }
        byAthlete.forEach((athlete, appearances) -> {
            appearances.sort(Comparator.comparing(ScheduledHeat::start));
            for (int i = 1; i < appearances.size(); i++) {
                ScheduledHeat a = appearances.get(i - 1), b = appearances.get(i);
                if (Duration.between(a.end(), b.start()).compareTo(input.policy().minimumRest()) < 0) {
                    errors.add(new Violation("REST_TOO_SHORT", b.heatId(), athlete, "Insufficient rest from previous heat end"));
                }
            }
        });
        return List.copyOf(errors);
    }
}
