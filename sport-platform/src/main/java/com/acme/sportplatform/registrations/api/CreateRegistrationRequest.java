package com.acme.sportplatform.registrations.api;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record CreateRegistrationRequest(

        @NotNull
        UUID eventDisciplineId,

        JsonNode registrationMeta
) {

    @JsonIgnore
    @AssertTrue(
            message = "Registration metadata must be an object; "
                    + "seedTimeSeconds must be a positive number "
                    + "with at most two decimal places, or absent/null"
    )
    public boolean isRegistrationMetaValid() {
        return com.acme.sportplatform.common.numeric.SeedTimeValue.metadataIsValid(registrationMeta);
    }
}
