// SWIM_LABELS_V1
package com.acme.sportplatform.demo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.acme.sportplatform.common.DemoDataException;
import com.acme.sportplatform.common.DemoDataMode;
import com.acme.sportplatform.competition.ProtocolDisciplineLookup;
import com.acme.sportplatform.competition.DemoDisciplineConstraints;
import com.acme.sportplatform.events.DemoEventAccess;
import com.acme.sportplatform.events.EventOperationAccess;
import com.acme.sportplatform.events.EventLookup;
import com.acme.sportplatform.events.EventRegulationLookup;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.identity.DemoParticipantProvisioning;
import com.acme.sportplatform.protocol.CompetitionUnitProtocolAccess;
import com.acme.sportplatform.protocol.ProtocolConfigurationPublication;
import com.acme.sportplatform.protocol.DemoParticipantPlanning;
import com.acme.sportplatform.registrations.DemoRegistrationAccess;
import com.acme.sportplatform.regulations.RegulationBundleAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotNull;

@Service
public class DemoDataService {
    public record Request(@NotNull UUID requestId, @NotNull UUID expectedRuleVersionId,
            @NotNull Integer expectedCapacity, @NotNull Integer expectedCount,
            @NotNull String datasetVersion, @NotNull Boolean confirmed) { }
    public record Row(int ordinal, String name, String gender, BigDecimal seedTimeSeconds, UUID registrationId,
            String birthDate, String categoryCode, String seedingGroupCode) {
        public Row(int ordinal, String name, String gender, BigDecimal seedTimeSeconds, UUID registrationId) {
            this(ordinal, name, gender, seedTimeSeconds, registrationId, null, null, null);
        }
    }
    public record Receipt(UUID requestId, UUID eventId, UUID disciplineId, UUID ruleVersionId,
            int capacity, int count, String datasetVersion, String createdAt, List<Row> participants) { }
    public record Plan(UUID eventId, UUID disciplineId, UUID ruleVersionId, int capacity, int count,
            String datasetVersion, String state, String message, Receipt receipt, List<Row> participants) { }
    private record Actor(UUID id, boolean admin) { }
    private final DemoDataMode mode;
    private final EventOperationAccess access;
    private final ProtocolDisciplineLookup disciplines;
    private final DemoDisciplineConstraints constraints;
    private final EventLookup events;
    private final DemoEventAccess eventLock;
    private final EventRegulationLookup eventRegulations;
    private final RegulationBundleAccess regulations;
    private final ProtocolConfigurationPublication configurations;
    private final CompetitionUnitProtocolAccess units;
    private final DemoRegistrationAccess registrations;
    private final DemoParticipantProvisioning participants;
    private final DemoBatchStore batches;
    private final ObjectMapper json;
    private final DemoParticipantPlanning planning;
    public DemoDataService(DemoDataMode mode, EventOperationAccess access, ProtocolDisciplineLookup disciplines,
            DemoDisciplineConstraints constraints, EventLookup events, DemoEventAccess eventLock,
            EventRegulationLookup eventRegulations, RegulationBundleAccess regulations,
            ProtocolConfigurationPublication configurations, CompetitionUnitProtocolAccess units,
            DemoRegistrationAccess registrations, DemoParticipantProvisioning participants,
            DemoBatchStore batches, ObjectMapper json, DemoParticipantPlanning planning) {
        this.mode=mode; this.access=access; this.disciplines=disciplines; this.constraints=constraints;
        this.events=events; this.eventLock=eventLock; this.eventRegulations=eventRegulations;
        this.regulations=regulations; this.configurations=configurations; this.units=units;
        this.registrations=registrations; this.participants=participants; this.batches=batches; this.json=json; this.planning=planning;
    }
    private Actor authorize(UUID eventId, UUID disciplineId) {
        mode.requireAllowed(eventId);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof AuthenticatedUserPrincipal p) || p.getUserId() == null)
            throw new AccessDeniedException("Authenticated platform principal required");
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_PLATFORM_ADMIN".equals(a.getAuthority()));
        boolean organizer = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ORGANIZER".equals(a.getAuthority()));
        if (!admin && (!organizer || !access.canManageEvent(eventId, p.getUserId())))
            throw new AccessDeniedException("Cannot manage this event");
        var d = disciplines.lock(disciplineId);
        if (!eventId.equals(d.eventId())) throw failure(404,"demo.discipline_not_found","Дисциплина не принадлежит событию.");
        if (!"INDIVIDUAL".equals(d.competitionFormat()) || !"HEAT".equals(d.unitType()))
            throw failure(409,"demo.unsupported_format","Демонабор рассчитан на INDIVIDUAL / HEAT.");
        return new Actor(p.getUserId(), admin);
    }
    @Transactional
    public Plan preview(UUID eventId, UUID disciplineId) {
        authorize(eventId, disciplineId);
        return plan(eventId, disciplineId);
    }
    private Plan plan(UUID eventId, UUID disciplineId) {
        var existing = batches.byDiscipline(disciplineId);
        if (existing.isPresent()) {
            var receipt = read(existing.get());
            return new Plan(eventId, disciplineId, receipt.ruleVersionId(), receipt.capacity(), receipt.count(),
                    receipt.datasetVersion(), "PREPARED", "Тестовый набор уже создан. Повторное наполнение запрещено.",
                    receipt, receipt.participants());
        }
        var binding = regulations.binding(eventRegulations.versionId(eventId));
        if (binding == null || binding.ruleVersionId() == null) throw failure(409,
                "demo.no_configuration", "За регламентом события не закреплены настройки заплывов.");
        var settings = configurations.read(binding.ruleVersionId());
        int count = DemoSample.count(settings);
        var limits = constraints.read(disciplineId);
        String blocker = null;
        if (!"TIME".equalsIgnoreCase(limits.resultType()) || !"ASC".equalsIgnoreCase(limits.rankingStrategy()))
            blocker = "Демонабор времени рассчитан на TIME / ASC.";
        else if (!"DRAFT".equals(events.getById(eventId).status()))
            blocker = "Наполнение разрешено только в черновике события, до публикации.";
        else if (units.hasCompetitionUnits(disciplineId)) blocker = "У дисциплины уже есть заплывы. Наполнение запрещено.";
        else if (registrations.count(disciplineId) != 0) blocker = "В дисциплине уже есть заявки. Наполнение запрещено.";
        else if (limits.participantLimit() != null && limits.participantLimit() < count)
            blocker = "Лимит участников меньше размера демонабора. Автоматически увеличивать лимит нельзя.";
        List<Row> rows = List.of();
        if (blocker == null) {
            try {
                var candidates = planning.plan(binding.ruleVersionId(), limits.settingsJson(), count);
                if (candidates.size() != count) throw new IllegalStateException("Incomplete demo plan");
                List<Row> prepared = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    var candidate = candidates.get(i);
                    prepared.add(new Row(i + 1, DemoSample.name(i + 1, candidate.gender()), candidate.gender(),
                            DemoSample.metric(i + 1, count), null, candidate.birthDate().toString(),
                            candidate.categoryCode(), candidate.seedingGroupCode()));
                }
                rows = List.copyOf(prepared);
            } catch (DemoDataException error) { blocker = error.getMessage(); }
        }
        String dataset = dataset(binding.ruleVersionId(), settings.capacity(), count, limits.settingsJson(), rows);
        return new Plan(eventId, disciplineId, binding.ruleVersionId(), settings.capacity(), count, dataset,
                blocker == null ? "AVAILABLE" : "BLOCKED",
                blocker == null ? "Будут созданы отдельные неактивные демоаккаунты и подтверждённые тестовые заявки. Без платежей и уведомлений." : blocker,
                null, rows);
    }
    @Transactional
    public Receipt create(UUID eventId, UUID disciplineId, Request request) {
        var actor = authorize(eventId, disciplineId);
        if (request == null || request.requestId() == null || !Boolean.TRUE.equals(request.confirmed())
                || request.expectedRuleVersionId() == null || request.expectedCapacity() == null
                || request.expectedCount() == null || request.datasetVersion() == null || request.datasetVersion().isBlank())
            throw failure(400,"demo.confirmation_required","Требуется подтверждение конкретного плана наполнения.");
        var previous = batches.byRequest(request.requestId());
        if (previous.isPresent()) {
            var old = previous.get();
            if (!old.eventId().equals(eventId) || !old.disciplineId().equals(disciplineId)
                    || !old.actorId().equals(actor.id()) || !old.ruleVersionId().equals(request.expectedRuleVersionId())
                    || old.capacity() != request.expectedCapacity() || old.count() != request.expectedCount())
                throw failure(409,"demo.request_conflict","Идентификатор запроса уже связан с другой операцией.");
            var receipt = read(old);
            if (!Objects.equals(receipt.datasetVersion(), request.datasetVersion()))
                throw failure(409,"demo.request_conflict","Идентификатор запроса связан с другой версией демонабора.");
            return receipt;
        }
        eventLock.lockDraft(eventId);
        var plan = plan(eventId, disciplineId);
        if (!"AVAILABLE".equals(plan.state())) throw failure(409,"demo.not_available",plan.message());
        if (!Objects.equals(plan.ruleVersionId(),request.expectedRuleVersionId())
                || plan.capacity() != request.expectedCapacity() || plan.count() != request.expectedCount()
                || !Objects.equals(plan.datasetVersion(), request.datasetVersion()))
            throw failure(409,"demo.plan_changed","Настройки изменились. Обновите предварительный план.");
        try {
            batches.reserve(request.requestId(),eventId,disciplineId,actor.id(),plan.ruleVersionId(),plan.capacity(),plan.count());
        } catch (DuplicateKeyException error) {
            throw failure(409,"demo.request_conflict","Набор уже создаётся или создан. Обновите состояние, не повторяйте операцию вслепую.");
        }
        List<DemoRegistrationAccess.Entry> entries = new ArrayList<>();
        for (Row row : plan.participants()) {
            var fixture = new DemoParticipantProvisioning.Fixture(row.gender(), LocalDate.parse(row.birthDate()));
            var profile = participants.create(eventId, request.requestId(), row.ordinal(), fixture);
            String expectedGender = "MALE".equals(row.gender()) ? "male" : "female";
            String actualName = profile == null ? null : profile.lastName() + " " + profile.firstName();
            if (profile == null || !expectedGender.equals(profile.gender())
                    || !fixture.birthDate().equals(profile.birthDate()) || !row.name().equals(actualName)) {
                throw new IllegalStateException("Provisioned demo profile differs from preview");
            }
            entries.add(new DemoRegistrationAccess.Entry(profile, row.seedTimeSeconds()));
        }
        var ids = registrations.createConfirmed(eventId,disciplineId,request.requestId(),actor.id(),entries);
        if (ids.size() != plan.count()) throw new IllegalStateException("Incomplete demo batch");
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            Row source = plan.participants().get(i);
            rows.add(new Row(source.ordinal(), source.name(), source.gender(), source.seedTimeSeconds(), ids.get(i),
                    source.birthDate(), source.categoryCode(), source.seedingGroupCode()));
        }
        var receipt = new Receipt(request.requestId(),eventId,disciplineId,plan.ruleVersionId(),plan.capacity(),plan.count(),
                plan.datasetVersion(),OffsetDateTime.now(ZoneOffset.UTC).toString(),List.copyOf(rows));
        try { batches.finish(request.requestId(),json.writeValueAsString(receipt)); }
        catch (com.fasterxml.jackson.core.JsonProcessingException error) { throw new IllegalStateException(error); }
        return receipt;
    }
    private String dataset(UUID version, int capacity, int count, String settings, List<Row> rows) {
        try {
            var context = json.createObjectNode();
            context.put("ruleVersionId", version.toString());
            context.put("capacity", capacity); context.put("count", count);
            context.put("disciplineSettings", settings);
            context.set("participants", json.valueToTree(rows));
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                    context.toString().getBytes(StandardCharsets.UTF_8));
            return DemoSample.VERSION + ":" + HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    private Receipt read(DemoBatchStore.Stored stored) {
        try { return json.readValue(stored.response(),Receipt.class); }
        catch (Exception error) { throw new IllegalStateException("Invalid demo batch receipt",error); }
    }
    private DemoDataException failure(int status,String code,String text) { return new DemoDataException(status,code,text); }
}
