package com.acme.sportplatform.competition;
import java.util.UUID;
public interface DemoDisciplineConstraints {
    record View(Integer participantLimit, String resultType, String rankingStrategy, String settingsJson) {
        public View(Integer participantLimit, String resultType, String rankingStrategy) {
            this(participantLimit, resultType, rankingStrategy, "{}");
        }
    }
    View read(UUID disciplineId);
}
