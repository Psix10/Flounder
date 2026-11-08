package com.acme.sportplatform.protocol.domain.schedule;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.*;

/** One pool, fixed session/program order. Does not change heat membership or lanes. */
public final class FixedOrderProtocolScheduler {
    public Result schedule(Input input) {
        Map<UUID, Instant> athleteReady = new HashMap<>();
        ArrayList<ScheduledHeat> scheduled = new ArrayList<>();
        Instant poolReady = null;
        for (Session session : input.sessions()) {
            Instant cursor = session.window().start();
            if (poolReady != null && poolReady.isAfter(cursor)) cursor = poolReady;
            for (Heat heat : input.heats()) {
                if (!heat.sessionId().equals(session.id())) continue;
                Instant start = cursor;
                for (UUID athlete : heat.athleteIds()) {
                    Instant ready = athleteReady.get(athlete);
                    if (ready != null && ready.isAfter(start)) start = ready;
                }
                for (Window pause : session.breaks()) {
                    Instant end = start.plus(heat.duration());
                    if (start.isBefore(pause.end()) && end.isAfter(pause.start())) start = pause.end();
                }
                Instant end = start.plus(heat.duration());
                if (end.isAfter(session.window().end())) {
                    return new Result(Status.NOT_FIT_FIXED_ORDER, input.policy().origin(), input.policy().sourceDescription(),
                            java.util.List.of(), new Failure("SESSION_WINDOW_EXCEEDED", heat.id(), session.id(),
                            start, end, session.window().end()));
                }
                scheduled.add(new ScheduledHeat(heat.id(), session.id(), heat.disciplineId(), start, end));
                for (UUID athlete : heat.athleteIds()) athleteReady.put(athlete, end.plus(input.policy().minimumRest()));
                cursor = end.plus(input.policy().changeover()); poolReady = cursor;
            }
        }
        return new Result(Status.SCHEDULED, input.policy().origin(), input.policy().sourceDescription(), scheduled, null);
    }
}
