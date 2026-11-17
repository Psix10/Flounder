package com.acme.sportplatform.identity.api;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateUserRequest(
        @Email
        @NotBlank
        String email,

        @NotBlank
        String password,

        String phone,

        @NotBlank
        String firstName,

        @NotBlank
        String lastName,

        String middleName,

        @NotNull
        LocalDate birthDate,

        @NotBlank(message = "Participant gender is required")
        @Pattern(
                regexp = "MALE|FEMALE",
                message = "Gender must be MALE or FEMALE"
        )
        String gender,

        String city,
        String countryCode,
        String clubName
) {
}