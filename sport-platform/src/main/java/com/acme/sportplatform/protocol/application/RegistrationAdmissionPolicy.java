package com.acme.sportplatform.protocol.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.DisciplineSettingsPolicy;
import com.acme.sportplatform.protocol.RegistrationProtocolAccess.ConfirmedRegistrationView;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Checks one candidate snapshot without generating units or changing data.
 * A null rules value means an explicitly established absence of binding,
 * never a failed attempt to load bound rules.
 */
public final class RegistrationAdmissionPolicy {
    private final ObjectMapper json;

    public RegistrationAdmissionPolicy(ObjectMapper json) {
        this.json = Objects.requireNonNull(json);
    }

    public void verify(
            UUID registrationId,
            String participantSnapshot,
            String disciplineSettings,
            ResolvedProtocolRules rules
    ) {
        Objects.requireNonNull(registrationId, "Registration ID is required");

        var settings = DisciplineSettingsPolicy.read(disciplineSettings);
        DisciplineSettingsPolicy.validate(settings);

        // Adapter to existing snapshot policies, not a claim about saved status.
        var candidates = List.of(new ConfirmedRegistrationView(
                registrationId, "{}", null, participantSnapshot
        ));

        if (rules == null) {
            if (!DisciplineSettingsPolicy.allowedCategoryCodes(settings).isEmpty()) {
                throw new BusinessException(
                        "registrations.admission_configuration_required",
                        "Для проверки категорий дисциплины требуется привязанная версия правил."
                );
            }

            String gender = DisciplineSettingsPolicy.gender(settings);
            if (gender != null) {
                var restriction = json.createObjectNode();
                restriction.putArray("allowedGenders").add(gender);
                new ProtocolEligibilityVerifier(json).verify(
                        candidates, ProtocolEligibilityPolicy.parse(restriction)
                );
            }
            return;
        }

        var effective = DisciplineProtocolAdmission.apply(
                disciplineSettings, rules, candidates, json, rules.versionId()
        );

        var eligibility = effective.blocks().stream()
                .filter(block -> "ELIGIBILITY_CONFIRMED_ONLY".equals(block.executorCode()))
                .toList();

        var grouping = effective.blocks().stream()
                .filter(block -> "GROUP_BY_CAPACITY".equals(block.executorCode()))
                .toList();

        if (eligibility.size() != 1 || grouping.size() != 1) {
            throw new BusinessException(
                    "protocol.invalid_rule_pipeline",
                    "Exactly one eligibility and grouping block is required"
            );
        }

        new ProtocolEligibilityVerifier(json).verify(
                candidates, ProtocolEligibilityPolicy.parse(eligibility.get(0).config())
        );

        ProtocolCategoryPolicy.parse(grouping.get(0).config())
                .classify(candidates, json);
    }
}