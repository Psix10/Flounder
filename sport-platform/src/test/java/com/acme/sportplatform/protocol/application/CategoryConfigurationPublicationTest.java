package com.acme.sportplatform.protocol.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.protocol.ProtocolConfigurationPublication.Settings;
import com.acme.sportplatform.protocol.api.CreateProtocolRuleSetRequest;
import com.acme.sportplatform.protocol.api.ProtocolRuleSetResponse;
import com.acme.sportplatform.protocol.infrastructure.jpa.ProtocolRuleBlockEntity;
import com.acme.sportplatform.protocol.infrastructure.jpa.ProtocolRuleBlockRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class CategoryConfigurationPublicationTest {
    private final ObjectMapper json = new ObjectMapper();
    private final CreateProtocolRuleSetUseCase create = mock(CreateProtocolRuleSetUseCase.class);
    private final PublishProtocolRuleSetVersionUseCase publish = mock(PublishProtocolRuleSetVersionUseCase.class);
    private final ProtocolRuleBlockRepository blocks = mock(ProtocolRuleBlockRepository.class);
    private final UUID sport = UUID.randomUUID(), actor = UUID.randomUUID();
    private final UUID set = UUID.randomUUID(), version = UUID.randomUUID();
    private ProtocolConfigurationPublicationService service;

    @BeforeEach
    void setup() {
        service = new ProtocolConfigurationPublicationService(create, publish, blocks, json);
    }

    private List<JsonNode> categories() throws Exception {
        return List.of(
                json.readTree("""
                    {"code":"MEN","seedingGroupCode":"OPEN","eligibility":{"allowedGenders":["MALE"]}}
                    """),
                json.readTree("""
                    {"code":"WOMEN","seedingGroupCode":"OPEN","eligibility":{"allowedGenders":["FEMALE"]}}
                    """)
        );
    }

    private Settings settings(List<JsonNode> categories) {
        return new Settings(2, List.of(1, 2), "seedTimeSeconds", "SLOWEST_FIRST", "FIRST", categories);
    }

    private void stubPublication() {
        ProtocolRuleSetResponse draft = mock(ProtocolRuleSetResponse.class);
        when(draft.id()).thenReturn(set);
        when(draft.ruleSetVersionId()).thenReturn(version);
        when(create.execute(any(CreateProtocolRuleSetRequest.class), eq(actor))).thenReturn(draft);
        when(publish.execute(set, version, actor, false)).thenReturn(draft);
    }

    private void givenStoredCategories(List<JsonNode> categories) {
        var group = json.createObjectNode().put("capacity", 2)
                .put("unitLabelPrefix", "Heat").put("heatOrder", "SLOWEST_FIRST").put("partialHeat", "FIRST");
        if (categories != null) group.set("categories", json.valueToTree(categories));
        var positions = json.createObjectNode();
        positions.set("positionOrder", json.valueToTree(List.of(1, 2)));
        ProtocolRuleBlockEntity eligibility = mock(ProtocolRuleBlockEntity.class);
        ProtocolRuleBlockEntity seed = mock(ProtocolRuleBlockEntity.class);
        ProtocolRuleBlockEntity grouping = mock(ProtocolRuleBlockEntity.class);
        ProtocolRuleBlockEntity assignment = mock(ProtocolRuleBlockEntity.class);
        when(seed.getConfig()).thenReturn(json.createObjectNode().put("metricKey", "seedTimeSeconds"));
        when(grouping.getConfig()).thenReturn(group);
        when(assignment.getConfig()).thenReturn(positions);
        when(blocks.findByRuleSetVersionIdOrderByOrderIndexAsc(version))
                .thenReturn(List.of(eligibility, seed, grouping, assignment));
    }

    @Test
    void shouldPublishCategoriesInsideGroupingStage() throws Exception {
        stubPublication();
        var categories = categories();
        assertEquals(version, service.publish(sport, "Test", settings(categories), actor));
        var capture = ArgumentCaptor.forClass(CreateProtocolRuleSetRequest.class);
        verify(create).execute(capture.capture(), eq(actor));
        var stages = capture.getValue().blocks();
        assertEquals(4, stages.size());
        assertEquals("GROUP_BY_CAPACITY", stages.get(2).executorCode());
        assertEquals(json.valueToTree(categories), stages.get(2).config().get("categories"));
        assertFalse(stages.get(0).config().has("categories"));
    }

    @Test
    void shouldKeepOldFiveArgumentSettingsConstructor() {
        stubPublication();
        var legacy = new Settings(2, List.of(1, 2), "seedTimeSeconds", "FASTEST_FIRST", "LAST");
        service.publish(sport, "Legacy", legacy, actor);
        var capture = ArgumentCaptor.forClass(CreateProtocolRuleSetRequest.class);
        verify(create).execute(capture.capture(), eq(actor));
        assertFalse(capture.getValue().blocks().get(2).config().has("categories"));
    }

    @Test
    void shouldReadLegacyWithoutInventingCategories() {
        givenStoredCategories(null);
        var result = service.read(version);
        assertTrue(result.categories().isEmpty());
        assertEquals(2, result.capacity());
        assertEquals(List.of(1, 2), result.positionOrder());
    }

    @Test
    void shouldReadSavedCategoriesWithoutDroppingAgeRules() throws Exception {
        List<JsonNode> saved = List.of(json.readTree("""
            {"code":"JUNIOR","seedingGroupCode":"OPEN","eligibility":{
              "allowedGenders":["FEMALE"],"ageMethod":"BY_YEAR",
              "ageReferenceDate":"2026-12-31","minAge":12,"maxAge":17}}
            """));
        givenStoredCategories(saved);
        var result = service.read(version);
        assertEquals(saved, result.categories());
        assertNotSame(saved.get(0), result.categories().get(0));
    }

    @Test
    void shouldRejectDuplicateCategoryCodesBeforeCreatingRules() throws Exception {
        var category = categories().get(0);
        var failure = assertThrows(BusinessException.class,
                () -> service.publish(sport, "Bad", settings(List.of(category, category.deepCopy())), actor));
        assertEquals("protocol.invalid_rule_config", failure.getCode());
        verifyNoInteractions(create, publish);
    }

    @Test
    void shouldRejectInvalidGenderBeforeCreatingRules() throws Exception {
        List<JsonNode> invalid = List.of(json.readTree("""
            {"code":"A","seedingGroupCode":"G","eligibility":{"allowedGenders":["OTHER"]}}
            """));
        assertThrows(BusinessException.class,
                () -> service.publish(sport, "Bad", settings(invalid), actor));
        verifyNoInteractions(create, publish);
    }

    @Test
    void shouldRoundTripSettingsJsonWithCategories() throws Exception {
        var original = settings(categories());
        var restored = json.readValue(json.writeValueAsString(original), Settings.class);
        assertEquals(original, restored);
    }
}
