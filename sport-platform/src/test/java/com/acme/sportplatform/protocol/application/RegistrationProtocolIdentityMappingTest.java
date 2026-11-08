package com.acme.sportplatform.protocol.application;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.acme.sportplatform.protocol.RegistrationProtocolAccess.ConfirmedRegistrationView;
import com.acme.sportplatform.registrations.RegistrationLookup;
import com.acme.sportplatform.registrations.RegistrationLookupResult;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegistrationProtocolIdentityMappingTest {
    private final RegistrationLookup lookup = mock(RegistrationLookup.class);
    private final RegistrationProtocolAccessService service = new RegistrationProtocolAccessService(lookup);
    private final UUID disciplineId = UUID.randomUUID(), profileId = UUID.randomUUID(), userId = UUID.randomUUID();
    private RegistrationLookupResult registration(UUID id, UUID discipline, String status, UUID profile) {
        return new RegistrationLookupResult(id,userId,discipline,status,"{}","{\"seedTimeSeconds\":30}",profile);
    }
    @Test void passesProfileAndKeepsRegistrationIdentityAndMetadata() {
        UUID registrationId = UUID.randomUUID();
        when(lookup.findByEventDisciplineId(disciplineId)).thenReturn(List.of(registration(registrationId,disciplineId,"CONFIRMED",profileId)));
        var result = service.findConfirmedByEventDisciplineId(disciplineId);
        assertEquals(1,result.size()); var value = result.get(0);
        assertEquals(registrationId,value.registrationId()); assertEquals(profileId,value.requireParticipantProfileId());
        assertEquals("{\"seedTimeSeconds\":30}",value.registrationMeta());
        assertNotEquals(userId,value.participantProfileId()); assertNotEquals(registrationId,value.participantProfileId());
    }
    @Test void stillFiltersNonConfirmedStatuses() {
        var values = java.util.Arrays.asList(
                registration(UUID.randomUUID(),disciplineId,"CONFIRMED",profileId),
                registration(UUID.randomUUID(),disciplineId,"SUBMITTED",profileId),
                registration(UUID.randomUUID(),disciplineId,"REJECTED",profileId),
                registration(UUID.randomUUID(),disciplineId,null,profileId));
        when(lookup.findByEventDisciplineId(disciplineId)).thenReturn(values);
        assertEquals(1,service.findConfirmedByEventDisciplineId(disciplineId).size());
    }
    @Test void sameProfileSurvivesAcrossDifferentDisciplineRegistrations() {
        UUID otherDiscipline = UUID.randomUUID(), firstId = UUID.randomUUID(), secondId = UUID.randomUUID();
        when(lookup.findByEventDisciplineId(disciplineId)).thenReturn(List.of(registration(firstId,disciplineId,"CONFIRMED",profileId)));
        when(lookup.findByEventDisciplineId(otherDiscipline)).thenReturn(List.of(registration(secondId,otherDiscipline,"CONFIRMED",profileId)));
        var first = service.findConfirmedByEventDisciplineId(disciplineId).get(0);
        var second = service.findConfirmedByEventDisciplineId(otherDiscipline).get(0);
        assertNotEquals(first.registrationId(),second.registrationId());
        assertEquals(first.requireParticipantProfileId(),second.requireParticipantProfileId());
    }
    @Test void legacyViewIsUsableForSeedingButRejectedForScheduling() {
        UUID id = UUID.randomUUID(); var legacy = new ConfirmedRegistrationView(id,"{}");
        assertEquals(id,legacy.registrationId()); assertEquals("{}",legacy.registrationMeta());
        assertNull(legacy.participantProfileId());
        assertThrows(IllegalStateException.class,legacy::requireParticipantProfileId);
    }
    @Test void lookupWithoutProfileNeverFallsBackToUserOrRegistrationId() {
        UUID id = UUID.randomUUID();
        var legacy = new RegistrationLookupResult(id,userId,disciplineId,"CONFIRMED","{}","{}");
        when(lookup.findByEventDisciplineId(disciplineId)).thenReturn(List.of(legacy));
        var mapped = service.findConfirmedByEventDisciplineId(disciplineId).get(0);
        assertNull(mapped.participantProfileId()); assertThrows(IllegalStateException.class,mapped::requireParticipantProfileId);
    }
    @Test void emptyLookupReturnsEmptyList() {
        when(lookup.findByEventDisciplineId(disciplineId)).thenReturn(List.of());
        assertTrue(service.findConfirmedByEventDisciplineId(disciplineId).isEmpty());
    }
}
