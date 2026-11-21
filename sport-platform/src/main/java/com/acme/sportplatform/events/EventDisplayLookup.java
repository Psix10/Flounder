package com.acme.sportplatform.events;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Internal display data lookup. Does not grant HTTP access to events. */
public interface EventDisplayLookup {
    Map<UUID, String> titles(Set<UUID> eventIds);
}
