package com.acme.sportplatform.registrations.web;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acme.sportplatform.registrations.api.RegistrationResponse;
import com.acme.sportplatform.registrations.api.ReturnRegistrationForCorrectionRequest;
import com.acme.sportplatform.registrations.application.ReturnRegistrationForCorrectionUseCase;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/registrations")
public class RegistrationCorrectionReviewController {

    private final ReturnRegistrationForCorrectionUseCase returnForCorrection;

    public RegistrationCorrectionReviewController(
            ReturnRegistrationForCorrectionUseCase returnForCorrection
    ) {
        this.returnForCorrection = returnForCorrection;
    }

    @PostMapping("/{registrationId}/return-for-correction")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER')")
    public RegistrationResponse returnForCorrection(
            @PathVariable UUID registrationId,
            @Valid @RequestBody
            ReturnRegistrationForCorrectionRequest request
    ) {
        return returnForCorrection.execute(
                registrationId,
                request
        );
    }
}