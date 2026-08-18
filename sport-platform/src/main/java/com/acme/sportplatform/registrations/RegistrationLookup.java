package com.acme.sportplatform.registrations;

import java.util.UUID;

public interface RegistrationLookup {

    RegistrationLookupResult getById(UUID registrationId);
}