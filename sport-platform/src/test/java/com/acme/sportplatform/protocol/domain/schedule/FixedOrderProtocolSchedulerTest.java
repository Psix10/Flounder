package com.acme.sportplatform.protocol.domain.schedule;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.*;

class FixedOrderProtocolSchedulerTest {
    private static final Instant T = Instant.parse("2026-11-22T06:30:00Z");
    private static final UUID SESSION = id(1), A = id(100), B = id(101), DISCIPLINE = id(200);
    private final FixedOrderProtocolScheduler scheduler = new FixedOrderProtocolScheduler();
    private final ProtocolScheduleValidator validator = new ProtocolScheduleValidator();
    private static UUID id(long value) { return new UUID(0, value); }
    private TimingPolicy policy() {
        return new TimingPolicy(Duration.ofMinutes(10), Duration.ofSeconds(30), Origin.EXPERIMENTAL, "unit-test-only values, not regulation");
    }
    private Session session(long lengthSeconds, List<Window> breaks) {
        return new Session(SESSION, new Window(T, T.plusSeconds(lengthSeconds)), breaks);
    }
    private Heat heat(long number, UUID athlete) {
        return new Heat(id(number), SESSION, DISCIPLINE, List.of(athlete), Duration.ofSeconds(120), "explicit test planning duration");
    }
    private Input input(Heat... heats) { return new Input(policy(), List.of(session(3600, List.of())), List.of(heats)); }
    private Result plan(ScheduledHeat... heats) {
        return new Result(Status.SCHEDULED, Origin.EXPERIMENTAL, policy().sourceDescription(), List.of(heats), null);
    }
    private ScheduledHeat scheduled(Heat heat, long start, long end) {
        return new ScheduledHeat(heat.id(), heat.sessionId(), heat.disciplineId(), T.plusSeconds(start), T.plusSeconds(end));
    }
    private boolean has(Input input, Result result, String code) {
        return validator.validate(input, result).stream().anyMatch(v -> v.code().equals(code));
    }
    @Test void schedulesDifferentAthletesWithChangeoverOnly() {
        var input = input(heat(10, A), heat(11, B)); var result = scheduler.schedule(input);
        assertEquals(Status.SCHEDULED, result.status());
        assertEquals(T, result.heats().get(0).start());
        assertEquals(T.plusSeconds(150), result.heats().get(1).start());
        assertTrue(validator.validate(input, result).isEmpty());
    }
    @Test void waitsForSameAthleteRestFromPreviousHeatEnd() {
        var input = input(heat(10, A), heat(11, A)); var result = scheduler.schedule(input);
        assertEquals(T.plusSeconds(720), result.heats().get(1).start());
        assertTrue(validator.validate(input, result).isEmpty());
    }
    @Test void tracksRestAcrossDifferentDisciplines() {
        Heat second = new Heat(id(11), SESSION, id(201), List.of(A), Duration.ofSeconds(60), "test");
        var input = input(heat(10, A), second); var result = scheduler.schedule(input);
        assertEquals(T.plusSeconds(720), result.heats().get(1).start());
        assertTrue(validator.validate(input, result).isEmpty());
    }
    @Test void mandatoryBreakMovesHeatAfterBreak() {
        var input = new Input(policy(), List.of(session(3600, List.of(new Window(T.plusSeconds(180), T.plusSeconds(300))))),
                List.of(heat(10, A), heat(11, B)));
        var result = scheduler.schedule(input);
        assertEquals(T.plusSeconds(300), result.heats().get(1).start());
        assertTrue(validator.validate(input, result).isEmpty());
    }
    @Test void endingExactlyAtBreakStartIsAllowed() {
        var input = new Input(policy(), List.of(session(3600, List.of(new Window(T.plusSeconds(120), T.plusSeconds(200))))),
                List.of(heat(10, A)));
        var result = scheduler.schedule(input);
        assertEquals(T, result.heats().get(0).start()); assertTrue(validator.validate(input, result).isEmpty());
    }
    @Test void failureReturnsNoPartialSchedule() {
        var input = new Input(policy(), List.of(session(200, List.of())), List.of(heat(10, A), heat(11, B)));
        var result = scheduler.schedule(input);
        assertEquals(Status.NOT_FIT_FIXED_ORDER, result.status()); assertTrue(result.heats().isEmpty());
        assertEquals("SESSION_WINDOW_EXCEEDED", result.failure().code()); assertEquals(id(11), result.failure().heatId());
        assertEquals(T.plusSeconds(270), result.failure().requiredEnd());
    }
    @Test void endingExactlyAtSessionDeadlineIsAllowed() {
        var input = new Input(policy(), List.of(session(120, List.of())), List.of(heat(10, A)));
        var result = scheduler.schedule(input);
        assertEquals(Status.SCHEDULED, result.status()); assertTrue(validator.validate(input, result).isEmpty());
    }
    @Test void restIsRetainedAcrossSessions() {
        Session first = session(240, List.of());
        Session second = new Session(id(2), new Window(T.plusSeconds(300), T.plusSeconds(3600)), List.of());
        Heat next = new Heat(id(11), id(2), id(201), List.of(A), Duration.ofSeconds(120), "test");
        var input = new Input(policy(), List.of(first, second), List.of(heat(10, A), next));
        var result = scheduler.schedule(input);
        assertEquals(T.plusSeconds(720), result.heats().get(1).start()); assertTrue(validator.validate(input, result).isEmpty());
    }
    @Test void technicalGapIsRetainedAcrossAdjacentSessions() {
        Session first = session(120, List.of());
        Session second = new Session(id(2), new Window(T.plusSeconds(120), T.plusSeconds(600)), List.of());
        Heat next = new Heat(id(11), id(2), id(201), List.of(B), Duration.ofSeconds(120), "test");
        var input = new Input(policy(), List.of(first, second), List.of(heat(10, A), next));
        var result = scheduler.schedule(input);
        assertEquals(T.plusSeconds(150), result.heats().get(1).start()); assertTrue(validator.validate(input, result).isEmpty());
    }
    @Test void rejectsNegativeRest() {
        assertThrows(IllegalArgumentException.class, () -> new TimingPolicy(Duration.ofSeconds(-1), Duration.ZERO, Origin.EXPERIMENTAL, "test"));
    }
    @Test void rejectsDuplicateHeatIds() {
        assertThrows(IllegalArgumentException.class, () -> input(heat(10, A), heat(10, B)));
    }
    @Test void rejectsUnknownSession() {
        var heat = new Heat(id(10), id(999), DISCIPLINE, List.of(A), Duration.ofSeconds(120), "test");
        assertThrows(IllegalArgumentException.class, () -> input(heat));
    }
    @Test void rejectsBreakOutsideSession() {
        assertThrows(IllegalArgumentException.class, () -> session(120, List.of(new Window(T.plusSeconds(100), T.plusSeconds(150)))));
    }
    @Test void requiresExplicitDurationBasis() {
        assertThrows(IllegalArgumentException.class, () -> new Heat(id(10), SESSION, DISCIPLINE, List.of(A), Duration.ofSeconds(120), ""));
    }
    @Test void rejectsDuplicateAthleteInsideHeat() {
        assertThrows(IllegalArgumentException.class, () -> new Heat(id(10), SESSION, DISCIPLINE, List.of(A,A), Duration.ofSeconds(120), "test"));
    }
    @Test void validatorDetectsRestViolationWithoutScheduler() {
        Heat a = heat(10, A), b = heat(11, A); var input = input(a,b);
        assertTrue(has(input, plan(scheduled(a,0,120), scheduled(b,150,270)), "REST_TOO_SHORT"));
    }
    @Test void validatorDetectsPoolOverlap() {
        Heat a = heat(10, A), b = heat(11, B); var input = input(a,b);
        assertTrue(has(input, plan(scheduled(a,0,120), scheduled(b,100,220)), "POOL_GAP_TOO_SHORT"));
    }
    @Test void validatorDetectsBreakOverlap() {
        Heat a = heat(10, A);
        var input = new Input(policy(), List.of(session(3600, List.of(new Window(T.plusSeconds(100), T.plusSeconds(200))))), List.of(a));
        assertTrue(has(input, plan(scheduled(a,0,120)), "BREAK_OVERLAP"));
    }
    @Test void validatorDetectsMissingHeat() {
        Heat a = heat(10, A), b = heat(11, B);
        assertTrue(has(input(a,b), plan(scheduled(a,0,120)), "MISSING_HEAT"));
    }
    @Test void validatorDetectsChangedDuration() {
        Heat a = heat(10, A);
        assertTrue(has(input(a), plan(scheduled(a,0,100)), "DURATION_MISMATCH"));
    }
    @Test void validatorDetectsDuplicateHeat() {
        Heat a = heat(10, A);
        assertTrue(has(input(a), plan(scheduled(a,0,120), scheduled(a,150,270)), "DUPLICATE_HEAT"));
    }
    @Test void validatorDetectsChangedProgramOrder() {
        Heat a = heat(10, A), b = heat(11, B);
        assertTrue(has(input(a,b), plan(scheduled(b,0,120), scheduled(a,150,270)), "PROGRAM_ORDER_MISMATCH"));
    }
    @Test void validatorDetectsDeadlineOverrun() {
        Heat a = heat(10, A); var input = new Input(policy(), List.of(session(120,List.of())), List.of(a));
        assertTrue(has(input, plan(scheduled(a,30,150)), "OUTSIDE_SESSION"));
    }
    @Test void validatorDoesNotAcceptFailedScheduleAsValid() {
        var input = new Input(policy(), List.of(session(60,List.of())), List.of(heat(10,A)));
        assertTrue(has(input, scheduler.schedule(input), "SCHEDULE_NOT_BUILT"));
    }
    @Test void validatorChecksParameterProvenance() {
        Heat a = heat(10,A);
        var wrong = new Result(Status.SCHEDULED, Origin.ORGANIZER_CONFIRMED, "not the original source", List.of(scheduled(a,0,120)), null);
        assertTrue(has(input(a), wrong, "TIMING_PROVENANCE_MISMATCH"));
    }
    @Test void zeroRestAndZeroGapAreExplicitlyAllowed() {
        var input = new Input(new TimingPolicy(Duration.ZERO, Duration.ZERO, Origin.EXPERIMENTAL, "explicit test zero"),
                List.of(session(240,List.of())), List.of(heat(10,A),heat(11,A)));
        var result = scheduler.schedule(input);
        assertEquals(T.plusSeconds(120), result.heats().get(1).start()); assertTrue(validator.validate(input,result).isEmpty());
    }
}
