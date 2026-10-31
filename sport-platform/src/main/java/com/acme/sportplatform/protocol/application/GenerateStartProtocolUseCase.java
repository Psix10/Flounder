package com.acme.sportplatform.protocol.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
                eventProtocolAccess.getPublishedEventDiscipline(
                        eventDisciplineId
                );

        JsonNode root = readSettings(discipline.settingsJson());
        JsonNode protocolNode = root.path("protocol");

        int unitCapacity = readUnitCapacity(
                protocolNode.path("unitCapacity")
        );

        String unitLabelPrefix = protocolNode
                .path("unitLabelPrefix")
                .asText("Heat");

        String seedMetricKey = protocolNode
                .path("seedMetricKey")
                .asText(null);

        boolean allowRegenerate = protocolNode
                .path("allowRegenerate")
                .asBoolean(false);

        List<Integer> positionOrder = readPositionOrder(
                protocolNode.path("positionOrder")
        );

        validateProtocolSettings(
                unitCapacity,
                unitLabelPrefix,
                seedMetricKey,
                positionOrder
        );

        boolean hasExistingUnits =
                competitionUnitProtocolAccess.hasCompetitionUnits(
                        eventDisciplineId
                );

        boolean regenerate = request != null
                && Boolean.TRUE.equals(request.regenerate());

        validateGenerationState(
                hasExistingUnits,
                regenerate,
                allowRegenerate
        );

        List<RegistrationProtocolAccess.ConfirmedRegistrationView>
                registrations = registrationProtocolAccess
                        .findConfirmedByEventDisciplineId(
                                eventDisciplineId
                        );

        if (registrations.isEmpty()) {
            throw new BusinessException(
                    "protocol.no_confirmed_registrations",
                    "No confirmed registrations found for this discipline"
            );
        }

        List<SeedParticipant> participants = registrations.stream()
                .map(registration -> new SeedParticipant(
                        registration.registrationId(),
                        extractSeedMetric(
                                registration.registrationMeta(),
                                seedMetricKey
                        )
                ))
                .toList();

        Map<UUID, BigDecimal> seedMetricByRegistrationId =
                indexParticipants(participants);

        ProtocolGenerationContext context =
                new ProtocolGenerationContext(
                        eventDisciplineId,
                        unitCapacity,
                        unitLabelPrefix,
                        positionOrder,
                        participants
                );

        ruleExecutorRegistry
                .get("ELIGIBILITY_CONFIRMED_ONLY")
                .apply(context);

        ruleExecutorRegistry
                .get("SEED_BY_METRIC_ASC_NULLS_LAST")
                .apply(context);

        ruleExecutorRegistry
                .get("GROUP_BY_CAPACITY")
                .apply(context);

        ruleExecutorRegistry
                .get("ASSIGN_CENTER_OUT")
                .apply(context);

        validateGeneratedAssignments(
                context,
                seedMetricByRegistrationId.keySet()
        );

        List<GeneratedCompetitionUnit> savedUnits = persist(
                context,
                seedMetricByRegistrationId
        );

        return new ProtocolGenerationResult(
                eventDisciplineId,
                savedUnits
        );
    }

    private int readUnitCapacity(JsonNode node) {
        if (!node.isIntegralNumber()
                || !node.canConvertToInt()
                || node.intValue() <= 0) {
            throw invalidSettings(
                    "Protocol unitCapacity must be a positive integer"
            );
        }

        return node.intValue();
    }

    private void validateProtocolSettings(
            int unitCapacity,
            String unitLabelPrefix,
            String seedMetricKey,
            List<Integer> positionOrder
    ) {
        if (unitLabelPrefix == null || unitLabelPrefix.isBlank()) {
            throw invalidSettings(
                    "Protocol unitLabelPrefix must not be blank"
            );
        }

        if (seedMetricKey == null || seedMetricKey.isBlank()) {
            throw invalidSettings(
                    "Protocol seedMetricKey is required"
            );
        }

        if (positionOrder.size() != unitCapacity) {
            throw invalidPositionOrder(
                    "Position order must contain exactly unitCapacity positions"
            );
        }

        Set<Integer> uniquePositions = new HashSet<>();

        for (Integer position : positionOrder) {
            if (position == null
                    || position < 1
                    || position > unitCapacity) {
                throw invalidPositionOrder(
                        "Positions must be between 1 and unitCapacity"
                );
            }

            if (!uniquePositions.add(position)) {
                throw invalidPositionOrder(
                        "Position order must not contain duplicate positions"
                );
            }
        }
    }

    private Map<UUID, BigDecimal> indexParticipants(
            List<SeedParticipant> participants
    ) {
        Map<UUID, BigDecimal> index = new HashMap<>();

        for (SeedParticipant participant : participants) {
            UUID registrationId = participant.registrationId();

            if (registrationId == null
                    || index.containsKey(registrationId)) {
                throw new BusinessException(
                        "protocol.invalid_participants",
                        "Registration IDs must be present and unique"
                );
            }

            index.put(registrationId, participant.seedMetric());
        }

        return index;
    }

    private void validateGeneratedAssignments(
            ProtocolGenerationContext context,
            Set<UUID> expectedRegistrationIds
    ) {
        Set<UUID> assignedRegistrationIds = new HashSet<>();

        for (List<GeneratedAssignment> group
                : context.getGroupedAssignments()) {

            if (group.isEmpty()
                    || group.size() > context.getUnitCapacity()) {
                throw invalidGeneration(
                        "Generated unit must contain between 1 "
                                + "and unitCapacity participants"
                );
            }

            Set<Integer> occupiedPositions = new HashSet<>();

            for (int i = 0; i < group.size(); i++) {
                GeneratedAssignment assignment = group.get(i);
                UUID registrationId = assignment.registrationId();
                Integer position = assignment.laneOrPosition();

                if (!expectedRegistrationIds.contains(registrationId)) {
                    throw invalidGeneration(
                            "Generated assignment references "
                                    + "an unexpected registration"
                    );
                }

                if (!assignedRegistrationIds.add(registrationId)) {
                    throw invalidGeneration(
                            "Registration appears more than once "
                                    + "in the generated protocol"
                    );
                }

                if (position == null
                        || position < 1
                        || position > context.getUnitCapacity()
                        || !occupiedPositions.add(position)) {
                    throw invalidGeneration(
                            "Generated positions must be valid "
                                    + "and unique within each unit"
                    );
                }

                if (!position.equals(
                        context.getPositionOrder().get(i)
                )) {
                    throw invalidGeneration(
                            "Generated position does not match "
                                    + "the configured position order"
                    );
                }
            }
        }

        if (!assignedRegistrationIds.equals(expectedRegistrationIds)) {
            throw invalidGeneration(
                    "Generated protocol does not cover "
                            + "all eligible registrations"
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
                    "Start protocol has already been generated "
                            + "for this discipline"
            );
        }

        throw new BusinessException(
                "protocol.regenerate_not_supported_yet",
                "Regeneration is not supported in the current version"
        );
    }

    private List<GeneratedCompetitionUnit> persist(
            ProtocolGenerationContext context,
            Map<UUID, BigDecimal> seedMetricByRegistrationId
    ) {
        List<CompetitionUnitProtocolAccess.CompetitionUnitDraftView>
                drafts = new ArrayList<>();

        for (int i = 0;
                i < context.getGroupedAssignments().size();
                i++) {

            int sequenceNumber = i + 1;
            String label = context.getUnitLabelPrefix()
                    + " " + sequenceNumber;

            List<CompetitionUnitProtocolAccess.AssignmentDraftView>
                    assignments = context
                            .getGroupedAssignments()
                            .get(i)
                            .stream()
                            .map(assignment ->
                                    new CompetitionUnitProtocolAccess
                                            .AssignmentDraftView(
                                            assignment.registrationId(),
                                            assignment.laneOrPosition()
                                    )
                            )
                            .toList();

            drafts.add(
                    new CompetitionUnitProtocolAccess
                            .CompetitionUnitDraftView(
                            label,
                            sequenceNumber,
                            "DRAFT",
                            assignments
                    )
            );
        }

        List<CompetitionUnitProtocolAccess.SavedCompetitionUnitView>
                savedUnits = competitionUnitProtocolAccess
                        .createDraftStartProtocol(
                                context.getEventDisciplineId(),
                                drafts
                        );

        return savedUnits.stream()
                .map(unit -> new GeneratedCompetitionUnit(
                        unit.competitionUnitId(),
                        unit.label(),
                        unit.sequenceNumber(),
                        unit.assignments().stream()
                                .map(assignment ->
                                        new GeneratedAssignment(
                                                assignment.registrationId(),
                                                assignment.laneOrPosition(),
                                                seedMetricByRegistrationId.get(
                                                        assignment.registrationId()
                                                )
                                        )
                                )
                                .toList()
                ))
                .toList();
    }

    private JsonNode readSettings(String settingsJson) {
        if (settingsJson == null || settingsJson.isBlank()) {
            return objectMapper.createObjectNode();
        }

        JsonNode root;

        try {
            root = objectMapper.readTree(settingsJson);
        } catch (Exception exception) {
            throw invalidSettings(
                    "Event discipline settings JSON is invalid"
            );
        }

        if (root == null || !root.isObject()) {
            throw invalidSettings(
                    "Event discipline settings must be a JSON object"
            );
        }

        return root;
    }

    private List<Integer> readPositionOrder(JsonNode node) {
        List<Integer> values = new ArrayList<>();

        if (!node.isArray()) {
            return values;
        }

        for (JsonNode item : node) {
            if (!item.isIntegralNumber()
                    || !item.canConvertToInt()) {
                throw invalidSettings(
                        "Protocol positionOrder must contain integers only"
                );
            }

            values.add(item.intValue());
        }

        return values;
    }

    private BigDecimal extractSeedMetric(
            String registrationMeta,
            String seedMetricKey
    ) {
        if (registrationMeta == null || registrationMeta.isBlank()) {
            return null;
        }

        JsonNode root;

        try {
            root = objectMapper.readTree(registrationMeta);
        } catch (Exception exception) {
            throw new BusinessException(
                    "protocol.invalid_seed_metric",
                    "Registration meta JSON is invalid"
            );
        }

        if (root == null || !root.isObject()) {
            throw new BusinessException(
                    "protocol.invalid_seed_metric",
                    "Registration meta must be a JSON object"
            );
        }

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
    }

    private BusinessException invalidSettings(String message) {
        return new BusinessException(
                "protocol.invalid_settings",
                message
        );
    }

    private BusinessException invalidPositionOrder(String message) {
        return new BusinessException(
                "protocol.invalid_position_order",
                message
        );
    }

    private BusinessException invalidGeneration(String message) {
        return new BusinessException(
                "protocol.invalid_generated_assignments",
                message
        );
    }
}