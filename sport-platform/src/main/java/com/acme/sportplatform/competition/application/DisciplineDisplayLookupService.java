package com.acme.sportplatform.competition.application;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.competition.DisciplineDisplayLookup;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineRepository;

@Service
@Transactional(readOnly = true)
public class DisciplineDisplayLookupService implements DisciplineDisplayLookup {
    private final EventDisciplineRepository disciplines;

    public DisciplineDisplayLookupService(EventDisciplineRepository disciplines) {
        this.disciplines = disciplines;
    }

    @Override
    public Map<UUID, Label> labels(Set<UUID> disciplineIds) {
        if (disciplineIds.isEmpty()) return Map.of();
        Map<UUID, Label> result = new HashMap<>();
        disciplines.findAllById(disciplineIds).forEach(discipline -> {
            if (disciplineIds.contains(discipline.getId())) {
                result.put(discipline.getId(),
                        new Label(discipline.getEventId(), discipline.getName()));
            }
        });
        return Map.copyOf(result);
    }
}
