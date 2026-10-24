package com.acme.sportplatform.protocol.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.protocol.CompetitionUnitProtocolAccess;
import com.acme.sportplatform.protocol.EventProtocolAccess;
import com.acme.sportplatform.protocol.RegistrationProtocolAccess;
import com.acme.sportplatform.protocol.api.GenerateStartProtocolRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class GenerateStartProtocolUseCase {

    private final EventProtocolAccess eventProtocolAccess;
    private final RegistrationProtocolAccess registrationProtocolAccess;
    private final CompetitionUnitProtocolAccess competitionUnitProtocolAccess;
    private final RuleExecutorRegistry ruleExecutorRegistry;
    private final ObjectMapper objectMapper;

    public GenerateStartProtocolUseCase(
            EventProtocolAccess eventProtocolAccess,
            RegistrationProtocolAccess registrationProtocolAccess,
            CompetitionUnitProtocolAccess competitionUnitProtocolAccess,
            RuleExecutorRegistry ruleExecutorRegistry,
            ObjectMapper objectMapper
    ) {
        this.eventProtocolAccess = eventProtocolAccess;
        this.registrationProtocolAccess = registrationProtocolAccess;
        this.competitionUnitProtocolAccess = competitionUnitProtocolAccess;
        this.ruleExecutorRegistry = ruleExecutorRegistry;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ProtocolGenerationResult execute(
            UUID eventDisciplineId,
            GenerateStartProtocolRequest request
    ) {
        EventProtocolAccess.PublishedEventDisciplineView discipline =
                eventProtocolAccess.getPublishedEventDiscipline(eventDisciplineId);

        JsonNode root = readSettings(discipline.settingsJson());
        JsonNode protocolNode = root.path("protocol");

        int unitCapacity = protocolNode.path("unitCapacity").asInt(0);
        String unitLabelPrefix = protocolNode.path("unitLabelPrefix").asText("Heat");
        String seedMetricKey = protocolNode.path("seedMetricKey").asText(null);
        boolean allowRegenerate = protocolNode.path("allowRegenerate").asBoolean(false);
        List<Integer> positionOrder = readPositionOrder(protocolNode.path("positionOrder"));

        validateProtocolSettings(unitCapacity, seedMetricKey, positionOrder);

        boolean hasExistingUnits = competitionUnitProtocolAccess.hasCompetitionUnits(eventDisciplineId);
        boolean regenerate = request != null && Boolean.TRUE.equals(request.regenerate());

        validateGenerationState(hasExistingUnits, regenerate, allowRegenerate);

        List<RegistrationProtocolAccess.ConfirmedRegistrationView> registrations =
                registrationProtocolAccess.findConfirmedByEventDisciplineId(eventDisciplineId);

        if (registrations.isEmpty()) {
            throw new BusinessException(
                    "protocol.no_confirmed_registrations",
                    "No confirmed registrations found for this discipline"
            );
        }

        List<SeedParticipant> participants = registrations.stream()
                .map(registration -> new SeedParticipant(
                        registration.registrationId(),
                        extractSeedMetric(registration.registrationMeta(), seedMetricKey)
                ))
                .toList();

        ProtocolGenerationContext context = new ProtocolGenerationContext(
                eventDisciplineId,
                unitCapacity,
                unitLabelPrefix,
                positionOrder,
                participants
        );

        ruleExecutorRegistry.get("ELIGIBILITY_CONFIRMED_ONLY").apply(context);
        ruleExecutorRegistry.get("SEED_BY_METRIC_ASC_NULLS_LAST").apply(context);
        ruleExecutorRegistry.get("GROUP_BY_CAPACITY").apply(context);
        ruleExecutorRegistry.get("ASSIGN_CENTER_OUT").apply(context);

        List<GeneratedCompetitionUnit> savedUnits = persist(context);

        return new ProtocolGenerationResult(eventDisciplineId, savedUnits);
    }

    private void validateProtocolSettings(
            int unitCapacity,
            String seedMetricKey,
            List<Integer> positionOrder
    ) {
        if (unitCapacity <= 0) {
            throw new BusinessException(
                    "protocol.invalid_settings",
                    "Protocol unitCapacity must be greater than zero"
            );
        }

        if (seedMetricKey == null || seedMetricKey.isBlank()) {
            throw new BusinessException(
                    "protocol.invalid_settings",
                    "Protocol seedMetricKey is required"
            );
        }

        if (positionOrder.isEmpty()) {
            throw new BusinessException(
                    "protocol.invalid_settings",
                    "Protocol positionOrder is required"
            );
        }
    }

    private void validateGenerationState(
            boolean hasExistingUnits,
            boolean regenerate,
            boolean allowRegenerate
    ) {
        if (!hasExistingUnits) {
            return;
        }

        if (!regenerate || !allowRegenerate) {
            throw new BusinessException(
                    "protocol.already_generated",
                    "Start protocol has already been generated for this discipline"
            );
        }

        throw new BusinessException(
                "protocol.regenerate_not_supported_yet",
                "Regeneration is not supported in the current version"
        );
    }

    private BigDecimal findSeedMetric(
            ProtocolGenerationContext context,
            UUID registrationId
    ) {
        return context.getParticipants().stream()
                .filter(participant -> participant.registrationId().equals(registrationId))
                .map(SeedParticipant::seedMetric)
                .findFirst()
                .orElse(null);
    }

    private List<GeneratedCompetitionUnit> persist(ProtocolGenerationContext context) {
        List<CompetitionUnitProtocolAccess.CompetitionUnitDraftView> drafts = new ArrayList<>();

        for (int i = 0; i < context.getGroupedAssignments().size(); i++) {
            int sequenceNumber = i + 1;
            String label = context.getUnitLabelPrefix() + " " + sequenceNumber;

            List<CompetitionUnitProtocolAccess.AssignmentDraftView> assignments =
                    context.getGroupedAssignments().get(i).stream()
                            .map(assignment -> new CompetitionUnitProtocolAccess.AssignmentDraftView(
                                    assignment.registrationId(),
                                    assignment.laneOrPosition()
                            ))
                            .toList();

            drafts.add(new CompetitionUnitProtocolAccess.CompetitionUnitDraftView(
                    label,
                    sequenceNumber,
                    "DRAFT",
                    assignments
            ));
        }

        List<CompetitionUnitProtocolAccess.SavedCompetitionUnitView> savedUnits =
                competitionUnitProtocolAccess.createDraftStartProtocol(
                        context.getEventDisciplineId(),
                        drafts
                );

        return savedUnits.stream()
                .map(unit -> new GeneratedCompetitionUnit(
                        unit.competitionUnitId(),
                        unit.label(),
                        unit.sequenceNumber(),
                        unit.assignments().stream()
                                .map(assignment -> new GeneratedAssignment(
                                        assignment.registrationId(),
                                        assignment.laneOrPosition(),
                                        findSeedMetric(context, assignment.registrationId())
                                ))
                                .toList()
                ))
                .toList();
    }

    private JsonNode readSettings(String settingsJson) {
        try {
            if (settingsJson == null || settingsJson.isBlank()) {
                return objectMapper.createObjectNode();
            }

            return objectMapper.readTree(settingsJson);
        } catch (Exception exception) {
            throw new BusinessException(
                    "protocol.invalid_settings",
                    "Event discipline settings JSON is invalid"
            );
        }
    }

    private List<Integer> readPositionOrder(JsonNode node) {
        List<Integer> values = new ArrayList<>();

        if (!node.isArray()) {
            return values;
        }

        for (JsonNode item : node) {
            if (!item.isInt()) {
                throw new BusinessException(
                        "protocol.invalid_settings",
                        "Protocol positionOrder must contain integers only"
                );
            }

            values.add(item.asInt());
        }

        return values;
    }

    private BigDecimal extractSeedMetric(
            String registrationMeta,
            String seedMetricKey
    ) {
        try {
            if (registrationMeta == null || registrationMeta.isBlank()) {
                return null;
            }

            JsonNode root = objectMapper.readTree(registrationMeta);
            JsonNode metricNode = root.path(seedMetricKey);

            if (metricNode.isMissingNode() || metricNode.isNull()) {
                return null;
            }

            if (!metricNode.isNumber()) {
                throw new BusinessException(
                        "protocol.invalid_seed_metric",
                        "Seed metric must be numeric"
                );
            }

            return metricNode.decimalValue();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(
                    "protocol.invalid_seed_metric",
                    "Registration meta JSON is invalid"
            );
        }
    }
}