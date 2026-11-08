package com.acme.sportplatform.protocol.application;
import java.util.UUID;
import org.springframework.stereotype.Service;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.ProtocolDisciplineLookup;
import com.acme.sportplatform.events.EventRegulationLookup;
import com.acme.sportplatform.regulations.RegulationBundleAccess;
@Service
public class BoundRegulationRules {
    private final EventRegulationLookup events;
    private final RegulationBundleAccess documents;
    public BoundRegulationRules(EventRegulationLookup events, RegulationBundleAccess documents) {
        this.events = events; this.documents = documents;
    }
    public UUID resolve(ProtocolDisciplineLookup.View discipline, UUID requested) {
        if (discipline.eventId() == null) return requested;
        var binding = documents.binding(events.versionId(discipline.eventId()));
        if (binding == null) return requested;
        if (!binding.competitionFormat().equals(discipline.competitionFormat()) || !binding.unitType().equals(discipline.unitType())) {
            throw new BusinessException("protocol.rule_version_incompatible", "Regulation rules do not match this discipline");
        }
        if (requested != null && !requested.equals(binding.ruleVersionId())) {
            throw new BusinessException("protocol.regulation_rule_mismatch", "Use the rule version bound to the event regulation");
        }
        return binding.ruleVersionId();
    }
}
