package com.acme.sportplatform.registrations.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.registrations.api.RegistrationResponse;
import com.acme.sportplatform.registrations.api.ReviewRegistrationRequest;
import com.acme.sportplatform.registrations.domain.RegistrationReviewDecision;
import com.acme.sportplatform.registrations.domain.RegistrationStatus;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationEntity;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository;

@Service
public class ReviewRegistrationUseCase {

    private final RegistrationRepository registrationRepository;
    private final RegistrationMapper registrationMapper;

    public ReviewRegistrationUseCase(
            RegistrationRepository registrationRepository,
            RegistrationMapper registrationMapper
    ) {
        this.registrationRepository = registrationRepository;
        this.registrationMapper = registrationMapper;
    }

    @Transactional
    public RegistrationResponse execute(
            UUID registrationId,
            ReviewRegistrationRequest request
    ) {
        RegistrationEntity registration = registrationRepository
                .findById(registrationId)
                .orElseThrow(() -> new BusinessException(
                        "registrations.not_found",
                        "Registration not found"
                ));

        if (!RegistrationStatus.SUBMITTED.name()
                .equals(registration.getStatus())) {
            throw new BusinessException(
                    "registrations.not_submitted",
                    "Only a submitted registration can be reviewed"
            );
        }

        if (requiresReviewNote(request.decision())
                && (request.reviewNote() == null
                || request.reviewNote().isBlank())) {
            throw new BusinessException(
                    "registrations.review_note_required",
                    "A review note is required for this decision"
            );
        }

        registration.setStatus(toRegistrationStatus(request.decision()));
        registration.setReviewNote(request.reviewNote());
        registration.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        return registrationMapper.toResponse(
                registrationRepository.save(registration)
        );
    }

    private boolean requiresReviewNote(
            RegistrationReviewDecision decision
    ) {
        return decision == RegistrationReviewDecision.NEEDS_CORRECTION
                || decision == RegistrationReviewDecision.REJECTED;
    }

    private String toRegistrationStatus(
            RegistrationReviewDecision decision
    ) {
        return switch (decision) {
            case CONFIRMED -> RegistrationStatus.CONFIRMED.name();
            case NEEDS_CORRECTION ->
                    RegistrationStatus.NEEDS_CORRECTION.name();
            case REJECTED -> RegistrationStatus.REJECTED.name();
        };
    }
}