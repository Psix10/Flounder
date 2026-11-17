package com.acme.sportplatform.registrations.web;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.acme.sportplatform.registrations.api.RegistrationResponse;
import com.acme.sportplatform.registrations.api.WithdrawConfirmedRegistrationRequest;
import com.acme.sportplatform.registrations.application.WithdrawConfirmedRegistrationUseCase;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/registrations")
public class RegistrationWithdrawalController {
    private final WithdrawConfirmedRegistrationUseCase withdrawal;

    public RegistrationWithdrawalController(WithdrawConfirmedRegistrationUseCase withdrawal) {
        this.withdrawal = withdrawal;
    }

    @PostMapping("/{registrationId}/withdraw-confirmed")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER')")
    public RegistrationResponse withdraw(
            @PathVariable UUID registrationId,
            @Valid @RequestBody WithdrawConfirmedRegistrationRequest request
    ) {
        return withdrawal.execute(registrationId, request);
    }
}
