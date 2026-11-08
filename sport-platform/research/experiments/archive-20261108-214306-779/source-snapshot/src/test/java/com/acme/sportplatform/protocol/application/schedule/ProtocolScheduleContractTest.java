package com.acme.sportplatform.protocol.domain.schedule;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.Heat;
import com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.Input;
import com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.Origin;
import com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.Result;
import com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.ScheduledHeat;
import com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.Session;
import com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.Status;
import com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.TimingPolicy;
import com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.Window;

class ProtocolScheduleContractTest {

    private static final Instant BASE =
            Instant.parse("2026-11-07T09:00:00Z");

    private final FixedOrderProtocolScheduler scheduler =
            new FixedOrderProtocolScheduler();

    private final ProtocolScheduleValidator validator =
            new ProtocolScheduleValidator();

    private UUID id(long value) {
        return new UUID(0L, value);
    }

    private Instant time(long seconds) {
        return BASE.plusSeconds(seconds);
    }

    private Window window(long start, long end) {
        return new Window(time(start), time(end));
    }

    private TimingPolicy policy(long rest, long changeover) {
        return new TimingPolicy(
                Duration.ofSeconds(rest),
                Duration.ofSeconds(changeover),
                Origin.EXPERIMENTAL,
                "Deterministic contract test"
        );
    }

    private Session session(
            long sessionId,
            long start,
            long end,
            Window... breaks
    ) {
        return new Session(
                id(sessionId),
                window(start, end),
                List.of(breaks)
        );
    }

    private Heat heat(
            long heatId,
            long sessionId,
            long athleteId,
            long duration
    ) {
        return new Heat(
                id(heatId),
                id(sessionId),
                id(1000 + heatId),
                List.of(id(athleteId)),
                Duration.ofSeconds(duration),
                "Explicit test duration"
        );
    }

    private ScheduledHeat scheduled(
            Heat source,
            long start
    ) {
        return new ScheduledHeat(
                source.id(),
                source.sessionId(),
                source.disciplineId(),
                time(start),
                time(start).plus(source.duration())
        );
    }

    private Result supplied(
            Input input,
            ScheduledHeat... heats
    ) {
        return new Result(
                Status.SCHEDULED,
                input.policy().origin(),
                input.policy().sourceDescription(),
                List.of(heats),
                null
        );
    }

    private Result accepted(Input input) {
        Result result = scheduler.schedule(input);

        assertEquals(Status.SCHEDULED, result.status());
        assertNull(result.failure());
        assertEquals(input.heats().size(), result.heats().size());

        assertTrue(
                validator.validate(input, result).isEmpty(),
                () -> "Unexpected violations: "
                        + validator.validate(input, result)
        );

        return result;
    }

    private void assertViolation(
            Input input,
            Result result,
            String code
    ) {
        var violations = validator.validate(input, result);

        assertTrue(
                violations.stream()
                        .anyMatch(v -> code.equals(v.code())),
                () -> "Expected " + code + ", got: " + violations
        );
    }

    @Test
    void schedulesDifferentAthletesWithChangeover() {
        var first = heat(1, 100, 10, 120);
        var second = heat(2, 100, 20, 120);

        var input = new Input(
                policy(600, 30),
                List.of(session(100, 0, 2000)),
                List.of(first, second)
        );

        var result = accepted(input);

        assertEquals(time(0), result.heats().get(0).start());
        assertEquals(time(120), result.heats().get(0).end());
        assertEquals(time(150), result.heats().get(1).start());
        assertEquals(time(270), result.heats().get(1).end());
    }

    @Test
    void measuresRestFromPreviousHeatEnd() {
        var first = heat(1, 100, 10, 120);
        var second = heat(2, 100, 10, 120);

        var input = new Input(
                policy(600, 30),
                List.of(session(100, 0, 2000)),
                List.of(first, second)
        );

        var result = accepted(input);

        assertEquals(time(720), result.heats().get(1).start());
        assertEquals(time(840), result.heats().get(1).end());
    }

    @Test
    void usesMaximumOfRestAndChangeoverRatherThanTheirSum() {
        var first = heat(1, 100, 10, 120);
        var second = heat(2, 100, 10, 120);

        var input = new Input(
                policy(20, 30),
                List.of(session(100, 0, 1000)),
                List.of(first, second)
        );

        var result = accepted(input);

        assertEquals(time(150), result.heats().get(1).start());
    }

    @Test
    void movesWholeHeatPastMultipleBreaks() {
        var first = heat(1, 100, 10, 120);

        var input = new Input(
                policy(0, 0),
                List.of(session(
                        100,
                        0,
                        1000,
                        window(60, 180),
                        window(240, 360)
                )),
                List.of(first)
        );

        var result = accepted(input);

        assertEquals(time(360), result.heats().get(0).start());
        assertEquals(time(480), result.heats().get(0).end());
    }

    @Test
    void acceptsExactBreakAndSessionBoundaries() {
        var first = heat(1, 100, 10, 120);
        var second = heat(2, 100, 20, 120);

        var input = new Input(
                policy(0, 0),
                List.of(session(
                        100,
                        0,
                        420,
                        window(120, 300)
                )),
                List.of(first, second)
        );

        var result = accepted(input);

        assertEquals(time(0), result.heats().get(0).start());
        assertEquals(time(120), result.heats().get(0).end());
        assertEquals(time(300), result.heats().get(1).start());
        assertEquals(time(420), result.heats().get(1).end());
    }

