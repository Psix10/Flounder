package com.acme.sportplatform.protocol.application.schedule;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.acme.sportplatform.protocol.RegistrationProtocolAccess.ConfirmedRegistrationView;
import com.acme.sportplatform.protocol.application.GeneratedAssignment;
import com.acme.sportplatform.protocol.application.GeneratedCompetitionUnit;
import com.acme.sportplatform.protocol.application.schedule.ProtocolScheduleInputAdapter.DisciplinePlan;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.*;

class ProtocolScheduleInputAdapterTest {

    private static final Instant START =
            Instant.parse("2026-11-07T09:00:00Z");

    private final ProtocolScheduleInputAdapter adapter =
            new ProtocolScheduleInputAdapter();

    private UUID id(long value) {
        return new UUID(0L, value);
    }

    private TimingPolicy policy() {
        return new TimingPolicy(
                Duration.ofSeconds(600),
                Duration.ofSeconds(30),
                Origin.EXPERIMENTAL,
                "Adapter contract test"
        );
    }

    private List<Session> sessions() {
        return List.of(
                new Session(
                        id(100),
                        new Window(
                                START,
                                START.plusSeconds(7200)
                        ),
                        List.of()
                )
        );
    }

    private ConfirmedRegistrationView registration(
            long registrationId,
            long profileId
    ) {
        var registration = mock(ConfirmedRegistrationView.class);

        when(registration.registrationId())
                .thenReturn(id(registrationId));

        when(registration.requireParticipantProfileId())
                .thenReturn(id(profileId));

        return registration;
    }

    private GeneratedCompetitionUnit unit(
            long unitId,
            int sequence,
            long... registrationIds
    ) {
        var assignments = java.util.Arrays.stream(registrationIds)
                .mapToObj(registrationId ->
                        new GeneratedAssignment(
                                id(registrationId),
                                4,
                                null
                        )
                )
                .toList();

        return new GeneratedCompetitionUnit(
                id(unitId),
                "Heat " + sequence,
                sequence,
                assignments
        );
    }

    private DisciplinePlan plan(
            long disciplineId,
            List<GeneratedCompetitionUnit> units,
            List<ConfirmedRegistrationView> registrations
    ) {
        return new DisciplinePlan(
                id(disciplineId),
                id(100),
                units,
                registrations,
                Duration.ofSeconds(120),
                "Explicit planning duration"
        );
    }

    @Test
    void mapsRegistrationsToProfilesAndPreservesPlanningParameters() {
        var registration = registration(1, 500);
        var unit = unit(10, 1, 1);

        var input = adapter.adapt(
                policy(),
                sessions(),
                List.of(plan(
                        1000,
                        List.of(unit),
                        List.of(registration)
                ))
        );

        assertEquals(1, input.heats().size());

        var heat = input.heats().get(0);

        assertEquals(id(10), heat.id());
        assertEquals(id(100), heat.sessionId());
        assertEquals(id(1000), heat.disciplineId());
        assertEquals(List.of(id(500)), heat.athleteIds());
        assertEquals(Duration.ofSeconds(120), heat.duration());
        assertEquals(
                "Explicit planning duration",
                heat.durationBasis()
        );

        assertEquals(policy(), input.policy());
        assertEquals(sessions(), input.sessions());
    }

    @Test
    void sharedProfileAcrossDisciplinesReceivesMinimumRest() {
        var firstRegistration = registration(1, 500);
        var secondRegistration = registration(2, 500);

        var plans = List.of(
                plan(
                        1000,
                        List.of(unit(10, 1, 1)),
                        List.of(firstRegistration)
                ),
                plan(
                        2000,
                        List.of(unit(20, 1, 2)),
                        List.of(secondRegistration)
                )
        );

        var prepared = new PrepareProtocolSchedule().execute(
                policy(),
                sessions(),
                plans
        );

        assertEquals(
                List.of(id(500)),
                prepared.input().heats().get(0).athleteIds()
        );
        assertEquals(
                List.of(id(500)),
                prepared.input().heats().get(1).athleteIds()
        );

        assertEquals(
                Status.SCHEDULED,
                prepared.result().status()
        );
        assertEquals(
                START,
                prepared.result().heats().get(0).start()
        );
        assertEquals(
                START.plusSeconds(720),
                prepared.result().heats().get(1).start()
        );
    }

