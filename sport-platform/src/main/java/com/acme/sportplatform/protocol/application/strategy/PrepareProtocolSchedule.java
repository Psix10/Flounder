package com.acme.sportplatform.protocol.application.schedule;

import java.util.List;
import com.acme.sportplatform.protocol.domain.schedule.FixedOrderProtocolScheduler;
import com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleValidator;
import static com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.*;
import com.acme.sportplatform.protocol.application.schedule.ProtocolScheduleInputAdapter.DisciplinePlan;

/** In-memory preparation only. Deliberately not a Spring service or HTTP use case. */
public final class PrepareProtocolSchedule {
    public record PreparedSchedule(Input input, Result result) { }
    public PreparedSchedule execute(TimingPolicy policy, List<Session> sessions, List<DisciplinePlan> plans) {
        Input input = new ProtocolScheduleInputAdapter().adapt(policy, sessions, plans);
        Result result = new FixedOrderProtocolScheduler().schedule(input);
        if (result.status() == Status.SCHEDULED) {
            var violations = new ProtocolScheduleValidator().validate(input, result);
            if (!violations.isEmpty()) throw new IllegalStateException("Generated schedule failed independent validation: " + violations);
        }
        return new PreparedSchedule(input, result);
    }
}