    @Test
    void preservesAthleteRestAcrossSessions() {
        var first = heat(1, 100, 10, 120);
        var second = heat(2, 200, 10, 120);

        var input = new Input(
                policy(600, 30),
                List.of(
                        session(100, 0, 120),
                        session(200, 200, 2000)
                ),
                List.of(first, second)
        );

        var result = accepted(input);

        assertEquals(time(720), result.heats().get(1).start());
    }

    @Test
    void preservesPoolChangeoverAcrossSessions() {
        var first = heat(1, 100, 10, 120);
        var second = heat(2, 200, 20, 120);

        var input = new Input(
                policy(0, 30),
                List.of(
                        session(100, 0, 120),
                        session(200, 120, 1000)
                ),
                List.of(first, second)
        );

        var result = accepted(input);

        assertEquals(time(150), result.heats().get(1).start());
    }

    @Test
    void preservesFixedOrderInsteadOfMovingLaterHeatIntoRestGap() {
        var first = heat(1, 100, 10, 120);
        var second = heat(2, 100, 10, 120);
        var third = heat(3, 100, 20, 120);

        var input = new Input(
                policy(600, 30),
                List.of(session(100, 0, 2000)),
                List.of(first, second, third)
        );

        var result = accepted(input);

        assertEquals(
                List.of(first.id(), second.id(), third.id()),
                result.heats().stream()
                        .map(ScheduledHeat::heatId)
                        .toList()
        );

        assertEquals(time(720), result.heats().get(1).start());
        assertEquals(time(870), result.heats().get(2).start());
    }

    @Test
    void refusesTooSmallWindowWithoutPartialSchedule() {
        var first = heat(1, 100, 10, 120);
        var second = heat(2, 100, 20, 120);

        var input = new Input(
                policy(0, 30),
                List.of(session(100, 0, 269)),
                List.of(first, second)
        );

        var result = scheduler.schedule(input);

        assertEquals(
                Status.NOT_FIT_FIXED_ORDER,
                result.status()
        );

        assertTrue(result.heats().isEmpty());
        assertNotNull(result.failure());

        assertEquals(
                "SESSION_WINDOW_EXCEEDED",
                result.failure().code()
        );

        assertEquals(second.id(), result.failure().heatId());
        assertEquals(time(150), result.failure().earliestStart());
        assertEquals(time(270), result.failure().requiredEnd());
        assertEquals(time(269), result.failure().sessionEnd());
    }

    @Test
    void validatorDetectsInsufficientRestInSuppliedSchedule() {
        var first = heat(1, 100, 10, 120);
        var second = heat(2, 100, 10, 120);

        var input = new Input(
                policy(600, 30),
                List.of(session(100, 0, 2000)),
                List.of(first, second)
        );

        var invalid = supplied(
                input,
                scheduled(first, 0),
                scheduled(second, 150)
        );

        assertViolation(input, invalid, "REST_TOO_SHORT");
    }

    @Test
    void validatorDetectsPoolOverlap() {
        var first = heat(1, 100, 10, 120);
        var second = heat(2, 100, 20, 120);

        var input = new Input(
                policy(0, 30),
                List.of(session(100, 0, 1000)),
                List.of(first, second)
        );

        var invalid = supplied(
                input,
                scheduled(first, 0),
                scheduled(second, 100)
        );

        assertViolation(input, invalid, "POOL_GAP_TOO_SHORT");
    }

    @Test
    void validatorDetectsBreakOverlap() {
        var first = heat(1, 100, 10, 120);

        var input = new Input(
                policy(0, 0),
                List.of(session(
                        100,
                        0,
                        1000,
                        window(60, 180)
                )),
                List.of(first)
        );

        assertViolation(
                input,
                supplied(input, scheduled(first, 0)),
                "BREAK_OVERLAP"
        );
    }

    @Test
    void validatorDetectsMissingAndDuplicateHeats() {
        var first = heat(1, 100, 10, 120);
        var second = heat(2, 100, 20, 120);

        var input = new Input(
                policy(0, 0),
                List.of(session(100, 0, 1000)),
                List.of(first, second)
        );

        assertViolation(
                input,
                supplied(input, scheduled(first, 0)),
                "MISSING_HEAT"
        );

        assertViolation(
                input,
                supplied(
                        input,
                        scheduled(first, 0),
                        scheduled(first, 120),
                        scheduled(second, 240)
                ),
                "DUPLICATE_HEAT"
        );
    }

    @Test
    void validatorDetectsChangedProgramOrder() {
        var first = heat(1, 100, 10, 120);
        var second = heat(2, 100, 20, 120);

        var input = new Input(
                policy(0, 0),
                List.of(session(100, 0, 1000)),
                List.of(first, second)
        );

        var invalid = supplied(
                input,
                scheduled(second, 0),
                scheduled(first, 120)
        );

        assertViolation(input, invalid, "PROGRAM_ORDER_MISMATCH");
    }
}