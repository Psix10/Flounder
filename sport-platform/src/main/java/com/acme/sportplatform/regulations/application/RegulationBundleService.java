package com.acme.sportplatform.regulations.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.acme.sportplatform.regulations.RegulationBundleAccess;
import com.acme.sportplatform.regulations.api.*;
import com.acme.sportplatform.regulations.infrastructure.jpa.*;

@Service
public class RegulationBundleService implements RegulationBundleAccess {
    private final RegulationService service;
    private final RegulationVersionRepository versions;
    private final JdbcTemplate jdbc;
    @PersistenceContext private EntityManager em;
    public RegulationBundleService(RegulationService service, RegulationVersionRepository versions, JdbcTemplate jdbc) {
        this.service = service; this.versions = versions; this.jdbc = jdbc;
    }
    @Override @Transactional(readOnly = true)
    public Binding binding(UUID id) {
        return jdbc.query("""
                select protocol_rule_set_version_id, competition_format, unit_type
                from regulation_protocol_bindings where regulation_version_id = ?
                """, (rs, row) -> new Binding(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)), id)
                .stream().findFirst().orElse(null);
    }
    @Override @Transactional(readOnly = true)
    public Document read(UUID id) {
        var v = service.getVersion(id);
        var t = service.getTemplate(v.regulationTemplateId());
        var sections = v.rulesJson() == null ? null : v.rulesJson().sections();
        var conditions = sections == null ? List.<String>of()
                : sections.stream().map(RegulationSection::title).toList();
        return new Document(v.id(), t.id(), t.sportId(), t.code(), t.name(), t.description(),
                v.versionNo(), v.status(), v.effectiveFrom(), v.notes(), v.createdBy(), conditions, binding(id));
    }
    @Override @Transactional
    public Document publish(UUID sourceId, UUID sportId, String code, String name,
            String description, int versionNo, LocalDate effectiveFrom, String notes,
            List<String> conditions, UUID ruleVersionId, UUID actorId, boolean admin) {
        UUID templateId;
        if (sourceId == null) {
            if (versionNo != 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A new regulation starts at version 1");
            templateId = service.createTemplate(new CreateRegulationTemplateRequest(
                    sportId, code.trim().toUpperCase(java.util.Locale.ROOT), name.trim(), description)).id();
        } else {
            var source = read(sourceId);
            if (!admin && !actorId.equals(source.createdBy())) {
                throw new org.springframework.security.access.AccessDeniedException("Only the owner can publish a new version");
            }
            if (!source.sportId().equals(sportId)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sport cannot change across versions");
            templateId = source.templateId();
            var template = em.find(RegulationTemplateEntity.class, templateId);
            em.refresh(template, LockModeType.PESSIMISTIC_WRITE);
            Integer latest = em.createQuery("select max(v.versionNo) from RegulationVersionEntity v where v.regulationTemplateId = :id", Integer.class)
                    .setParameter("id", templateId).getSingleResult();
            if (latest != null && versionNo <= latest) throw new ResponseStatusException(HttpStatus.CONFLICT, "Use a version number greater than all existing versions");
        }
        var sections = java.util.stream.IntStream.range(0, conditions.size())
                .mapToObj(i -> new RegulationSection("condition-" + (i + 1), conditions.get(i).trim())).toList();
        var version = service.createVersion(templateId, new CreateRegulationVersionRequest(
                versionNo, new RegulationRules(sections), notes, effectiveFrom));
        var entity = versions.findById(version.id()).orElseThrow();
        entity.setCreatedBy(actorId);
        versions.flush();
        if (ruleVersionId != null) {
            jdbc.update("""
                    insert into regulation_protocol_bindings
                    (regulation_version_id, protocol_rule_set_version_id, competition_format, unit_type, created_by_user_id)
                    values (?, ?, 'INDIVIDUAL', 'HEAT', ?)
                    """, version.id(), ruleVersionId, actorId);
        }
        service.publishVersion(version.id());
        return read(version.id());
    }
}
