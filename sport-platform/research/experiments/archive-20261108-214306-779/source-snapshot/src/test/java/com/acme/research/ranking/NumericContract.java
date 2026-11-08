package com.acme.research.ranking;

import java.util.regex.Pattern;

final class NumericContract {
    private static final Pattern NUMBER = Pattern.compile("[+-]?[0-9]+(?:[.][0-9]+)?");
    private static final Pattern TIME = Pattern.compile("[0-9]+(?:[.][0-9]+)?:[0-9]+(?:[.][0-9]+)?");
    private NumericContract() {}
    static Double parse(String value) {
        if (value == null || value.length() > 50) return null;
        if (NUMBER.matcher(value).matches()) {
            double number = Double.parseDouble(value);
            return Double.isFinite(number) ? (number == 0 ? 0d : number) : null;
        }
        if (!TIME.matcher(value).matches()) return null;
        String[] parts = value.split(":", -1);
        double minutes = Double.parseDouble(parts[0]);
        double seconds = Double.parseDouble(parts[1]);
        double number = minutes * 60 + seconds;
        return seconds < 60 && Double.isFinite(number) ? number : null;
    }
}
