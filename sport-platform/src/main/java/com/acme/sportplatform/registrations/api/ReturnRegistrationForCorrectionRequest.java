package com.acme.sportplatform.registrations.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReturnRegistrationForCorrectionRequest(
        @NotBlank
        @Size(max = 2000)
        String reviewNote
) {
}