package com.acme.sportplatform.results.application;

import java.time.OffsetDateTime;
import java.util.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.results.*;
import com.acme.sportplatform.results.infrastructure.jpa.*;

@Service
@Transactional
public class ResultsProtocolGatewayService implements ResultsProtocolGateway {
    private final CompetitionUnitRepository units;
    private final CompetitionUnitEntryRepository entries;
    private final JdbcTemplate jdbc;
    @PersistenceContext private EntityManager entityManager;
    public ResultsProtocolGatewayService(CompetitionUnitRepository units,
            CompetitionUnitEntryRepository entries, JdbcTemplate jdbc) {
        this.units = units; this.entries = entries; this.jdbc = jdbc;
    }
    @Override @Transactional(readOnly = true)
    public boolean hasCompetitionUnits(UUID id) {
        return !units.findByEventDisciplineIdOrderBySequenceNumberAsc(id).isEmpty();
    }
    @Override @Transactional(readOnly = true)
    public List<ResultsCompetitionUnitView> findCompetitionUnitsByEventDisciplineId(UUID id) {
        List<ResultsCompetitionUnitView> result = new ArrayList<>();
        for (var unit : units.findByEventDisciplineIdOrderBySequenceNumberAsc(id)) {
            var assigned = entries.findByCompetitionUnitId(unit.getId()).stream().map(e ->
                    new ResultsCompetitionUnitEntryView(e.getId(), e.getRegistrationId(), e.getLaneOrPosition(), e.getCategoryCode(), e.getSeedingGroupCode())).toList();
            result.add(new ResultsCompetitionUnitView(unit.getId(), unit.getLabel(), unit.getSequenceNumber(),
                    unit.getStatus(), assigned, unit.getScheduledAt()));
        }
        return result;
    }
    @Override
    public List<ResultsCompetitionUnitView> createDraftStartProtocol(UUID id,
            List<ResultsCompetitionUnitDraft> drafts) {
        validate(drafts);
        return insert(id, drafts);
    }
    @Override
    public List<ResultsCompetitionUnitView> replaceDraftStartProtocol(UUID id,
            List<ResultsCompetitionUnitDraft> drafts) {
        validate(drafts);
        entityManager.flush();
        List<LockedUnit> locked = jdbc.query("""
                SELECT id, status FROM competition_units
                WHERE event_discipline_id = ? ORDER BY id FOR UPDATE
                """, (rs, row) -> new LockedUnit(rs.getObject("id", UUID.class), rs.getString("status")), id);
        if (locked.isEmpty()) throw error("protocol.start_protocol_not_found", "No units to regenerate");
        if (locked.stream().anyMatch(u -> "PUBLISHED".equals(u.status()))) {
            throw error("protocol.regeneration_unit_published", "Published units cannot be regenerated");
        }
        if (locked.stream().anyMatch(u -> !"DRAFT".equals(u.status()))) {
            throw error("protocol.regeneration_unit_not_draft", "Only draft units can be regenerated");
        }
        jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT e.id FROM competition_unit_entries e
                    JOIN competition_units u ON u.id = e.competition_unit_id
                    WHERE u.event_discipline_id = ? ORDER BY e.id FOR UPDATE OF e
                ) locked_entries
                """, Long.class, id);
        Long resultCount = jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT r.id FROM results r
                    JOIN competition_unit_entries e ON e.id = r.competition_unit_entry_id
                    JOIN competition_units u ON u.id = e.competition_unit_id
                    WHERE u.event_discipline_id = ? ORDER BY r.id FOR UPDATE OF r
                ) locked_results
                """, Long.class, id);
        if (resultCount != null && resultCount > 0) throw error(
                "protocol.regeneration_has_results", "Cannot regenerate units containing any results");
        jdbc.update("""
                DELETE FROM competition_unit_entries e USING competition_units u
                WHERE e.competition_unit_id = u.id AND u.event_discipline_id = ?
                """, id);
        jdbc.update("DELETE FROM competition_units WHERE event_discipline_id = ?", id);
        entityManager.clear();
        return insert(id, drafts);
    }
    private List<ResultsCompetitionUnitView> insert(UUID id, List<ResultsCompetitionUnitDraft> drafts) {
        OffsetDateTime now = OffsetDateTime.now();
        List<ResultsCompetitionUnitView> result = new ArrayList<>();
        for (var draft : drafts) {
            var unit = new CompetitionUnitEntity(); unit.setId(UUID.randomUUID());
            unit.setEventDisciplineId(id); unit.setLabel(draft.label());
            unit.setSequenceNumber(draft.sequenceNumber()); unit.setStatus("DRAFT");
            unit.setScheduledAt(draft.scheduledAt());
            unit.setCreatedAt(now); unit.setUpdatedAt(now); unit = units.save(unit);
            List<ResultsCompetitionUnitEntryView> assigned = new ArrayList<>();
            for (var item : draft.entries()) {
                var entry = new CompetitionUnitEntryEntity(); entry.setId(UUID.randomUUID());
                entry.setCompetitionUnitId(unit.getId()); entry.setRegistrationId(item.registrationId());
                entry.setLaneOrPosition(item.laneOrPosition()); entry.setCreatedAt(now);
                entry.setCategoryCode(item.categoryCode()); entry.setSeedingGroupCode(item.seedingGroupCode());
                entry = entries.save(entry);
                assigned.add(new ResultsCompetitionUnitEntryView(entry.getId(), entry.getRegistrationId(),
                        entry.getLaneOrPosition(), entry.getCategoryCode(), entry.getSeedingGroupCode()));
            }
            result.add(new ResultsCompetitionUnitView(unit.getId(), unit.getLabel(), unit.getSequenceNumber(),
                    unit.getStatus(), assigned, unit.getScheduledAt()));
        }
        return result;
    }
    private void validate(List<ResultsCompetitionUnitDraft> drafts) {
        if (drafts == null || drafts.isEmpty()) throw error(
                "protocol.invalid_generated_assignments", "At least one generated unit is required");
        Set<UUID> registrations = new HashSet<>(); Set<Integer> sequence = new HashSet<>();
        for (var draft : drafts) {
            if (draft == null || draft.label() == null || draft.label().isBlank() || draft.label().length() > 100
                    || draft.sequenceNumber() == null || draft.sequenceNumber() < 1
                    || !sequence.add(draft.sequenceNumber()) || draft.entries() == null || draft.entries().isEmpty()
                    || (draft.status() != null && !draft.status().isBlank() && !"DRAFT".equals(draft.status()))) {
                throw error("protocol.invalid_generated_assignments", "Invalid generated unit");
            }
            if (draft.scheduledAt() != null && draft.scheduledAt().getNano() % 1000 != 0) {
                throw error("protocol.invalid_scheduled_at", "scheduledAt must have at most microsecond precision");
            }
            Set<String> seedingGroups = new HashSet<>();
            Boolean categorized = null;
            Set<Integer> positions = new HashSet<>();
            for (var entry : draft.entries()) {
                if (entry == null) throw error("protocol.invalid_generated_assignments", "Null assignment");
                boolean hasCategory = entry.categoryCode() != null;
                if (hasCategory != (entry.seedingGroupCode() != null)
                        || (hasCategory && (!entry.categoryCode().matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,79}")
                        || !entry.seedingGroupCode().matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,79}"))))
                    throw error("protocol.invalid_generated_assignments", "Invalid category/group pair");
                if (categorized != null && categorized != hasCategory)
                    throw error("protocol.invalid_generated_assignments", "Cannot mix categorized and legacy assignments");
                categorized = hasCategory;
                if (hasCategory && seedingGroups.add(entry.seedingGroupCode()) && seedingGroups.size() > 1)
                    throw error("protocol.invalid_generated_assignments", "Cannot mix seeding groups within one heat");
                if (entry == null || entry.registrationId() == null || !registrations.add(entry.registrationId())
                        || entry.laneOrPosition() == null || entry.laneOrPosition() < 0
                        || !positions.add(entry.laneOrPosition())) throw error(
                                "protocol.invalid_generated_assignments", "Invalid or duplicate generated assignment");
            }
        }
    }
    private BusinessException error(String code, String message) { return new BusinessException(code, message); }
    private record LockedUnit(UUID id, String status) { }
}
