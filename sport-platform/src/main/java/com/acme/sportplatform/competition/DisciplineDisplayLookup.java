package com.acme.sportplatform.competition;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Internal display data lookup. Callers must check the owning event. */
public interface DisciplineDisplayLookup {
    record Label(UUID eventId, String name) {}

    Map<UUID, Label> labels(Set<UUID> disciplineIds);
}
