package com.acme.sportplatform.organizations.application;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.organizations.VenueTimezoneLookup;
import com.acme.sportplatform.organizations.infrastructure.jpa.VenueRepository;

@Service
@Transactional(readOnly = true)
public class VenueTimezoneLookupService implements VenueTimezoneLookup {
    private final VenueRepository venues;

    public VenueTimezoneLookupService(VenueRepository venues) {
        this.venues = venues;
    }

    @Override
    public Map<UUID, String> timezones(Set<UUID> venueIds) {
        if (venueIds.isEmpty()) return Map.of();
        Map<UUID, String> result = new HashMap<>();
        venues.findAllById(venueIds).forEach(venue -> {
            if (venueIds.contains(venue.getId()) && venue.getTimezone() != null) {
                result.put(venue.getId(), venue.getTimezone());
            }
        });
        return Map.copyOf(result);
    }
}
