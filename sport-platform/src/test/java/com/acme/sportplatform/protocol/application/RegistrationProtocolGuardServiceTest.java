package com.acme.sportplatform.protocol.application;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.acme.sportplatform.protocol.CompetitionUnitProtocolAccess;
import com.acme.sportplatform.protocol.SessionProtocolSnapshotAccess;
import com.acme.sportplatform.protocol.SessionProtocolSnapshotReader;
import com.acme.sportplatform.protocol.infrastructure.jpa.StartProtocolSnapshotEntity;
import com.acme.sportplatform.protocol.infrastructure.jpa.StartProtocolSnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

class RegistrationProtocolGuardServiceTest {

    private final UUID eventId = UUID.randomUUID();
    private final UUID disciplineId = UUID.randomUUID();

    private SessionProtocolSnapshotAccess sessionAccess;
    private SessionProtocolSnapshotReader sessionReader;
    private StartProtocolSnapshotRepository startSnapshots;
    private CompetitionUnitProtocolAccess units;

    private RegistrationProtocolGuardService guard;

    @BeforeEach
    void setUp() {
        sessionAccess = mock(SessionProtocolSnapshotAccess.class);
        sessionReader = mock(SessionProtocolSnapshotReader.class);
        startSnapshots = mock(StartProtocolSnapshotRepository.class);
        units = mock(CompetitionUnitProtocolAccess.class);

        guard = new RegistrationProtocolGuardService(
                sessionAccess,
                sessionReader,
                startSnapshots,
                units
        );

        when(sessionReader.findLatestProtocol(eventId))
                .thenReturn(Optional.empty());

        when(startSnapshots
                .findFirstByEventDisciplineIdOrderByRevisionNoDesc(
                        disciplineId
                ))
                .thenReturn(Optional.empty());

        when(units.hasCompetitionUnits(disciplineId))
                .thenReturn(false);
    }

    @Test
    void shouldDelegateEventBatchLock() {
        guard.lockEventBatch(eventId);

        verify(sessionAccess).lockEventBatch(eventId);
        verifyNoInteractions(
                sessionReader,
                startSnapshots,
                units
        );
    }

    @Test
    void shouldAllowCorrectionWhenProtocolsAndUnitsAreAbsent() {
        assertDoesNotThrow(
                () -> guard.requireNoProtocolsOrUnits(
                        eventId,
                        disciplineId
                )
        );

        verify(sessionReader).findLatestProtocol(eventId);

        verify(startSnapshots)
                .findFirstByEventDisciplineIdOrderByRevisionNoDesc(
                        disciplineId
                );

        verify(units).hasCompetitionUnits(disciplineId);

        verifyNoInteractions(sessionAccess);
    }

    @Test
    void shouldRejectWhenSessionProtocolExists() {
        when(sessionReader.findLatestProtocol(eventId))
                .thenReturn(Optional.of(
                        new ObjectMapper().createObjectNode()
                ));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> guard.requireNoProtocolsOrUnits(
                        eventId,
                        disciplineId
                )
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );
        assertEquals(
                "Event already has a saved session protocol",
                exception.getReason()
        );

        verifyNoInteractions(startSnapshots, units);
    }

    @Test
    void shouldRejectWhenStartProtocolExists() {
        when(startSnapshots
                .findFirstByEventDisciplineIdOrderByRevisionNoDesc(
                        disciplineId
                ))
                .thenReturn(Optional.of(
                        mock(StartProtocolSnapshotEntity.class)
                ));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> guard.requireNoProtocolsOrUnits(
                        eventId,
                        disciplineId
                )
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );
        assertEquals(
                "Discipline already has a saved start protocol",
                exception.getReason()
        );

        verifyNoInteractions(units);
    }

    @Test
    void shouldRejectWhenCompetitionUnitsExist() {
        when(units.hasCompetitionUnits(disciplineId))
                .thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> guard.requireNoProtocolsOrUnits(
                        eventId,
                        disciplineId
                )
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );
        assertEquals(
                "Discipline already has competition units",
                exception.getReason()
        );
    }
}