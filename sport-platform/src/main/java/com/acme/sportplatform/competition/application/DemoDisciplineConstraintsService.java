package com.acme.sportplatform.competition.application;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.acme.sportplatform.competition.DemoDisciplineConstraints;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineRepository;
import com.acme.sportplatform.common.DemoDataException;
@Service
public class DemoDisciplineConstraintsService implements DemoDisciplineConstraints {
    private final EventDisciplineRepository repository;
    public DemoDisciplineConstraintsService(EventDisciplineRepository repository) { this.repository = repository; }
    @Override @Transactional(readOnly = true)
    public View read(UUID id) {
        var d = repository.findById(id).orElseThrow(() -> new DemoDataException(404,
                "demo.discipline_not_found", "Дисциплина не найдена."));
        return new View(d.getParticipantLimit(), d.getResultType(), d.getRankingStrategy(), d.getSettingsJson());
    }
}
