package com.acme.sportplatform.registrations.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WithdrawConfirmedRegistrationRequest(
        @NotBlank @Size(max = 2000) String reason
) {
}
