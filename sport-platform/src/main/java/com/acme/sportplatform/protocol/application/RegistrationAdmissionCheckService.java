package com.acme.sportplatform.protocol.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.events.EventRegulationLookup;
import com.acme.sportplatform.events.ProtocolEventLookup;
import com.acme.sportplatform.protocol.api.CreateProtocolRuleSetRequest;
import com.acme.sportplatform.protocol.api.RuleBlockRequest;
import com.acme.sportplatform.protocol.domain.ProtocolRuleSetStatus;
import com.acme.sportplatform.protocol.infrastructure.jpa.ProtocolRuleBlockRepository;
import com.acme.sportplatform.protocol.infrastructure.jpa.ProtocolRuleSetRepository;
import com.acme.sportplatform.protocol.infrastructure.jpa.ProtocolRuleSetVersionRepository;
import com.acme.sportplatform.registrations.RegistrationAdmissionCheck;
import com.acme.sportplatform.regulations.RegulationBundleAccess;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reads admission rules bound to the event, without granting rule-management
 * privileges to the participant and without acquiring workflow scope locks.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class RegistrationAdmissionCheckService implements RegistrationAdmissionCheck {
    private final EventDisciplineLookup disciplines;
    private final ProtocolEventLookup events;
    private final EventRegulationLookup eventRegulations;
    private final RegulationBundleAccess regulations;
    private final ProtocolRuleSetVersionRepository versions;
    private final ProtocolRuleSetRepository sets;
    private final ProtocolRuleBlockRepository blocks;
    private final ProtocolRuleSetValidator validator;
    private final RegistrationAdmissionPolicy policy;

    public RegistrationAdmissionCheckService(
            EventDisciplineLookup disciplines,
            ProtocolEventLookup events,
            EventRegulationLookup eventRegulations,
            RegulationBundleAccess regulations,
            ProtocolRuleSetVersionRepository versions,
            ProtocolRuleSetRepository sets,
            ProtocolRuleBlockRepository blocks,
            ProtocolRuleSetValidator validator,
            ObjectMapper json
    ) {
        this.disciplines = disciplines;
        this.events = events;
        this.eventRegulations = eventRegulations;
        this.regulations = regulations;
        this.versions = versions;
        this.sets = sets;
        this.blocks = blocks;
        this.validator = validator;
        this.policy = new RegistrationAdmissionPolicy(json);
    }

    @Override
    public void verify(
            UUID eventDisciplineId,
            UUID registrationId,
            String participantSnapshot
    ) {
        Objects.requireNonNull(eventDisciplineId, "Discipline ID is required");
        Objects.requireNonNull(registrationId, "Registration ID is required");

        var discipline = disciplines.getPublishedById(eventDisciplineId);
        if (!eventDisciplineId.equals(discipline.id()) || discipline.eventId() == null) {
            throw configuration("Discipline lookup returned an inconsistent scope");
        }

        UUID regulationVersionId = eventRegulations.versionId(discipline.eventId());
        var binding = regulationVersionId == null
                ? null
                : regulations.binding(regulationVersionId);

        if (binding == null) {
            policy.verify(
                    registrationId, participantSnapshot,
                    discipline.settingsJson(), null
            );
            return;
        }

        if (binding.ruleVersionId() == null
                || !Objects.equals(binding.competitionFormat(), discipline.competitionFormat())
                || !Objects.equals(binding.unitType(), discipline.unitType())) {
            throw configuration("Bound rules do not match the discipline format");
        }

        var version = versions.findById(binding.ruleVersionId())
                .orElseThrow(() -> configuration("Bound rule version was not found"));

        if (!binding.ruleVersionId().equals(version.getId())
                || version.getStatus() != ProtocolRuleSetStatus.PUBLISHED
                || version.getRuleSetId() == null) {
            throw configuration("Bound rule version must exist and be published");
        }

        var set = sets.findById(version.getRuleSetId())
                .orElseThrow(() -> configuration("Bound rule set was not found"));

        var event = events.getById(discipline.eventId());

        if (set.getSportId() == null
                || !set.getSportId().equals(event.sportId())
                || set.getCompetitionFormat() == null
                || !set.getCompetitionFormat().name().equals(discipline.competitionFormat())
                || set.getUnitType() == null
                || !set.getUnitType().name().equals(discipline.unitType())) {
            throw configuration("Bound rule set is incompatible with the event or discipline");
        }

        var requests = blocks
                .findByRuleSetVersionIdOrderByOrderIndexAsc(version.getId())
                .stream()
                .map(block -> {
                    if (block.getConfig() == null) {
                        throw configuration("Bound rule block configuration is missing");
                    }
                    return new RuleBlockRequest(
                            block.getOrderIndex(),
                            block.getExecutorCode(),
                            block.getConfig().deepCopy()
                    );
                })
                .toList();

        var validated = validator.validate(new CreateProtocolRuleSetRequest(
                set.getSportId(),
                set.getCode(),
                set.getName(),
                set.getCompetitionFormat().name(),
                set.getUnitType().name(),
                requests
        ));

        JsonNode group = validated.get(2).config();
        JsonNode assignment = validated.get(3).config();
        int capacity = group.path("capacity").intValue();

        List<Integer> allowed = assignment.has("allowedPositions")
                ? integers(assignment.get("allowedPositions"))
                : IntStream.rangeClosed(1, capacity).boxed().toList();

        var resolved = new ResolvedProtocolRules(
                version.getId(),
                capacity,
                group.path("unitLabelPrefix").textValue(),
                validated.get(1).config().path("metricKey").textValue(),
                integers(assignment.path("positionOrder")),
                allowed,
                group.path("heatOrder").asText("FASTEST_FIRST"),
                group.path("partialHeat").asText("LAST"),
                validated
        );

        policy.verify(
                registrationId, participantSnapshot,
                discipline.settingsJson(), resolved
        );
    }

    private static List<Integer> integers(JsonNode array) {
        List<Integer> values = new ArrayList<>();
        array.forEach(value -> values.add(value.intValue()));
        return List.copyOf(values);
    }

    private static BusinessException configuration(String message) {
        return new BusinessException(
                "registrations.invalid_admission_configuration", message
        );
    }
}