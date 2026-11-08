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
            UUID reviewerUserId,
            ReviewRegistrationRequest request
    ) {
        RegistrationEntity registration = registrationRepository
                .findForUpdate(registrationId)
                .orElseThrow(() -> new BusinessException(
                        "registrations.notfound",
                        "Registration not found"
                ));

        if (!RegistrationStatus.SUBMITTED.name()
                .equals(registration.getStatus())) {
            throw new BusinessException(
                    "registrations.notreviewable",
                    "Only submitted registrations can be reviewed"
            );
        }

        String reviewNote = request.reviewNote() == null
                ? ""
                : request.reviewNote().trim();

        if (requiresReviewNote(request.decision()) && reviewNote.isBlank()) {
            throw new BusinessException(
                    "registrations.review_note_required",
                    "Review note is required"
            );
        }

        RegistrationStatus newStatus = switch (request.decision()) {
            case CONFIRMED -> RegistrationStatus.CONFIRMED;
            case REJECTED -> RegistrationStatus.REJECTED;
            case NEEDS_CORRECTION -> RegistrationStatus.NEEDS_CORRECTION;
        };

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        registration.setStatus(newStatus.name());
        registration.setReviewNote(reviewNote);
        registration.setReviewedByUserId(reviewerUserId);
        registration.setReviewedAt(now);
        registration.setUpdatedAt(now);

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
}