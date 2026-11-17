package com.acme.sportplatform.competition;
import java.util.UUID;
public interface DemoDisciplineConstraints {
    record View(Integer participantLimit, String resultType, String rankingStrategy) { }
    View read(UUID disciplineId);
}
