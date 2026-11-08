package com.acme.sportplatform.protocol.application;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.protocol.RegistrationProtocolAccess.ConfirmedRegistrationView;

/** Categories are evaluated on captured snapshots, never on current profiles. */
public final class ProtocolCategoryPolicy {
    public record Category(String code, String seedingGroupCode, ProtocolEligibilityPolicy eligibility) { }
    public record Membership(String categoryCode, String seedingGroupCode) { }
    private final List<Category> categories;
    private ProtocolCategoryPolicy(List<Category> categories) { this.categories = List.copyOf(categories); }
    public boolean enabled() { return !categories.isEmpty(); }
    public List<String> groupOrder() {
        return categories.stream().map(Category::seedingGroupCode).distinct().toList();
    }
    public static ProtocolCategoryPolicy parse(JsonNode groupingConfig) {
        if (groupingConfig == null || !groupingConfig.isObject()) throw invalid("Grouping config must be an object");
        if (!groupingConfig.has("categories")) return new ProtocolCategoryPolicy(List.of());
        JsonNode array = groupingConfig.get("categories");
        if (!array.isArray() || array.isEmpty() || array.size() > 256)
            throw invalid("categories must contain 1..256 definitions; omit it for legacy seeding");
        List<Category> result = new ArrayList<>(); Set<String> codes = new HashSet<>();
        for (JsonNode node : array) {
            if (!node.isObject()) throw invalid("Each category must be an object");
            node.fieldNames().forEachRemaining(k -> {
                if (!Set.of("code", "seedingGroupCode", "eligibility").contains(k)) throw invalid("Unknown category parameter: " + k);
            });
            String code = code(node, "code"), group = code(node, "seedingGroupCode");
            if (!codes.add(code)) throw invalid("Duplicate category code: " + code);
            result.add(new Category(code, group, ProtocolEligibilityPolicy.parse(node.get("eligibility"))));
        }
        return new ProtocolCategoryPolicy(result);
    }
    public Map<UUID, Membership> classify(List<ConfirmedRegistrationView> registrations, ObjectMapper json) {
        if (!enabled()) return Map.of();
        var verifier = new ProtocolEligibilityVerifier(json);
        Map<UUID, Membership> result = new LinkedHashMap<>();
        List<ProtocolCategoryException.Violation> failures = new ArrayList<>();
        for (var registration : registrations) {
            List<Category> matches = new ArrayList<>(); List<String> diagnostics = new ArrayList<>();
            for (Category category : categories) {
                try {
                    verifier.verify(List.of(registration), category.eligibility());
                    matches.add(category);
                } catch (ProtocolEligibilityException ex) {
                    diagnostics.add(category.code() + ":" + ex.violations().stream()
                            .map(v -> v.reason() + "@" + v.field()).toList());
                }
            }
            if (matches.size() != 1) {
                failures.add(new ProtocolCategoryException.Violation(registration.registrationId(),
                        matches.isEmpty() ? "NO_MATCHING_CATEGORY" : "AMBIGUOUS_CATEGORY",
                        matches.isEmpty() ? "No category matched; " + diagnostics
                                : "Matching categories: " + matches.stream().map(Category::code).toList()));
            } else {
                var selected = matches.get(0);
                result.put(registration.registrationId(), new Membership(selected.code(), selected.seedingGroupCode()));
            }
        }
        if (!failures.isEmpty()) throw new ProtocolCategoryException(failures);
        return Collections.unmodifiableMap(result);
    }
    private static String code(JsonNode node, String key) {
        JsonNode value = node.path(key);
        if (!value.isTextual() || !value.textValue().matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,79}"))
            throw invalid(key + " must be an ASCII identifier of 1..80 characters");
        return value.textValue();
    }
    private static BusinessException invalid(String message) {
        return new BusinessException("protocol.invalid_rule_config", message);
    }
}
