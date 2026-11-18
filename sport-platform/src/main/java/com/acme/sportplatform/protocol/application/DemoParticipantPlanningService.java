package com.acme.sportplatform.protocol.application;

import java.time.LocalDate;
import java.time.DateTimeException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.acme.sportplatform.common.DemoDataException;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.DisciplineSettingsPolicy;
import com.acme.sportplatform.protocol.DemoParticipantPlanning;
import com.acme.sportplatform.protocol.RegistrationProtocolAccess.ConfirmedRegistrationView;
import com.acme.sportplatform.protocol.infrastructure.jpa.ProtocolRuleBlockRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class DemoParticipantPlanningService implements DemoParticipantPlanning {
    private static final LocalDate EARLIEST = LocalDate.of(1, 1, 1);
    private static final LocalDate PREFERRED = LocalDate.of(2000, 1, 1);
    private final ProtocolRuleBlockRepository blocks;
    private final ObjectMapper json;
    public DemoParticipantPlanningService(ProtocolRuleBlockRepository blocks, ObjectMapper json) {
        this.blocks = blocks; this.json = json;
    }
    private record Interval(LocalDate start, LocalDate end) {
        Interval intersect(Interval other) {
            LocalDate a = start.isAfter(other.start) ? start : other.start;
            LocalDate b = end.isBefore(other.end) ? end : other.end;
            return a.isAfter(b) ? null : new Interval(a, b);
        }
    }
    private record Category(String code, String group, ProtocolEligibilityPolicy policy) { }

    @Override @Transactional(readOnly = true)
    public List<Candidate> plan(UUID version, String raw, int count) {
        try { return prepare(version, raw, count); }
        catch (DemoDataException error) { throw error; }
        catch (BusinessException | DateTimeException | ArithmeticException error) {
            throw invalid("Невозможно подготовить демонабор: " + error.getMessage());
        }
    }
    private List<Candidate> prepare(UUID version, String raw, int count) {
        if (version == null || count < 2 || count > 64) throw invalid("Недопустимый размер демонабора.");
        var items = blocks.findByRuleSetVersionIdOrderByOrderIndexAsc(version);
        List<String> pipeline = List.of("ELIGIBILITY_CONFIRMED_ONLY", "SEED_BY_METRIC_ASC_NULLS_LAST",
                "GROUP_BY_CAPACITY", "ASSIGN_CENTER_OUT");
        if (items.size() != 4) throw invalid("Демонабор поддерживает стандартный конвейер из четырёх этапов.");
        for (int i = 0; i < 4; i++) {
            if (!pipeline.get(i).equals(items.get(i).getExecutorCode())) throw invalid("Неподдерживаемый конвейер правил.");
        }
        var global = ProtocolEligibilityPolicy.parse(items.get(0).getConfig());
        var grouping = items.get(2).getConfig();
        ProtocolCategoryPolicy.parse(grouping);
        ObjectNode settings = DisciplineSettingsPolicy.read(raw);
        DisciplineSettingsPolicy.validate(settings);
        String disciplineGender = DisciplineSettingsPolicy.gender(settings);
        var allowed = DisciplineSettingsPolicy.allowedCategoryCodes(settings);
        List<Category> categories = new ArrayList<>();
        if (grouping.has("categories")) {
            Set<String> known = new HashSet<>();
            grouping.get("categories").forEach(node -> known.add(node.path("code").asText()));
            if (!known.containsAll(allowed)) throw invalid("Дисциплина выбирает неизвестную категорию регламента.");
            for (JsonNode node : grouping.get("categories")) {
                String code = node.path("code").asText();
                if (allowed.isEmpty() || allowed.contains(code)) categories.add(new Category(code,
                        node.path("seedingGroupCode").asText(), ProtocolEligibilityPolicy.parse(node.get("eligibility"))));
            }
        } else {
            if (!allowed.isEmpty()) throw invalid("В регламенте нет выбранных дисциплиной категорий.");
            categories.add(new Category(null, null, ProtocolEligibilityPolicy.parse(json.createObjectNode())));
        }
        LocalDate today = LocalDate.now();
        List<List<Candidate>> buckets = new ArrayList<>();
        for (Category category : categories) {
            List<Candidate> choices = new ArrayList<>();
            boolean hasEligibleIntersection = false;
            for (String gender : List.of("MALE", "FEMALE")) {
                if (disciplineGender != null && !disciplineGender.equals(gender)) continue;
                Interval globalRange = range(global, gender, today);
                Interval categoryRange = range(category.policy(), gender, today);
                Interval overlap = globalRange == null || categoryRange == null ? null : globalRange.intersect(categoryRange);
                if (overlap == null) continue;
                hasEligibleIntersection = true;
                List<Interval> unique = List.of(overlap);
                for (Category other : categories) {
                    if (other == category) continue;
                    Interval excluded = range(other.policy(), gender, today);
                    if (excluded != null) unique = subtract(unique, excluded);
                }
                if (unique.isEmpty()) continue;
                LocalDate birth = choose(unique);
                choices.add(new Candidate(gender, birth, category.code(), category.group()));
            }
            if (choices.isEmpty() && hasEligibleIntersection) {
                throw invalid("Нельзя однозначно подобрать участника для категории " + category.code() + ": категории перекрываются.");
            }
            if (!choices.isEmpty()) buckets.add(List.copyOf(choices));
        }
        if (buckets.isEmpty()) throw invalid("Нет участников, совместимых с полом, возрастом и категориями дисциплины.");
        if (buckets.size() > count) throw invalid("Категорий больше, чем мест в демонаборе; полный показ категорий невозможен.");
        List<Candidate> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            var choices = buckets.get(i % buckets.size());
            result.add(choices.get((i / buckets.size()) % choices.size()));
        }
        // Verify generated snapshots with the production eligibility/category evaluators.
        var selected = grouping.deepCopy();
        if (grouping.has("categories")) {
            var array = json.createArrayNode();
            for (JsonNode node : grouping.get("categories")) {
                if (allowed.isEmpty() || allowed.contains(node.path("code").asText())) array.add(node.deepCopy());
            }
            ((ObjectNode) selected).set("categories", array);
        }
        List<ConfirmedRegistrationView> snapshots = new ArrayList<>();
        for (int i = 0; i < result.size(); i++) {
            var candidate = result.get(i);
            var snapshot = json.createObjectNode().put("gender", candidate.gender()).put("birthDate", candidate.birthDate().toString());
            snapshots.add(new ConfirmedRegistrationView(new UUID(0, i + 1), "{}", new UUID(1, i + 1), snapshot.toString()));
        }
        new ProtocolEligibilityVerifier(json).verify(snapshots, global);
        var membership = ProtocolCategoryPolicy.parse(selected).classify(snapshots, json);
        for (int i = 0; i < result.size(); i++) {
            var found = membership.get(snapshots.get(i).registrationId());
            if (result.get(i).categoryCode() != null && (found == null
                    || !result.get(i).categoryCode().equals(found.categoryCode())
                    || !result.get(i).seedingGroupCode().equals(found.seedingGroupCode()))) {
                throw invalid("Проверка категорий демонабора не пройдена.");
            }
        }
        return List.copyOf(result);
    }
    private Interval range(ProtocolEligibilityPolicy policy, String gender, LocalDate today) {
        if (!policy.allowedGenders().isEmpty() && !policy.allowedGenders().contains(gender)) return null;
        Interval domain = new Interval(EARLIEST, today);
        if (!policy.checksAge()) return domain;
        LocalDate reference = policy.referenceDate();
        long min = policy.minAge() == null ? 0L : policy.minAge();
        Long max = policy.maxAge() == null ? null : policy.maxAge().longValue();
        LocalDate first, last;
        if (policy.ageMethod() == ProtocolEligibilityPolicy.AgeMethod.BY_YEAR) {
            first = max == null ? EARLIEST : LocalDate.of(Math.toIntExact(reference.getYear() - max), 1, 1);
            last = LocalDate.of(Math.toIntExact(reference.getYear() - min), 12, 31);
        } else {
            first = max == null ? EARLIEST : reference.minusYears(max + 1L).plusDays(1);
            last = reference.minusYears(min);
        }
        if (last.isAfter(reference)) last = reference;
        if (first.isAfter(last)) return null;
        return domain.intersect(new Interval(first, last));
    }
    private List<Interval> subtract(List<Interval> ranges, Interval excluded) {
        List<Interval> result = new ArrayList<>();
        for (Interval source : ranges) {
            Interval cut = source.intersect(excluded);
            if (cut == null) { result.add(source); continue; }
            if (source.start().isBefore(cut.start())) result.add(new Interval(source.start(), cut.start().minusDays(1)));
            if (source.end().isAfter(cut.end())) result.add(new Interval(cut.end().plusDays(1), source.end()));
        }
        return result;
    }
    private LocalDate choose(List<Interval> intervals) {
        LocalDate best = null;
        long distance = Long.MAX_VALUE;
        for (Interval interval : intervals) {
            LocalDate date = PREFERRED.isBefore(interval.start()) ? interval.start()
                    : PREFERRED.isAfter(interval.end()) ? interval.end() : PREFERRED;
            long delta = Math.abs(ChronoUnit.DAYS.between(PREFERRED, date));
            if (delta < distance) { best = date; distance = delta; }
        }
        return best;
    }
    private DemoDataException invalid(String message) {
        return new DemoDataException(409, "demo.unsupported_configuration", message);
    }
}
