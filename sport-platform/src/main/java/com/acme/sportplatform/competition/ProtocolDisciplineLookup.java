package com.acme.sportplatform.competition;
import java.util.UUID;
public interface ProtocolDisciplineLookup {
    View lock(UUID disciplineId);
    record View(
        UUID id, 
        UUID sportId, 
        UUID organizationId,
        String competitionFormat, 
        String unitType) { }
}
