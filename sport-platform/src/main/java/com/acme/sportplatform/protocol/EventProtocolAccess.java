package com.acme.sportplatform.protocol;

import java.util.UUID;

public interface EventProtocolAccess {

    PublishedEventDisciplineView getPublishedEventDiscipline(UUID eventDisciplineId);

    record PublishedEventDisciplineView(
            UUID id,
            String settingsJson
    ) {
    }
}