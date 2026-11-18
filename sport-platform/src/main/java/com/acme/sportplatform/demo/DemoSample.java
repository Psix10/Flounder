package com.acme.sportplatform.demo;

import java.math.BigDecimal;
import com.acme.sportplatform.common.DemoDataException;
import com.acme.sportplatform.protocol.ProtocolConfigurationPublication.Settings;

public final class DemoSample {
    public static final String VERSION = "SWIM_ELIGIBLE_V2";
    private DemoSample() { }
    public static int count(Settings settings) {
        if (settings == null || settings.capacity() == null || settings.capacity() < 1 || settings.capacity() > 32)
            throw invalid("Демонабор поддерживает вместимость от 1 до 32.");
        if (!"seedTimeSeconds".equals(settings.metricKey()))
            throw invalid("Для этого демонабора поле метрики должно быть seedTimeSeconds.");
        return settings.capacity() * 2;
    }
    public static BigDecimal metric(int ordinal, int count) {
        if (count < 2 || count > 64 || ordinal < 1 || ordinal > count) throw new IllegalArgumentException("Invalid sample index");
        return ordinal == count ? null : new BigDecimal("60.00")
                .add(new BigDecimal("1.25").multiply(BigDecimal.valueOf(count - ordinal)));
    }
    public static String gender(int ordinal) { return ordinal % 2 == 1 ? "MALE" : "FEMALE"; }
    public static String name(int ordinal) { return name(ordinal, gender(ordinal)); }
    public static String name(int ordinal, String gender) {
        if (!"MALE".equals(gender) && !"FEMALE".equals(gender)) throw new IllegalArgumentException("Invalid demo gender");
        return String.format("Тестовый-%03d ", ordinal) + ("MALE".equals(gender) ? "Демо-участник" : "Демо-участница");
    }
    private static DemoDataException invalid(String text) { return new DemoDataException(409, "demo.unsupported_configuration", text); }
}
