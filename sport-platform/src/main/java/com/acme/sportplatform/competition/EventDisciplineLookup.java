package com.acme.sportplatform.competition;

import java.util.UUID;

public interface EventDisciplineLookup {

    EventDisciplineLookupResult getPublishedById(UUID eventDisciplineId);
}