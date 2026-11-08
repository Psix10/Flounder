package com.acme.sportplatform.protocol.application.schedule;
import java.util.*;
import com.acme.sportplatform.protocol.application.ProtocolGenerationResult;
import com.acme.sportplatform.protocol.application.schedule.ScheduledProtocolBinding.BoundHeat;
public record SessionProtocolGenerationResult(UUID snapshotId, UUID eventId, int revisionNo,
        List<ProtocolGenerationResult> disciplines, List<BoundHeat> schedule) {
    public SessionProtocolGenerationResult {
        disciplines=List.copyOf(disciplines); schedule=List.copyOf(schedule);
    }
}
