package com.acme.sportplatform.competition;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.acme.sportplatform.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Reserved discipline metadata; no age calculation or registration-module dependency. */
public final class DisciplineSettingsPolicy {
    private static final ObjectMapper JSON = new ObjectMapper();
    private DisciplineSettingsPolicy() {}
    public static ObjectNode read(String raw) {
        if (raw == null || raw.isBlank()) return JSON.createObjectNode();
        try {
            JsonNode value = JSON.readTree(raw);
            if (value == null || !value.isObject()) throw invalid("Discipline settings must be a JSON object");
            return (ObjectNode) value;
        } catch (BusinessException ex) { throw ex; }
        catch (Exception ex) { throw invalid("Discipline settings contain invalid JSON"); }
    }
    public static ObjectNode mergeAndValidate(String defaults, JsonNode overrides) {
        ObjectNode result = read(defaults).deepCopy();
        if (overrides != null && !overrides.isNull()) {
            if (!overrides.isObject()) throw invalid("Discipline settings override must be an object");
            overrides.fields().forEachRemaining(field -> result.set(field.getKey(), field.getValue().deepCopy()));
        }
        validate(result);
        return result;
    }
    public static void validate(JsonNode settings) {
        if (settings == null || !settings.isObject()) throw invalid("Discipline settings must be an object");
        for (String key : List.of("distanceMeters", "poolLengthMeters")) {
            JsonNode value = settings.get(key);
            if (value != null && !value.isNull() && (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() <= 0)) {
                throw invalid(key + " must be a positive integer");
            }
        }
        JsonNode stroke = settings.get("stroke");
        if (stroke != null && !stroke.isNull() && (!stroke.isTextual() || stroke.textValue().isBlank() || stroke.textValue().length() > 64)) {
            throw invalid("stroke must be a nonblank code of at most 64 characters");
        }
        gender(settings);
        allowedCategoryCodes(settings);
    }
    public static String gender(JsonNode settings) {
        JsonNode value = settings.get("gender");
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw invalid("gender must be a text code");
        String code = value.textValue().toUpperCase(Locale.ROOT);
        if (Set.of("OPEN", "MIXED", "ANY").contains(code)) return null;
        if (!Set.of("MALE", "FEMALE").contains(code)) throw invalid("gender must be MALE, FEMALE, OPEN, MIXED or ANY");
        return code;
    }
    public static List<String> allowedCategoryCodes(JsonNode settings) {
        JsonNode value = settings.get("allowedCategoryCodes");
        if (value == null || value.isNull()) return List.of();
        if (!value.isArray() || value.isEmpty() || value.size() > 256) throw invalid("allowedCategoryCodes must contain 1..256 category codes; omit for all regulation categories");
        Set<String> result = new LinkedHashSet<>();
        for (JsonNode item : value) {
            if (!item.isTextual() || !item.textValue().matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,79}") || !result.add(item.textValue())) {
                throw invalid("allowedCategoryCodes must contain unique valid category codes");
            }
        }
        return List.copyOf(result);
    }
    private static BusinessException invalid(String message) { return new BusinessException("competition.invalid_discipline_settings", message); }
}
