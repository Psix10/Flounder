package com.acme.sportplatform.protocol.application;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.ProtocolDisciplineLookup;
import com.acme.sportplatform.events.EventRegulationLookup;
import com.acme.sportplatform.regulations.RegulationBundleAccess;

class BoundRegulationRulesTest {

    private EventRegulationLookup events;
    private RegulationBundleAccess documents;
    private ProtocolDisciplineLookup.View discipline;
    private BoundRegulationRules subject;

    private UUID eventId;
    private UUID regulationVersionId;
    private UUID boundRuleVersionId;

    @BeforeEach
    void setUp() {
        events = mock(EventRegulationLookup.class);
        documents = mock(
                RegulationBundleAccess.class,
                RETURNS_DEEP_STUBS
        );
        discipline = mock(ProtocolDisciplineLookup.View.class);

        subject = new BoundRegulationRules(events, documents);

        eventId = UUID.randomUUID();
        regulationVersionId = UUID.randomUUID();
        boundRuleVersionId = UUID.randomUUID();

        when(discipline.eventId()).thenReturn(eventId);
        when(events.versionId(eventId))
                .thenReturn(regulationVersionId);
    }

    private void configureBinding(
            String competitionFormat,
            String unitType
    ) {
        when(discipline.competitionFormat())
                .thenReturn("INDIVIDUAL");
        when(discipline.unitType())
                .thenReturn("HEAT");

        var binding = documents.binding(regulationVersionId);

        when(binding.competitionFormat())
                .thenReturn(competitionFormat);
        when(binding.unitType())
                .thenReturn(unitType);
        when(binding.ruleVersionId())
                .thenReturn(boundRuleVersionId);
    }

    @Test
    void acceptsRequestedVersionMatchingBinding() {
        configureBinding("INDIVIDUAL", "HEAT");

        UUID actual = subject.resolve(
                discipline,
                boundRuleVersionId
        );

        assertEquals(boundRuleVersionId, actual);
    }

    @Test
    void selectsBoundVersionWhenRequestedVersionIsAbsent() {
        configureBinding("INDIVIDUAL", "HEAT");

        UUID actual = subject.resolve(discipline, null);

        assertEquals(boundRuleVersionId, actual);
    }

    @Test
    void rejectsRequestedVersionDifferentFromBinding() {
        configureBinding("INDIVIDUAL", "HEAT");
        UUID otherVersionId = UUID.randomUUID();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> subject.resolve(
                        discipline,
                        otherVersionId
                )
        );

        assertEquals(
                "Use the rule version bound to the event regulation",
                exception.getMessage()
        );
    }

    @Test
    void rejectsBindingWithIncompatibleCompetitionFormat() {
        configureBinding("TEAM", "HEAT");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> subject.resolve(
                        discipline,
                        boundRuleVersionId
                )
        );

        assertEquals(
                "Regulation rules do not match this discipline",
                exception.getMessage()
        );
    }

    @Test
    void rejectsBindingWithIncompatibleUnitType() {
        configureBinding("INDIVIDUAL", "MATCH");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> subject.resolve(
                        discipline,
                        boundRuleVersionId
                )
        );

        assertEquals(
                "Regulation rules do not match this discipline",
                exception.getMessage()
        );
    }

    @Test
    void preservesRequestedVersionWhenBindingIsAbsent() {
        when(documents.binding(regulationVersionId))
                .thenReturn(null);

        UUID requestedVersionId = UUID.randomUUID();

        UUID actual = subject.resolve(
                discipline,
                requestedVersionId
        );

        assertEquals(requestedVersionId, actual);
    }

    @Test
    void returnsNullWhenBothBindingAndRequestedVersionAreAbsent() {
        when(documents.binding(regulationVersionId))
                .thenReturn(null);

        UUID actual = subject.resolve(discipline, null);

        assertNull(actual);
    }

    @Test
    void preservesRequestedVersionWhenEventIsAbsent() {
        when(discipline.eventId()).thenReturn(null);

        UUID requestedVersionId = UUID.randomUUID();

        UUID actual = subject.resolve(
                discipline,
                requestedVersionId
        );

        assertEquals(requestedVersionId, actual);
        verifyNoInteractions(events, documents);
    }
}