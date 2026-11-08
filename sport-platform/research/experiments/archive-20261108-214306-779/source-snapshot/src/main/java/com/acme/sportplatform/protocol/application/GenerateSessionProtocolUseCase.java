package com.acme.sportplatform.protocol.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.ProtocolDisciplineLookup;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.protocol.CompetitionUnitProtocolAccess;
import com.acme.sportplatform.protocol.CompetitionUnitProtocolAccess.CompetitionUnitDraftView;
import com.acme.sportplatform.protocol.EventProtocolAccess;
import com.acme.sportplatform.protocol.RegistrationProtocolAccess;
import com.acme.sportplatform.protocol.RegistrationProtocolAccess.ConfirmedRegistrationView;
import com.acme.sportplatform.protocol.SessionProtocolSnapshotAccess;
import com.acme.sportplatform.protocol.application.schedule.GenerateSessionProtocolCommand;
import com.acme.sportplatform.protocol.application.schedule.PrepareProtocolSchedule;
import com.acme.sportplatform.protocol.application.schedule.ProtocolScheduleInputAdapter.DisciplinePlan;
import com.acme.sportplatform.protocol.application.schedule.ScheduledProtocolBinding;
import com.acme.sportplatform.protocol.application.schedule.ScheduledProtocolBinding.BoundHeat;
import com.acme.sportplatform.protocol.application.schedule.SessionProtocolGenerationResult;
import com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.Status;
import com.acme.sportplatform.protocol.infrastructure.jpa.StartProtocolSnapshotEntity;
import com.acme.sportplatform.protocol.infrastructure.jpa.StartProtocolSnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Atomic persistence of the supplied batch.
 * No controller or global active-pool scheduling guarantee.
 */
@Service
public class GenerateSessionProtocolUseCase {

    private final EventProtocolAccess events;
    private final RegistrationProtocolAccess registrations;
    private final CompetitionUnitProtocolAccess units;
    private final RuleExecutorRegistry registry;
    private final ObjectMapper json;
    private final ProtocolDisciplineLookup disciplines;
    private final PublishedProtocolRulesResolver resolver;
    private final StartProtocolSnapshotRepository snapshots;
    private final SessionProtocolSnapshotAccess sessionSnapshots;
    private final BoundRegulationRules boundRules;

    public GenerateSessionProtocolUseCase(
            EventProtocolAccess events,
            RegistrationProtocolAccess registrations,
            CompetitionUnitProtocolAccess units,
            RuleExecutorRegistry registry,
            ObjectMapper json,
            ProtocolDisciplineLookup disciplines,
            PublishedProtocolRulesResolver resolver,
            StartProtocolSnapshotRepository snapshots,
            SessionProtocolSnapshotAccess sessionSnapshots,
            BoundRegulationRules boundRules
    ) {
        this.events = events;
        this.registrations = registrations;
        this.units = units;
        this.registry = registry;
        this.json = json;
        this.disciplines = disciplines;
        this.resolver = resolver;
        this.snapshots = snapshots;
        this.sessionSnapshots = sessionSnapshots;
        this.boundRules = boundRules;
    }

    private record Work(
            GenerateSessionProtocolCommand.Discipline command,
            UUID effectiveRuleVersionId,
            ResolvedProtocolRules rules,
            List<ConfirmedRegistrationView> confirmed,
            PreparedStartProtocol prepared,
            boolean existing
    ) { }

