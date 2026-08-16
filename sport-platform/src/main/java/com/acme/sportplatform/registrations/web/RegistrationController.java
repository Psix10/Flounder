package com.acme.sportplatform.registrations.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acme.sportplatform.registrations.api.CreateRegistrationRequest;
import com.acme.sportplatform.registrations.api.RegistrationResponse;
import com.acme.sportplatform.registrations.api.ReviewRegistrationRequest;
import com.acme.sportplatform.registrations.application.CreateRegistrationUseCase;
import com.acme.sportplatform.registrations.application.ListMyRegistrationsUseCase;
import com.acme.sportplatform.registrations.application.ReviewRegistrationUseCase;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/registrations")
public class RegistrationController {

        private final CreateRegistrationUseCase createRegistrationUseCase;
        private final ReviewRegistrationUseCase reviewRegistrationUseCase;
        private final ListMyRegistrationsUseCase listMyRegistrationsUseCase;

        public RegistrationController(
                CreateRegistrationUseCase createRegistrationUseCase,
                ReviewRegistrationUseCase reviewRegistrationUseCase,
                ListMyRegistrationsUseCase listMyRegistrationsUseCase
        ) {
                this.createRegistrationUseCase = createRegistrationUseCase;
                this.reviewRegistrationUseCase = reviewRegistrationUseCase;
                this.listMyRegistrationsUseCase = listMyRegistrationsUseCase;
        }

        @PostMapping
        @PreAuthorize("hasRole('PARTICIPANT')")
        public ResponseEntity<RegistrationResponse> create(
                @AuthenticationPrincipal(expression = "userId")
                UUID participantUserId,
                @RequestBody @Valid CreateRegistrationRequest request
        ) {
                return ResponseEntity.ok(
                        createRegistrationUseCase.execute(
                                participantUserId,
                                request
                        )
                );
        }

        @PostMapping("/{registrationId}/review")
        @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasRole('ORGANIZER')")
        public ResponseEntity<RegistrationResponse> review(
                @PathVariable UUID registrationId,
                @RequestBody @Valid ReviewRegistrationRequest request
        ) {
        return ResponseEntity.ok(
                reviewRegistrationUseCase.execute(registrationId, request)
                );
        }

        @GetMapping("/me")
        @PreAuthorize("hasRole('PARTICIPANT')")
        public ResponseEntity<List<RegistrationResponse>> listMine(
                @AuthenticationPrincipal(expression = "userId")
                UUID participantUserId
        ) {
        return ResponseEntity.ok(
                listMyRegistrationsUseCase.execute(participantUserId)
        );
        }
}