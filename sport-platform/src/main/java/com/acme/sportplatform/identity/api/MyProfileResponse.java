package com.acme.sportplatform.identity.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MyProfileResponse(
        UUID id,
        UUID userId,
        String firstName,
        String lastName,
        String middleName,
        LocalDate birthDate,
        String gender,
        String city,
        String countryCode,
        String clubName,
        OffsetDateTime updatedAt
) {
}