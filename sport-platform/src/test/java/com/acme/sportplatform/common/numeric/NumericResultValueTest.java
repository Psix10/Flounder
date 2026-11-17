package com.acme.sportplatform.common.numeric;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class NumericResultValueTest {
    @Test void clockAndSecondsHaveExactlyEqualValues() {
        assertEquals(0, NumericResultValue.parse("01:08.54", "TIME").compareTo(
                NumericResultValue.parse("68.540", "TIME")));
    }
    @Test void nonPositiveTimesAreRejected() {
        for (String value : List.of("0", "-0.00", "-12.5", "00:00.00"))
            assertNull(NumericResultValue.parse(value, "TIME"), value);
    }
    @Test void positiveTimeFormatsMatchExistingExactParser() {
        for (String value : List.of("1", "+1", ".5", "1.", "1e1", "00:00.01", "1:+2"))
            assertNotNull(NumericResultValue.parse(value, "TIME"), value);
    }
    @Test void malformedAndNonFiniteValuesAreRejected() {
        for (String value : List.of("", "NaN", "Infinity", "1,25", "0x1.0p0", "1D", "bad"))
            assertNull(NumericResultValue.parse(value, "TIME"), value);
    }
    @Test void invalidClocksAreRejected() {
        for (String value : List.of("1:60", "1:-1", "1:2:3", "1:", ":1", "1.5:20"))
            assertNull(NumericResultValue.parse(value, "TIME"), value);
    }
    @Test void pointsRetainSignedAndZeroValues() {
        assertEquals(0, new BigDecimal("-2.50").compareTo(NumericResultValue.parse("-2.50", "POINTS")));
        assertEquals(0, BigDecimal.ZERO.compareTo(NumericResultValue.parse("0", "POINTS")));
    }
    @Test void clocksAreNotPointValues() {
        assertNull(NumericResultValue.parse("01:08.54", "POINTS"));
    }
    @Test void precisionDoesNotCollapseCloseDecimalValues() {
        assertTrue(NumericResultValue.parse("1.00000000000000000001", "TIME").compareTo(
                NumericResultValue.parse("1.00000000000000000002", "TIME")) < 0);
    }
    @Test void nullLongAndOutOfRangeValuesAreRejected() {
        assertNull(NumericResultValue.parse(null, "TIME"));
        assertNull(NumericResultValue.parse("1", null));
        assertNull(NumericResultValue.parse("9".repeat(51), "TIME"));
        assertNull(NumericResultValue.parse("1e999999999", "TIME"));
    }
    @Test void surroundingAsciiSpacesMatchSqlTrimming() {
        assertEquals(0, new BigDecimal("12").compareTo(NumericResultValue.parse(" 12 ", "TIME")));
    }
}
