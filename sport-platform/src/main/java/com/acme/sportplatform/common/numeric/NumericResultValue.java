package com.acme.sportplatform.common.numeric;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Pattern;

/** Exact input parsing. Invalid inputs return null, like the SQL counterpart. */
public final class NumericResultValue {
    private static final Pattern NUMBER = Pattern.compile(
            "^[+-]?([0-9]+([.][0-9]*)?|[.][0-9]+)([eE][+-]?[0-9]+)?$");
    private static final Pattern MINUTES = Pattern.compile("^[0-9]+$");
    private NumericResultValue() { }

    public static BigDecimal parse(String raw, String resultType) {
        if (raw == null || resultType == null || raw.length() > 50) return null;
        String value = asciiTrim(raw);
        if (value.isEmpty()) return null;
        boolean time = "TIME".equals(asciiTrim(resultType).toUpperCase(Locale.ROOT));
        try {
            BigDecimal parsed;
            if (time && value.indexOf(':') >= 0) {
                String[] parts = value.split(":", -1);
                if (parts.length != 2 || !MINUTES.matcher(parts[0]).matches()) return null;
                BigDecimal seconds = number(parts[1]);
                if (seconds == null || seconds.signum() < 0
                        || seconds.compareTo(BigDecimal.valueOf(60)) >= 0) return null;
                parsed = new BigDecimal(parts[0]).multiply(BigDecimal.valueOf(60)).add(seconds);
            } else {
                parsed = number(value);
            }
            if (parsed == null || (time && parsed.signum() <= 0)) return null;
            return parsed;
        } catch (IllegalArgumentException | ArithmeticException invalid) {
            return null;
        }
    }

    private static BigDecimal number(String value) {
        if (!NUMBER.matcher(value).matches()) return null;
        BigDecimal number = new BigDecimal(value);
        long integerDigits = (long) number.precision() - number.scale();
        if (integerDigits > 131072 || number.scale() > 16383) return null;
        return number;
    }

    private static String asciiTrim(String value) {
        int start = 0, end = value.length();
        while (start < end && value.charAt(start) == ' ') start++;
        while (end > start && value.charAt(end - 1) == ' ') end--;
        return value.substring(start, end);
    }
}
