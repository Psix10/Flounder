package com.acme.sportplatform.integration.protocol;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.acme.sportplatform.integration.protocol.SessionProtocolSnapshotJsonAssertions.*;

class SessionProtocolSnapshotJsonAssertionsTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private JsonNode node(String value) throws Exception { return parse(mapper,value); }
    @Test void integerAndDecimalRepresentationsHaveEqualNumericValue() throws Exception {
        assertEquivalent(node("{\"a\":32.0,\"b\":31.00,\"c\":99.990}"),node("{\"c\":99.99,\"b\":31,\"a\":32}"));
    }
    @Test void changedNumericValueIsRejected() throws Exception {
        var expected=node("32.0");var actual=node("32.01");
        assertThrows(AssertionError.class,()->assertEquivalent(expected,actual));
    }
    @Test void numericStringIsNotConvertedToNumber() throws Exception {
        var expected=node("32");var actual=node("\"32\"");
        assertThrows(AssertionError.class,()->assertEquivalent(expected,actual));
    }
    @Test void labelsRemainStrictIncludingWhitespace() throws Exception {
        var expected=node("{\"label\":\"Heat 3\"}");var actual=node("{\"label\":\"Heat3\"}");
        assertThrows(AssertionError.class,()->assertEquivalent(expected,actual));
    }
    @Test void objectFieldOrderIsIgnored() throws Exception {
        assertEquivalent(node("{\"a\":1,\"b\":true}"),node("{\"b\":true,\"a\":1}"));
    }
    @Test void arrayOrderRemainsSignificant() throws Exception {
        var expected=node("[1,2]");var actual=node("[2,1]");
        assertThrows(AssertionError.class,()->assertEquivalent(expected,actual));
    }
    @Test void missingFieldIsNotEquivalentToNull() throws Exception {
        var expected=node("{\"metric\":null}");var actual=node("{}");
        assertThrows(AssertionError.class,()->assertEquivalent(expected,actual));
    }
    @Test void decimalParsingDoesNotRoundThroughDouble() throws Exception {
        var expected=node("0.12345678901234567891");var actual=node("0.12345678901234567892");
        assertTrue(expected.isBigDecimal());assertThrows(AssertionError.class,()->assertEquivalent(expected,actual));
        var large=node("9007199254740993");var other=node("9007199254740992");
        assertThrows(AssertionError.class,()->assertEquivalent(large,other));
    }
    @Test void failureReportsTheExactNestedPath() throws Exception {
        var expected=node("{\"disciplines\":[{\"units\":[{\"label\":\"Heat 3\"}]}]}");
        var actual=node("{\"disciplines\":[{\"units\":[{\"label\":\"Heat3\"}]}]}");
        var error=assertThrows(AssertionError.class,()->assertEquivalent(expected,actual));
        assertTrue(error.getMessage().contains("/disciplines/0/units/0/label"));
    }
}
