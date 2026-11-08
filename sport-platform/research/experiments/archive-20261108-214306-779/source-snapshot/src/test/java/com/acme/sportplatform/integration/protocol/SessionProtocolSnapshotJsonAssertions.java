package com.acme.sportplatform.integration.protocol;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;
import com.fasterxml.jackson.databind.*;
import static org.junit.jupiter.api.Assertions.fail;

/** Exact value comparison; no epsilon, string coercion, array sorting or ignored fields. */
final class SessionProtocolSnapshotJsonAssertions {
    private SessionProtocolSnapshotJsonAssertions() { }
    static JsonNode parse(ObjectMapper mapper,String raw) throws IOException {
        return mapper.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .with(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS).readTree(raw);
    }
    static void assertSnapshotMatches(ObjectMapper mapper,Object expected,JsonNode actual) throws IOException {
        assertEquivalent(parse(mapper,mapper.writeValueAsString(expected)),actual);
    }
    static void assertEquivalent(JsonNode expected,JsonNode actual) { compare(expected,actual,""); }
    private static void compare(JsonNode expected,JsonNode actual,String path) {
        if(expected==null || actual==null) {
            if(expected!=actual) mismatch(path,expected,actual);
            return;
        }
        if(expected.isNumber() && actual.isNumber()) {
            if(expected.decimalValue().compareTo(actual.decimalValue())!=0) mismatch(path,expected,actual);
            return;
        }
        if(expected.getNodeType()!=actual.getNodeType()) { mismatch(path,expected,actual);return; }
        if(expected.isObject()) {
            Set<String> expectedKeys=keys(expected),actualKeys=keys(actual);
            if(!expectedKeys.equals(actualKeys)) {
                Set<String> missing=new TreeSet<>(expectedKeys);missing.removeAll(actualKeys);
                Set<String> extra=new TreeSet<>(actualKeys);extra.removeAll(expectedKeys);
                fail("JSON field mismatch at "+shown(path)+": missing="+missing+", extra="+extra);
            }
            for(String key:expectedKeys) compare(expected.get(key),actual.get(key),path+"/"+key.replace("~","~0").replace("/","~1"));
            return;
        }
        if(expected.isArray()) {
            if(expected.size()!=actual.size()) fail("JSON array size mismatch at "+shown(path)+": expected="+expected.size()+", actual="+actual.size());
            for(int i=0;i<expected.size();i++) compare(expected.get(i),actual.get(i),path+"/"+i);
            return;
        }
        if(!expected.equals(actual)) mismatch(path,expected,actual);
    }
    private static Set<String> keys(JsonNode value) {
        Set<String> keys=new TreeSet<>();value.fieldNames().forEachRemaining(keys::add);return keys;
    }
    private static String shown(String path) { return path.isEmpty()?"<root>":path; }
    private static void mismatch(String path,JsonNode expected,JsonNode actual) {
        fail("JSON value mismatch at "+shown(path)+"\nexpected="+expected+"\nactual="+actual);
    }
}
