package com.acme.sportplatform.demo;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.acme.sportplatform.common.DemoDataException;
import com.acme.sportplatform.protocol.ProtocolConfigurationPublication.Settings;
import com.fasterxml.jackson.databind.JsonNode;

public final class DemoSample {
    public static final String VERSION = "SWIM_MIXED_V1";
    private DemoSample() { }
    public static int count(Settings settings) {
        if (settings == null || settings.capacity() == null || settings.capacity() < 1 || settings.capacity() > 32)
            throw invalid("Демонабор поддерживает вместимость от 1 до 32.");
        if (!"seedTimeSeconds".equals(settings.metricKey()))
            throw invalid("Для этого демонабора поле метрики должно быть seedTimeSeconds.");
        List<JsonNode> categories = settings.categories();
        if (categories != null && !categories.isEmpty()) {
            if (categories.size() != 2) throw invalid("Выберите режим без категорий или стандартный режим мужчин/женщин.");
            Set<String> codes = new HashSet<>();
            String groupMode = null;
            for (JsonNode category : categories) {
                if (category == null || !category.isObject()) throw invalid("Некорректная категория.");
                String code = category.path("code").asText();
                String expected = switch (code) { case "MEN" -> "MALE"; case "WOMEN" -> "FEMALE"; default -> ""; };
                if (expected.isEmpty() || !codes.add(code)) throw invalid("Демонабор рассчитан на категории MEN и WOMEN.");
                JsonNode eligibility = category.path("eligibility");
                JsonNode genders = eligibility.path("allowedGenders");
                if (!eligibility.isObject() || eligibility.size() != 1 || !genders.isArray()
                        || genders.size() != 1 || !expected.equals(genders.get(0).asText()))
                    throw invalid("Пользовательские возрастные и иные условия требуют отдельного демонабора; автоматическая подмена запрещена.");
                String group = category.path("seedingGroupCode").asText();
                String mode = "OPEN".equals(group) ? "MIXED" : code.equals(group) ? "SEPARATE" : "";
                if (mode.isEmpty() || (groupMode != null && !groupMode.equals(mode)))
                    throw invalid("Демонабор поддерживает общую группу OPEN либо раздельные MEN/WOMEN.");
                groupMode = mode;
            }
        }
        return settings.capacity() * 2;
    }
    public static BigDecimal metric(int ordinal, int count) {
        if (count < 2 || count > 64 || ordinal < 1 || ordinal > count) throw new IllegalArgumentException("Invalid sample index");
        return ordinal == count ? null : new BigDecimal("60.00")
                .add(new BigDecimal("1.25").multiply(BigDecimal.valueOf(count - ordinal)));
    }
    public static String gender(int ordinal) { return ordinal % 2 == 1 ? "MALE" : "FEMALE"; }
    public static String name(int ordinal) {
        return String.format("Тестовый-%03d ", ordinal) + (ordinal % 2 == 1 ? "Демо-участник" : "Демо-участница");
    }
    private static DemoDataException invalid(String text) { return new DemoDataException(409, "demo.unsupported_configuration", text); }
}
