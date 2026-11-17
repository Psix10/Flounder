package com.acme.sportplatform.registrations.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import com.acme.sportplatform.common.DemoDataException;
import com.acme.sportplatform.common.DemoDataMode;
import com.acme.sportplatform.events.EventOperationAccess;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.registrations.DemoRegistrationAccess;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationEntity;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class DemoRegistrationAccessService implements DemoRegistrationAccess {
    private final RegistrationRepository registrations;
    private final ObjectMapper json;
    private final DemoDataMode mode;
    private final EventOperationAccess access;
    public DemoRegistrationAccessService(RegistrationRepository registrations, ObjectMapper json,
            DemoDataMode mode, EventOperationAccess access) {
        this.registrations = registrations; this.json = json; this.mode = mode; this.access = access;
    }
    @Override @Transactional(readOnly = true)
    public long count(UUID disciplineId) {
        return registrations.findByEventDisciplineId(disciplineId).size();
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public List<UUID> createConfirmed(UUID eventId, UUID disciplineId, UUID batchId,
            UUID actorId, List<Entry> entries) {
        mode.requireAllowed(eventId);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof AuthenticatedUserPrincipal principal)
                || !java.util.Objects.equals(principal.getUserId(), actorId)) {
            throw new AccessDeniedException("Authenticated actor mismatch");
        }
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_PLATFORM_ADMIN".equals(a.getAuthority()));
        boolean organizer = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ORGANIZER".equals(a.getAuthority()));
        if (!admin && (!organizer || !access.canManageEvent(eventId, actorId))) {
            throw new AccessDeniedException("Cannot manage demo event");
        }
        if (entries == null || entries.isEmpty() || entries.size() > 64 || count(disciplineId) != 0) {
            throw new DemoDataException(409, "demo.not_empty", "Дисциплина должна быть пустой; максимум 64 тестовых заявки.");
        }
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        List<RegistrationEntity> created = new ArrayList<>();
        for (var entry : entries) {
            var p = entry.profile();
            if (p == null || p.id() == null || p.userId() == null || p.birthDate() == null
                    || p.sportMeta() == null || !Boolean.TRUE.equals(p.sportMeta().get("demoFixture"))
                    || !batchId.toString().equals(p.sportMeta().get("demoBatchId"))
                    || !("male".equals(p.gender()) || "female".equals(p.gender()))) {
                throw new DemoDataException(409, "demo.invalid_fixture", "Профиль не относится к создаваемому демонстрационному набору.");
            }
            var snapshot = json.createObjectNode();
            snapshot.put("firstName", p.firstName()); snapshot.put("lastName", p.lastName());
            snapshot.put("middleName", p.middleName()); snapshot.put("birthDate", p.birthDate().toString());
            snapshot.put("gender", "male".equals(p.gender()) ? "MALE" : "FEMALE");
            snapshot.put("city", p.city()); snapshot.put("countryCode", p.countryCode());
            snapshot.put("clubName", p.clubName()); snapshot.set("sportMeta", json.valueToTree(p.sportMeta()));
            var meta = json.createObjectNode();
            meta.put("demoFixture", true); meta.put("demoBatchId", batchId.toString());
            meta.put("demoPreparation", "CONFIRMED_FIXTURES_NOT_PARTICIPANT_WORKFLOW");
            if (entry.seedTimeSeconds() != null) meta.put("seedTimeSeconds", entry.seedTimeSeconds());
            var entity = new RegistrationEntity();
            entity.setId(UUID.randomUUID()); entity.setEventId(eventId); entity.setEventDisciplineId(disciplineId);
            entity.setParticipantUserId(p.userId()); entity.setParticipantProfileId(p.id());
            entity.setStatus("CONFIRMED"); entity.setParticipantSnapshot(snapshot.toString());
            entity.setRegistrationMeta(meta.toString());
            entity.setReviewNote("Демонстрационная фикстура: подготовлена организатором, не является реальной заявкой.");
            entity.setReviewedByUserId(actorId); entity.setReviewedAt(now);
            entity.setSubmittedAt(now); entity.setCreatedAt(now); entity.setUpdatedAt(now);
            created.add(entity);
        }
        registrations.saveAllAndFlush(created);
        return created.stream().map(RegistrationEntity::getId).toList();
    }
}