    @Test
    void preservesSuppliedDisciplineAndUnitOrder() {
        var input = adapter.adapt(
                policy(),
                sessions(),
                List.of(
                        plan(
                                2000,
                                List.of(
                                        unit(20, 1, 2),
                                        unit(21, 2, 3)
                                ),
                                List.of(
                                        registration(2, 502),
                                        registration(3, 503)
                                )
                        ),
                        plan(
                                1000,
                                List.of(unit(10, 1, 1)),
                                List.of(registration(1, 501))
                        )
                )
        );

        assertEquals(
                List.of(id(20), id(21), id(10)),
                input.heats().stream()
                        .map(Heat::id)
                        .toList()
        );
    }

    @Test
    void rejectsConfirmedRegistrationMissingFromAssignments() {
        var plan = plan(
                1000,
                List.of(unit(10, 1, 1)),
                List.of(
                        registration(1, 501),
                        registration(2, 502)
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.adapt(
                        policy(),
                        sessions(),
                        List.of(plan)
                )
        );
    }

    @Test
    void rejectsUnknownOrMultiplyAssignedRegistration() {
        var unknown = plan(
                1000,
                List.of(unit(10, 1, 99)),
                List.of(registration(1, 501))
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.adapt(
                        policy(),
                        sessions(),
                        List.of(unknown)
                )
        );

        var repeated = plan(
                1000,
                List.of(
                        unit(10, 1, 1),
                        unit(11, 2, 1)
                ),
                List.of(registration(1, 501))
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.adapt(
                        policy(),
                        sessions(),
                        List.of(repeated)
                )
        );
    }

    @Test
    void rejectsDuplicateRegistrationIdAcrossDisciplinePlans() {
        var plans = List.of(
                plan(
                        1000,
                        List.of(unit(10, 1, 1)),
                        List.of(registration(1, 501))
                ),
                plan(
                        2000,
                        List.of(unit(20, 1, 1)),
                        List.of(registration(1, 501))
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.adapt(
                        policy(),
                        sessions(),
                        plans
                )
        );
    }

    @Test
    void rejectsDuplicateProfileWithinOneHeat() {
        var plan = plan(
                1000,
                List.of(unit(10, 1, 1, 2)),
                List.of(
                        registration(1, 500),
                        registration(2, 500)
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.adapt(
                        policy(),
                        sessions(),
                        List.of(plan)
                )
        );
    }

    @Test
    void rejectsNonAscendingUnitSequence() {
        var plan = plan(
                1000,
                List.of(
                        unit(10, 2, 1),
                        unit(11, 1, 2)
                ),
                List.of(
                        registration(1, 501),
                        registration(2, 502)
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.adapt(
                        policy(),
                        sessions(),
                        List.of(plan)
                )
        );
    }

    @Test
    void rejectsUnknownSession() {
        var plan = new DisciplinePlan(
                id(1000),
                id(999),
                List.of(unit(10, 1, 1)),
                List.of(registration(1, 501)),
                Duration.ofSeconds(120),
                "Explicit planning duration"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.adapt(
                        policy(),
                        sessions(),
                        List.of(plan)
                )
        );
    }

    @Test
    void rejectsDuplicateDisciplinePlans() {
        var first = plan(
                1000,
                List.of(unit(10, 1, 1)),
                List.of(registration(1, 501))
        );

        var second = plan(
                1000,
                List.of(unit(20, 1, 2)),
                List.of(registration(2, 502))
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.adapt(
                        policy(),
                        sessions(),
                        List.of(first, second)
                )
        );
    }
}