package com.acme.sportplatform.demo;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.acme.sportplatform.common.DemoDataException;
import com.acme.sportplatform.protocol.ProtocolConfigurationPublication.Settings;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

class DemoSampleTest {
    private final ObjectMapper json = new ObjectMapper();
    private Settings settings(int capacity,List<JsonNode> categories) {
        return new Settings(capacity,List.of(2,3,1,4),"seedTimeSeconds","SLOWEST_FIRST","FIRST",categories);
    }
    private List<JsonNode> categories(boolean mixed) throws Exception {
        return List.of(json.readTree("{\"code\":\"MEN\",\"seedingGroupCode\":\"" + (mixed?"OPEN":"MEN") + "\",\"eligibility\":{\"allowedGenders\":[\"MALE\"]}}"),
                json.readTree("{\"code\":\"WOMEN\",\"seedingGroupCode\":\"" + (mixed?"OPEN":"WOMEN") + "\",\"eligibility\":{\"allowedGenders\":[\"FEMALE\"]}}"));
    }
    @Test void doublesCapacity() { assertEquals(8,DemoSample.count(settings(4,List.of()))); }
    @Test void supportsMixedPreset() throws Exception { assertEquals(8,DemoSample.count(settings(4,categories(true)))); }
    @Test void supportsSeparatePreset() throws Exception { assertEquals(8,DemoSample.count(settings(4,categories(false)))); }
    @Test void rejectsCapacityOutsideBoundaries() {
        assertThrows(DemoDataException.class,()->DemoSample.count(settings(0,List.of())));
        assertThrows(DemoDataException.class,()->DemoSample.count(settings(33,List.of())));
    }
    @Test void maximumProduces64() { assertEquals(64,DemoSample.count(settings(32,List.of()))); }
    @Test void rejectsAnotherMetric() {
        var settings = new Settings(4,List.of(1,2,3,4),"points","FASTEST_FIRST","LAST");
        assertThrows(DemoDataException.class,()->DemoSample.count(settings));
    }
    @Test void sizeCalculationLeavesCategoryValidationToThePlanner() throws Exception {
        var items = categories(true);
        ((com.fasterxml.jackson.databind.node.ObjectNode)items.get(0).path("eligibility")).put("minAge",30);
        assertEquals(8,DemoSample.count(settings(4,items)));
    }
    @Test void exactlyOneMissingMetric() {
        assertEquals(1,IntStream.rangeClosed(1,8).filter(i->DemoSample.metric(i,8)==null).count());
        assertNull(DemoSample.metric(8,8));
    }
    @Test void finiteMetricsAreDistinctPositiveAndHaveTwoDecimals() {
        var metrics=IntStream.rangeClosed(1,7).mapToObj(i->DemoSample.metric(i,8)).toList();
        assertEquals(7,metrics.stream().distinct().count());
        assertTrue(metrics.stream().allMatch(v->v.signum()>0 && v.scale()==2));
        assertEquals(new BigDecimal("68.75"),metrics.get(0));
        assertEquals(new BigDecimal("61.25"),metrics.get(6));
    }
    @Test void alternatesGendersWithoutChangingProfiles() {
        assertEquals("MALE",DemoSample.gender(1)); assertEquals("FEMALE",DemoSample.gender(2));
    }
}
