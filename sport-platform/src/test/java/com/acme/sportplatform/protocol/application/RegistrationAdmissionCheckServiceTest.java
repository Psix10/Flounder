package com.acme.sportplatform.protocol.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.events.EventRegulationLookup;
import com.acme.sportplatform.events.ProtocolEventLookup;
import com.acme.sportplatform.protocol.api.RuleBlockRequest;
import com.acme.sportplatform.protocol.domain.CompetitionFormat;
import com.acme.sportplatform.protocol.domain.ProtocolRuleSetStatus;
import com.acme.sportplatform.protocol.domain.ProtocolUnitType;
import com.acme.sportplatform.protocol.infrastructure.jpa.*;
import com.acme.sportplatform.regulations.RegulationBundleAccess;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RegistrationAdmissionCheckServiceTest {
    private final UUID discipline = UUID.randomUUID();
    private final UUID registration = UUID.randomUUID();
    private final UUID event = UUID.randomUUID();
    private final UUID regulation = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();
    private final UUID setId = UUID.randomUUID();
    private final UUID sport = UUID.randomUUID();

    private final EventDisciplineLookup disciplines =
            mock(EventDisciplineLookup.class, RETURNS_DEEP_STUBS);
    private final ProtocolEventLookup events =
            mock(ProtocolEventLookup.class, RETURNS_DEEP_STUBS);
    private final EventRegulationLookup eventRegulations = mock(EventRegulationLookup.class);
    private final RegulationBundleAccess regulations = mock(RegulationBundleAccess.class);
    private final ProtocolRuleSetVersionRepository versions = mock(ProtocolRuleSetVersionRepository.class);
    private final ProtocolRuleSetRepository sets = mock(ProtocolRuleSetRepository.class);
    private final ProtocolRuleBlockRepository blocks = mock(ProtocolRuleBlockRepository.class);
    private final ProtocolRuleSetValidator validator = mock(ProtocolRuleSetValidator.class);
    private final ObjectMapper json = new ObjectMapper();

    private final RegistrationAdmissionCheckService service =
            new RegistrationAdmissionCheckService(
                    disciplines, events, eventRegulations, regulations,
                    versions, sets, blocks, validator, json);

    @BeforeEach
    void setup() {
        var view = disciplines.getPublishedById(discipline);
        when(view.id()).thenReturn(discipline);
        when(view.eventId()).thenReturn(event);
        when(view.competitionFormat()).thenReturn("INDIVIDUAL");
        when(view.unitType()).thenReturn("HEAT");
        when(view.settingsJson()).thenReturn("{}");
        when(eventRegulations.versionId(event)).thenReturn(regulation);
    }

    private ProtocolRuleSetVersionEntity boundVersion() {
        when(regulations.binding(regulation)).thenReturn(
                new RegulationBundleAccess.Binding(versionId, "INDIVIDUAL", "HEAT"));
        var version = new ProtocolRuleSetVersionEntity();
        version.setId(versionId);
        version.setRuleSetId(setId);
        version.setStatus(ProtocolRuleSetStatus.PUBLISHED);
        when(versions.findById(versionId)).thenReturn(Optional.of(version));
        return version;
    }

    private void completeRules() throws Exception {
        boundVersion();

        var set = new ProtocolRuleSetEntity();
        set.setId(setId);
        set.setSportId(sport);
        set.setCode("test");
        set.setName("Test rules");
        set.setCompetitionFormat(CompetitionFormat.INDIVIDUAL);
        set.setUnitType(ProtocolUnitType.HEAT);
        when(sets.findById(setId)).thenReturn(Optional.of(set));
        when(events.getById(event).sportId()).thenReturn(sport);
        when(blocks.findByRuleSetVersionIdOrderByOrderIndexAsc(versionId))
                .thenReturn(List.of());

        // Deliberately stub validation output: this class tests loader orchestration.
        when(validator.validate(any())).thenReturn(List.of(
                new RuleBlockRequest(0, "ELIGIBILITY_CONFIRMED_ONLY",
                        json.readTree("{\"allowedGenders\":[\"MALE\"]}")),
                new RuleBlockRequest(1, "SEED_BY_METRIC_ASC_NULLS_LAST",
                        json.readTree("{\"metricKey\":\"seedTimeSeconds\"}")),
                new RuleBlockRequest(2, "GROUP_BY_CAPACITY",
                        json.readTree("{\"capacity\":2,\"unitLabelPrefix\":\"Heat\"}")),
                new RuleBlockRequest(3, "ASSIGN_CENTER_OUT",
                        json.readTree("{\"positionOrder\":[1,2]}"))
        ));
    }

    @Test
    void absentBindingUsesLegacyWithoutReadingRuleRepositories() {
        assertDoesNotThrow(() -> service.verify(discipline, registration, "{}"));
        verifyNoInteractions(versions, sets, blocks, validator, events);
    }

    @Test
    void absentRegulationIdDoesNotQueryBindingWithNull() {
        when(eventRegulations.versionId(event)).thenReturn(null);
        assertDoesNotThrow(() -> service.verify(discipline, registration, "{}"));
        verifyNoInteractions(regulations, versions, sets, blocks, validator, events);
    }

    @Test
    void legacyStillRejectsDisciplineGenderMismatch() {
        when(disciplines.getPublishedById(discipline).settingsJson())
                .thenReturn("{\"gender\":\"MALE\"}");
        assertThrows(ProtocolEligibilityException.class, () ->
                service.verify(discipline, registration, "{\"gender\":\"FEMALE\"}"));
        verifyNoInteractions(versions, sets, blocks, validator);
    }

    @Test
    void categoryFilterWithoutBindingIsNotSilentlyAccepted() {
        when(disciplines.getPublishedById(discipline).settingsJson())
                .thenReturn("{\"allowedCategoryCodes\":[\"JUNIOR\"]}");
        assertThrows(BusinessException.class, () ->
                service.verify(discipline, registration, "{}"));
        verifyNoInteractions(versions, sets, blocks, validator);
    }

    @Test
    void bindingReadFailureIsPropagatedWithoutLegacyFallback() {
        var failure = new IllegalStateException("Synthetic binding read failure");
        when(regulations.binding(regulation)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () ->
                service.verify(discipline, registration, "{}")));
        verifyNoInteractions(versions, sets, blocks, validator);
    }

    @Test
    void incompatibleBindingIsRejectedBeforeReadingVersion() {
        when(regulations.binding(regulation)).thenReturn(
                new RegulationBundleAccess.Binding(versionId, "OTHER", "HEAT"));
        assertThrows(BusinessException.class, () ->
                service.verify(discipline, registration, "{}"));
        verifyNoInteractions(versions, sets, blocks, validator);
    }

    @Test
    void missingBoundVersionIsNotLegacy() {
        boundVersion();
        when(versions.findById(versionId)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () ->
                service.verify(discipline, registration, "{}"));
        verifyNoInteractions(sets, blocks, validator);
    }

    @Test
    void unpublishedVersionIsRejected() {
        boundVersion().setStatus(null);
        assertThrows(BusinessException.class, () ->
                service.verify(discipline, registration, "{}"));
        verifyNoInteractions(sets, blocks, validator);
    }

    @Test
    void missingRuleSetIsRejected() {
        boundVersion();
        when(sets.findById(setId)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () ->
                service.verify(discipline, registration, "{}"));
        verifyNoInteractions(blocks, validator);
    }

    @Test
    void incompatibleSportIsRejectedBeforeReadingBlocks() throws Exception {
        completeRules();
        when(events.getById(event).sportId()).thenReturn(UUID.randomUUID());
        assertThrows(BusinessException.class, () ->
                service.verify(discipline, registration, "{\"gender\":\"MALE\"}"));
        verify(blocks, never()).findByRuleSetVersionIdOrderByOrderIndexAsc(any());
        verify(validator, never()).validate(any());
    }

    @Test
    void publishedCompatibleRulesAllowMatchingSnapshot() throws Exception {
        completeRules();
        assertDoesNotThrow(() ->
                service.verify(discipline, registration, "{\"gender\":\"MALE\"}"));
        verify(validator).validate(any());
        verify(sets, never()).findLockedById(any());
    }

    @Test
    void loadedGeneralEligibilityRejectsWrongSnapshot() throws Exception {
        completeRules();
        assertThrows(ProtocolEligibilityException.class, () ->
                service.verify(discipline, registration, "{\"gender\":\"FEMALE\"}"));
        verify(validator).validate(any());
    }

    @Test
    void invalidPipelineIsNotReplacedByLegacyRules() throws Exception {
        completeRules();
        var failure = new BusinessException("protocol.invalid_rule_pipeline", "Synthetic failure");
        when(validator.validate(any())).thenThrow(failure);
        assertSame(failure, assertThrows(BusinessException.class, () ->
                service.verify(discipline, registration, "{}")));
    }
}