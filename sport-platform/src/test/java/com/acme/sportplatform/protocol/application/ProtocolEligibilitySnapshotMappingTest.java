package com.acme.sportplatform.protocol.application;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.acme.sportplatform.registrations.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ProtocolEligibilitySnapshotMappingTest {
    @Test void copiesCapturedSnapshotWithoutChangingOtherIdentifiers() {
        UUID discipline=UUID.randomUUID(),registration=UUID.randomUUID(),profile=UUID.randomUUID();
        String snapshot="{\"birthDate\":\"2004-05-10\",\"gender\":\"MALE\"}";
        var lookup=mock(RegistrationLookup.class);
        when(lookup.findByEventDisciplineId(discipline)).thenReturn(List.of(new RegistrationLookupResult(
                registration,UUID.randomUUID(),discipline,"CONFIRMED",snapshot,"{}",profile)));
        var actual=new RegistrationProtocolAccessService(lookup).findConfirmedByEventDisciplineId(discipline).get(0);
        assertEquals(snapshot,actual.participantSnapshot());assertEquals(registration,actual.registrationId());
        assertEquals(profile,actual.participantProfileId());assertEquals("{}",actual.registrationMeta());
    }
}
