package com.acme.sportplatform.common.numeric;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SeedTimeValueTest {
    private final ObjectMapper json = new ObjectMapper();
    @Test void absentAndNullMeanNt() throws Exception {
        assertTrue(SeedTimeValue.metadataIsValid(null));
        assertTrue(SeedTimeValue.metadataIsValid(json.nullNode()));
        assertTrue(SeedTimeValue.metadataIsValid(json.readTree("{}")));
        assertTrue(SeedTimeValue.metadataIsValid(json.readTree("{\"seedTimeSeconds\":null}")));
    }
    @Test void positiveHundredthsAreAccepted() throws Exception {
        assertTrue(SeedTimeValue.isValid(json.readTree("28.11")));
        assertTrue(SeedTimeValue.isValid(json.readTree("28.1100")));
        assertTrue(SeedTimeValue.isValid(json.readTree("1")));
    }
    @Test void nonPositiveValuesAreRejected() throws Exception {
        for (String value : new String[]{"0", "-0.00", "-1"})
            assertFalse(SeedTimeValue.isValid(json.readTree(value)));
    }
    @Test void stringsAndBooleansAreNotSeedTimes() throws Exception {
        assertFalse(SeedTimeValue.isValid(json.getNodeFactory().textNode("28.11")));
        assertFalse(SeedTimeValue.isValid(json.readTree("true")));
    }
    @Test void precisionBeyondHundredthsIsRejected() throws Exception {
        assertFalse(SeedTimeValue.isValid(json.readTree("28.111")));
    }
    @Test void metadataMustBeObjectWhenPresent() throws Exception {
        assertFalse(SeedTimeValue.metadataIsValid(json.readTree("[]")));
        assertFalse(SeedTimeValue.metadataIsValid(json.readTree("42")));
    }
    @Test void nonFiniteProgrammaticNumbersReturnFalseRatherThanThrow() {
        assertFalse(SeedTimeValue.isValid(json.getNodeFactory().numberNode(Double.NaN)));
        assertFalse(SeedTimeValue.isValid(json.getNodeFactory().numberNode(Double.POSITIVE_INFINITY)));
    }
    @Test void checkingMetadataDoesNotMutateIt() throws Exception {
        var metadata = json.createObjectNode(); metadata.put("seedTimeSeconds", 28.11); metadata.put("keep", "yes");
        var before = metadata.deepCopy();
        assertTrue(SeedTimeValue.metadataIsValid(metadata));
        assertEquals(before, metadata);
    }
}
