package com.acme.sportplatform.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateMyProfileRequest(

        @NotBlank(message = "Participant gender is required")
        @Pattern(
                regexp = "MALE|FEMALE",
                message = "Gender must be MALE or FEMALE"
        )
        String gender
) {
}