package com.acme.sportplatform.protocol.api;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.acme.sportplatform.protocol.application.schedule.GenerateSessionProtocolCommand;
import com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record GenerateSessionProtocolRequest(
        boolean regenerate,

        @NotNull
        @Valid
        Timing timingPolicy,

        @NotEmpty
        @Size(max = 100)
        List<@NotNull @Valid SessionRequest> sessions,

        @NotEmpty
        @Size(max = 500)
        List<@NotNull @Valid DisciplineRequest> disciplines
) {
    public GenerateSessionProtocolCommand toCommand(UUID eventId) {
        var policy = new ProtocolScheduleModel.TimingPolicy(
                Duration.ofSeconds(timingPolicy.minimumRestSeconds()),
                Duration.ofSeconds(timingPolicy.changeoverSeconds()),
                ProtocolScheduleModel.Origin.ORGANIZER_CONFIRMED,
                timingPolicy.sourceDescription()
        );

        var sessionModels = sessions.stream()
                .map(session -> new ProtocolScheduleModel.Session(
                        session.id(),
                        new ProtocolScheduleModel.Window(
                                session.start().toInstant(),
                                session.end().toInstant()
                        ),
                        session.breaks().stream()
                                .map(pause -> new ProtocolScheduleModel.Window(
                                        pause.start().toInstant(),
                                        pause.end().toInstant()
                                ))
                                .toList()
                ))
                .toList();

        var disciplineModels = disciplines.stream()
                .map(discipline ->
                        new GenerateSessionProtocolCommand.Discipline(
                                discipline.disciplineId(),
                                discipline.ruleSetVersionId(),
                                discipline.sessionId(),
                                Duration.ofSeconds(
                                        discipline.heatDurationSeconds()
                                ),
                                discipline.durationBasis()
                        )
                )
                .toList();

        // Also checks chronology, overlapping sessions and session references.
        new ProtocolScheduleModel.Input(
                policy,
                sessionModels,
                List.of()
        );

        return new GenerateSessionProtocolCommand(
                eventId,
                regenerate,
                policy,
                sessionModels,
                disciplineModels
        );
    }

    public record Timing(
            @NotNull
            @PositiveOrZero
            Long minimumRestSeconds,

            @NotNull
            @PositiveOrZero
            Long changeoverSeconds,

            @NotBlank
            @Size(max = 2000)
            String sourceDescription
    ) { }

    public record SessionRequest(
            @NotNull
            UUID id,

            @NotNull
            OffsetDateTime start,

            @NotNull
            OffsetDateTime end,

            @NotNull
            @Size(max = 100)
            List<@NotNull @Valid BreakRequest> breaks
    ) { }

    public record BreakRequest(
            @NotNull
            OffsetDateTime start,

            @NotNull
            OffsetDateTime end
    ) { }

    public record DisciplineRequest(
            @NotNull
            UUID disciplineId,

            UUID ruleSetVersionId,

            @NotNull
            UUID sessionId,

            @NotNull
            @Positive
            Long heatDurationSeconds,

            @NotBlank
            @Size(max = 2000)
            String durationBasis
    ) { }
}