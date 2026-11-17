package com.acme.sportplatform.protocol.application;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.acme.sportplatform.protocol.CompetitionUnitProtocolAccess;
import com.acme.sportplatform.protocol.SessionProtocolSnapshotAccess;
import com.acme.sportplatform.protocol.SessionProtocolSnapshotReader;
import com.acme.sportplatform.protocol.infrastructure.jpa.StartProtocolSnapshotRepository;
import com.acme.sportplatform.registrations.RegistrationProtocolGuard;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class RegistrationProtocolGuardService
        implements RegistrationProtocolGuard {

    private final SessionProtocolSnapshotAccess sessionAccess;
    private final SessionProtocolSnapshotReader sessionReader;
    private final StartProtocolSnapshotRepository startSnapshots;
    private final CompetitionUnitProtocolAccess units;

    public RegistrationProtocolGuardService(
            SessionProtocolSnapshotAccess sessionAccess,
            SessionProtocolSnapshotReader sessionReader,
            StartProtocolSnapshotRepository startSnapshots,
            CompetitionUnitProtocolAccess units
    ) {
        this.sessionAccess = sessionAccess;
        this.sessionReader = sessionReader;
        this.startSnapshots = startSnapshots;
        this.units = units;
    }

    @Override
    public void lockEventBatch(UUID eventId) {
        sessionAccess.lockEventBatch(eventId);
    }

    @Override
    public void requireNoProtocolsOrUnits(
            UUID eventId,
            UUID disciplineId
    ) {
        if (sessionReader.findLatestProtocol(eventId).isPresent()) {
            throw conflict(
                    "Event already has a saved session protocol"
            );
        }

        if (startSnapshots
                .findFirstByEventDisciplineIdOrderByRevisionNoDesc(
                        disciplineId
                )
                .isPresent()) {
            throw conflict(
                    "Discipline already has a saved start protocol"
            );
        }

        if (units.hasCompetitionUnits(disciplineId)) {
            throw conflict(
                    "Discipline already has competition units"
            );
        }
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                message
        );
    }
}