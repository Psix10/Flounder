package com.acme.sportplatform.registrations.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResubmitRegistrationRequest(
        @NotBlank
        @Size(max = 2000)
        String reply,

        @NotBlank
        @Size(max = 200)
        String emergencyContactName,

        @NotBlank
        @Size(max = 50)
        String emergencyContactPhone,

        boolean medicalCertificate,

        @AssertTrue(message = "Agreement to the rules is required")
        boolean agreeToRules,

        boolean refreshGenderFromProfile
) {

    public ResubmitRegistrationRequest(
            String reply,
            String emergencyContactName,
            String emergencyContactPhone,
            boolean medicalCertificate,
            boolean agreeToRules
    ) {
        this(
                reply,
                emergencyContactName,
                emergencyContactPhone,
                medicalCertificate,
                agreeToRules,
                false
        );
    }
}