package com.acme.sportplatform.registrations.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.events.EventOperationAccess;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
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
    private final EventOperationAccess eventAccess;
    private final com.acme.sportplatform.registrations.RegistrationProtocolGuard protocolGuard;
    private final com.acme.sportplatform.competition.ProtocolDisciplineLookup disciplines;
    private final jakarta.persistence.EntityManager entityManager;

    public ReviewRegistrationUseCase(
            RegistrationRepository registrationRepository,
            RegistrationMapper registrationMapper,
            EventOperationAccess eventAccess,
            com.acme.sportplatform.registrations.RegistrationProtocolGuard protocolGuard,
            com.acme.sportplatform.competition.ProtocolDisciplineLookup disciplines,
            jakarta.persistence.EntityManager entityManager
    ) {
        this.registrationRepository = registrationRepository;
        this.registrationMapper = registrationMapper;
        this.eventAccess = eventAccess;
        this.protocolGuard = protocolGuard;
        this.disciplines = disciplines;
        this.entityManager = entityManager;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public RegistrationResponse execute(
            UUID registrationId,
            UUID reviewerUserId,
            ReviewRegistrationRequest request
    ) {
        var authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal()
                     instanceof AuthenticatedUserPrincipal principal)
                || principal.getUserId() == null) {
            throw new AccessDeniedException(
                    "Authenticated platform principal is required"
            );
        }

        UUID actorId = principal.getUserId();

        if (!actorId.equals(reviewerUserId)) {
            throw new AccessDeniedException(
                    "Reviewer must match authenticated user"
            );
        }

        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        "ROLE_PLATFORM_ADMIN".equals(
                                authority.getAuthority()
                        )
                );

        boolean organizer = authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        "ROLE_ORGANIZER".equals(
                                authority.getAuthority()
                        )
                );

        if (!admin && !organizer) {
            throw new AccessDeniedException(
                    "Organizer access is required"
            );
        }

        // Read scope without a row lock, authorize before waiting on shared locks.
        RegistrationEntity observed = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new BusinessException(
                        "registrations.notfound", "Registration not found"));
        if (!admin && !eventAccess.canManageEvent(observed.getEventId(), actorId)) {
            throw new AccessDeniedException("You cannot review registrations for this event");
        }
        UUID eventId = observed.getEventId();
        UUID disciplineId = observed.getEventDisciplineId();
        if (eventId == null || disciplineId == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "В заявке отсутствует мероприятие или дисциплина.");
        }
        protocolGuard.lockEventBatch(eventId);
        var discipline = disciplines.lock(disciplineId);
        if (!eventId.equals(discipline.eventId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "Дисциплина заявки не принадлежит мероприятию.");
        }

        RegistrationEntity registration = registrationRepository
                .findForUpdate(registrationId)
                .orElseThrow(() -> new BusinessException(
                        "registrations.notfound",
                        "Registration not found"
                ));

        // The initial read may be stale after waiting for the discipline lock.
        entityManager.refresh(registration);
        if (!eventId.equals(registration.getEventId())
                || !disciplineId.equals(registration.getEventDisciplineId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "Мероприятие или дисциплина заявки изменились. Обновите данные.");
        }

        if (!admin && !eventAccess.canManageEvent(
                registration.getEventId(),
                actorId
        )) {
            throw new AccessDeniedException(
                    "You cannot review registrations for this event"
            );
        }

        if (!RegistrationStatus.SUBMITTED.name()
                .equals(registration.getStatus())) {
            throw new BusinessException(
                    "registrations.notreviewable",
                    "Only submitted registrations can be reviewed"
            );
        }

        if (request == null || request.decision() == null) {
            throw new BusinessException(
                    "registrations.review_decision_required",
                    "Review decision is required"
            );
        }

        String reviewNote = request.reviewNote() == null
                ? ""
                : request.reviewNote().trim();

        if (requiresReviewNote(request.decision())
                && reviewNote.isBlank()) {
            throw new BusinessException(
                    "registrations.review_note_required",
                    "Review note is required"
            );
        }

        RegistrationStatus newStatus = switch (request.decision()) {
            case CONFIRMED -> RegistrationStatus.CONFIRMED;
            case REJECTED -> RegistrationStatus.REJECTED;
            case NEEDS_CORRECTION ->
                    RegistrationStatus.NEEDS_CORRECTION;
        };

        if (newStatus == RegistrationStatus.CONFIRMED) {
            // No administrator bypass: admission must not change a generated pool.
            protocolGuard.requireNoProtocolsOrUnits(eventId, disciplineId);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        registration.setStatus(newStatus.name());
        registration.setReviewNote(reviewNote);
        registration.setReviewedByUserId(actorId);
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