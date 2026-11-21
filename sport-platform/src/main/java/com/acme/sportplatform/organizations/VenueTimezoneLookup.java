package com.acme.sportplatform.organizations;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Internal display lookup; does not expose venue records through HTTP. */
public interface VenueTimezoneLookup {
    Map<UUID, String> timezones(Set<UUID> venueIds);
}
