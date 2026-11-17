package com.acme.sportplatform.common.numeric;

import java.math.BigDecimal;
import com.fasterxml.jackson.databind.JsonNode;

/** Existing canonical seedTimeSeconds contract, shared by all application paths. */
public final class SeedTimeValue {
    private SeedTimeValue() { }

    public static boolean metadataIsValid(JsonNode metadata) {
        return metadata == null || metadata.isNull()
                || (metadata.isObject() && isValid(metadata.get("seedTimeSeconds")));
    }

    public static boolean isValid(JsonNode value) {
        if (value == null || value.isNull()) return true;
        if (!value.isNumber()) return false;
        try {
            BigDecimal seconds = value.decimalValue();
            return seconds.signum() > 0
                    && seconds.stripTrailingZeros().scale() <= 2
                    && (long) seconds.precision() - seconds.scale() <= 131072;
        } catch (IllegalArgumentException | ArithmeticException invalid) {
            return false;
        }
    }
}