    @Transactional
    public SessionProtocolGenerationResult execute(
            GenerateSessionProtocolCommand command
    ) {
        Objects.requireNonNull(command);

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

        UUID actor = principal.getUserId();

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

        sessionSnapshots.lockEventBatch(command.eventId());

        Map<UUID, ProtocolDisciplineLookup.View> locked =
                new HashMap<>();

        var ordered = command.disciplines().stream()
                .map(GenerateSessionProtocolCommand.Discipline::disciplineId)
                .sorted(Comparator.comparing(UUID::toString))
                .toList();

        for (UUID id : ordered) {
            var view = disciplines.lock(id);

            if (!command.eventId().equals(view.eventId())) {
                throw error(
                        "protocol.session_event_mismatch",
                        "All disciplines must belong to the specified event"
                );
            }

            if (!"INDIVIDUAL".equals(view.competitionFormat())
                    || !"HEAT".equals(view.unitType())) {
                throw error(
                        "protocol.session_format_not_supported",
                        "Only individual heat disciplines are supported"
                );
            }

            events.getPublishedEventDiscipline(id);
            locked.put(id, view);
        }

        List<Work> work = new ArrayList<>();
        List<DisciplinePlan> plans = new ArrayList<>();

        var preparation = new PrepareStartProtocol(registry, json);

        for (var item : command.disciplines()) {
            var discipline = locked.get(item.disciplineId());

            UUID effectiveRuleVersionId = boundRules.resolve(
                    discipline,
                    item.ruleSetVersionId()
            );

            if (effectiveRuleVersionId == null) {
                throw error(
                        "protocol.rule_version_required",
                        "Select a published rule version or bind one "
                                + "to the event regulation"
                );
            }

            var rules = resolver.resolve(
                    effectiveRuleVersionId,
                    discipline,
                    actor,
                    admin
            );

            boolean existing =
                    units.hasCompetitionUnits(item.disciplineId());

            if (existing && !command.regenerate()) {
                throw error(
                        "protocol.already_generated",
                        "Regeneration must be explicitly requested"
                );
            }

            if (existing
                    && snapshots
                            .findFirstByEventDisciplineIdOrderByRevisionNoDesc(
                                    item.disciplineId()
                            )
                            .isEmpty()) {
                throw error(
                        "protocol.regeneration_requires_snapshot",
                        "Only protocols with saved snapshots "
                                + "can be regenerated"
                );
            }

            var confirmed =
                    registrations.findConfirmedByEventDisciplineId(
                            item.disciplineId()
                    );

            var prepared = preparation.prepare(
                    item.disciplineId(),
                    rules,
                    confirmed
            );

            work.add(new Work(
                    item,
                    effectiveRuleVersionId,
                    rules,
                    List.copyOf(confirmed),
                    prepared,
                    existing
            ));

            plans.add(new DisciplinePlan(
                    item.disciplineId(),
                    item.sessionId(),
                    prepared.units(),
                    confirmed,
                    item.heatDuration(),
                    item.durationBasis()
            ));
        }

        var schedule = new PrepareProtocolSchedule().execute(
                command.timingPolicy(),
                command.sessions(),
                plans
        );

        if (schedule.result().status() != Status.SCHEDULED) {
            throw error(
                    "protocol.session_window_exceeded",
                    "Programme does not fit the fixed order "
                            + "and timing parameters: "
                            + schedule.result().failure()
            );
        }

        var binding = new ScheduledProtocolBinding();

        Map<UUID, List<CompetitionUnitDraftView>> timed =
                new HashMap<>();

        for (var item : work) {
            timed.put(
                    item.command().disciplineId(),
                    binding.toTimedDrafts(
                            item.prepared(),
                            schedule
                    )
            );
        }

        UUID snapshotId = UUID.randomUUID();

        int revision =
                sessionSnapshots.nextRevision(command.eventId());

        OffsetDateTime now = OffsetDateTime
                .now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS);

        List<ProtocolGenerationResult> generated =
                new ArrayList<>();

        List<BoundHeat> bound = new ArrayList<>();

