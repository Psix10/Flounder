package com.acme.sportplatform.results.application;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.events.EventOperationAccess;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.results.api.RecordResultRequest;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntryRepository;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitRepository;

@Component("resultsAccess")
@Transactional(readOnly = true)
public class ResultsAccess {

    private final EventDisciplineLookup eventDisciplineLookup;
    private final EventOperationAccess eventOperationAccess;
    private final CompetitionUnitRepository competitionUnitRepository;
    private final CompetitionUnitEntryRepository entryRepository;

    public ResultsAccess(
            EventDisciplineLookup eventDisciplineLookup,
            EventOperationAccess eventOperationAccess,
            CompetitionUnitRepository competitionUnitRepository,
            CompetitionUnitEntryRepository entryRepository
    ) {
        this.eventDisciplineLookup = eventDisciplineLookup;
        this.eventOperationAccess = eventOperationAccess;
        this.competitionUnitRepository = competitionUnitRepository;
        this.entryRepository = entryRepository;
    }

    public boolean canReadDiscipline(
            UUID disciplineId,
            Authentication authentication
    ) {
        return checkDiscipline(
                disciplineId,
                authentication,
                Permission.READ
        );
    }

    public boolean canManageDiscipline(
            UUID disciplineId,
            Authentication authentication
    ) {
        return checkDiscipline(
                disciplineId,
                authentication,
                Permission.MANAGE
        );
    }

    public boolean canReadUnit(
            UUID unitId,
            Authentication authentication
    ) {
        return checkUnit(unitId, authentication, Permission.READ);
    }

    public boolean canManageUnit(
            UUID unitId,
            Authentication authentication
    ) {
        return checkUnit(unitId, authentication, Permission.MANAGE);
    }

    public boolean canWriteUnit(
            UUID unitId,
            Authentication authentication
    ) {
        return checkUnit(unitId, authentication, Permission.WRITE);
    }

    public boolean canPublishUnit(
            UUID unitId,
            Authentication authentication
    ) {
        return checkUnit(unitId, authentication, Permission.PUBLISH);
    }

    public boolean canWriteEntry(
            RecordResultRequest request,
            Authentication authentication
    ) {
        if (request == null || request.competitionUnitEntryId() == null) {
            return false;
        }

        return entryRepository.findById(request.competitionUnitEntryId())
                .map(entry -> canWriteUnit(
                        entry.getCompetitionUnitId(),
                        authentication
                ))
                .orElse(false);
    }

    private boolean checkUnit(
            UUID unitId,
            Authentication authentication,
            Permission permission
    ) {
        if (unitId == null || currentUserId(authentication) == null) {
            return false;
        }

        return competitionUnitRepository.findById(unitId)
                .map(unit -> checkDiscipline(
                        unit.getEventDisciplineId(),
                        authentication,
                        permission
                ))
                .orElse(false);
    }

    private boolean checkDiscipline(
            UUID disciplineId,
            Authentication authentication,
            Permission permission
    ) {
        UUID userId = currentUserId(authentication);

        if (disciplineId == null || userId == null) {
            return false;
        }

        return eventDisciplineLookup.findById(disciplineId)
                .map(discipline -> checkEvent(
                        discipline.eventId(),
                        userId,
                        authentication,
                        permission
                ))
                .orElse(false);
    }

    private boolean checkEvent(
            UUID eventId,
            UUID userId,
            Authentication authentication,
            Permission permission
    ) {
        if (hasRole(authentication, "PLATFORM_ADMIN")) {
            return true;
        }

        boolean organizerAllowed =
                permission == Permission.READ
                        || permission == Permission.MANAGE
                        || permission == Permission.PUBLISH;

        if (organizerAllowed
                && hasRole(authentication, "ORGANIZER")
                && eventOperationAccess.canManageEvent(eventId, userId)) {
            return true;
        }

        boolean operatorAllowed =
                permission == Permission.READ
                        || permission == Permission.WRITE
                        || permission == Permission.PUBLISH;

        return operatorAllowed
                && hasRole(authentication, "OPERATOR")
                && eventOperationAccess.isAssignedActiveOperator(
                        eventId,
                        userId
                );
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        if (!(authentication.getPrincipal()
                instanceof AuthenticatedUserPrincipal principal)) {
            return null;
        }

        return principal.getUserId();
    }

    private boolean hasRole(
            Authentication authentication,
            String role
    ) {
        return authentication != null
                && authentication.getAuthorities().stream()
                        .anyMatch(authority ->
                                ("ROLE_" + role).equals(
                                        authority.getAuthority()
                                )
                        );
    }

    private enum Permission {
        READ,
        MANAGE,
        WRITE,
        PUBLISH
    }
}