        for (var item : work) {
            UUID id = item.command().disciplineId();

            var saved = item.existing()
                    ? units.replaceDraftStartProtocol(
                            id,
                            timed.get(id)
                    )
                    : units.createDraftStartProtocol(
                            id,
                            timed.get(id)
                    );

            bound.addAll(binding.bindSavedUnits(
                    item.prepared(),
                    schedule,
                    saved
            ));

            Map<UUID, BigDecimal> metrics = new HashMap<>();

            for (var participant : item.prepared().participants()) {
                metrics.put(
                        participant.registrationId(),
                        participant.seedMetric()
                );
            }

            generated.add(new ProtocolGenerationResult(
                    id,
                    saved.stream()
                            .map(unit -> new GeneratedCompetitionUnit(
                                    unit.competitionUnitId(),
                                    unit.label(),
                                    unit.sequenceNumber(),
                                    unit.assignments().stream()
                                            .map(assignment ->
                                                    new GeneratedAssignment(
                                                            assignment.registrationId(),
                                                            assignment.laneOrPosition(),
                                                            metrics.get(
                                                                    assignment.registrationId()
                                                            ),
                                                            assignment.categoryCode(),
                                                            assignment.seedingGroupCode()
                                                    )
                                            )
                                            .toList()
                            ))
                            .toList()
            ));
        }

        long uniqueSavedHeatIds = bound.stream()
                .map(BoundHeat::savedHeatId)
                .distinct()
                .count();

        if (uniqueSavedHeatIds != bound.size()) {
            throw error(
                    "protocol.invalid_saved_schedule",
                    "Saved heat IDs must be unique across the batch"
            );
        }

        for (int i = 0; i < work.size(); i++) {
            var item = work.get(i);
            UUID id = item.command().disciplineId();

            var snapshot = new StartProtocolSnapshotEntity();

            snapshot.setId(UUID.randomUUID());
            snapshot.setEventDisciplineId(id);
            snapshot.setRuleSetVersionId(
                    item.effectiveRuleVersionId()
            );

            snapshot.setRevisionNo(
                    snapshots
                            .findFirstByEventDisciplineIdOrderByRevisionNoDesc(id)
                            .map(previous ->
                                    previous.getRevisionNo() + 1
                            )
                            .orElse(1)
            );

            snapshot.setOperation(
                    item.existing() ? "REGENERATE" : "GENERATE"
            );

            var input = json.createObjectNode();

            input.put("schemaVersion", 2);
            input.put(
                    "sessionSnapshotId",
                    snapshotId.toString()
            );
            input.put(
                    "ruleSetVersionId",
                    item.effectiveRuleVersionId().toString()
            );
            input.set(
                    "rules",
                    json.valueToTree(item.rules())
            );
            input.set(
                    "confirmedRegistrations",
                    json.valueToTree(item.confirmed())
            );
            input.set(
                    "participants",
                    json.valueToTree(
                            item.prepared().participants()
                    )
            );

            snapshot.setInput(input);
            snapshot.setProtocol(
                    json.valueToTree(generated.get(i))
            );
            snapshot.setCreatedByUserId(actor);
            snapshot.setCreatedAt(now);

            snapshots.saveAndFlush(snapshot);
        }

        var result = new SessionProtocolGenerationResult(
                snapshotId,
                command.eventId(),
                revision,
                generated,
                bound
        );

        var input = json.createObjectNode();

        input.put("schemaVersion", 1);
        input.set(
                "command",
                json.valueToTree(command)
        );
        input.set(
                "schedulerInput",
                json.valueToTree(schedule.input())
        );
        input.set(
                "disciplines",
                json.valueToTree(
                        work.stream()
                                .map(item -> {
                                    var node = json.createObjectNode();

                                    node.put(
                                            "disciplineId",
                                            item.command()
                                                    .disciplineId()
                                                    .toString()
                                    );
                                    node.put(
                                            "effectiveRuleSetVersionId",
                                            item.effectiveRuleVersionId()
                                                    .toString()
                                    );
                                    node.set(
                                            "rules",
                                            json.valueToTree(item.rules())
                                    );
                                    node.set(
                                            "confirmedRegistrations",
                                            json.valueToTree(item.confirmed())
                                    );
                                    node.set(
                                            "prepared",
                                            json.valueToTree(item.prepared())
                                    );

                                    return node;
                                })
                                .toList()
                )
        );

        sessionSnapshots.save(
                snapshotId,
                command.eventId(),
                revision,
                input,
                json.valueToTree(result),
                actor,
                now
        );

        return result;
    }

    private BusinessException error(
            String code,
            String message
    ) {
        return new BusinessException(code, message);
    }
}