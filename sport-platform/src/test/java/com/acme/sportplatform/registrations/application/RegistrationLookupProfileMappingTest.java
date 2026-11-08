package com.acme.sportplatform.registrations.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.acme.sportplatform.registrations.RegistrationLookupResult;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationEntity;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegistrationLookupProfileMappingTest {
    private final RegistrationRepository repository = mock(RegistrationRepository.class);
    private final RegistrationLookupService service = new RegistrationLookupService(repository);
    private final UUID registrationId = UUID.randomUUID(), userId = UUID.randomUUID(),
            profileId = UUID.randomUUID(), disciplineId = UUID.randomUUID();
    private RegistrationEntity entity() {
        var e = new RegistrationEntity();
        e.setId(registrationId); e.setParticipantUserId(userId); e.setParticipantProfileId(profileId);
        e.setEventDisciplineId(disciplineId); e.setStatus("CONFIRMED");
        e.setParticipantSnapshot("{\"firstName\":\"Test\"}");
        e.setRegistrationMeta("{\"seedTimeSeconds\":30}");
        return e;
    }
    private void assertMapped(RegistrationLookupResult value) {
        assertEquals(registrationId, value.id()); assertEquals(userId, value.participantUserId());
        assertEquals(profileId, value.participantProfileId()); assertEquals(disciplineId, value.eventDisciplineId());
        assertEquals("CONFIRMED", value.status());
        assertEquals("{\"firstName\":\"Test\"}", value.participantSnapshot());
        assertEquals("{\"seedTimeSeconds\":30}", value.registrationMeta());
        assertNotEquals(value.id(), value.participantProfileId());
        assertNotEquals(value.participantUserId(), value.participantProfileId());
    }
    @Test void mapsProfileOnFindById() {
        when(repository.findById(registrationId)).thenReturn(Optional.of(entity()));
        assertMapped(service.findById(registrationId).orElseThrow());
    }
    @Test void mapsProfileOnGetById() {
        when(repository.findById(registrationId)).thenReturn(Optional.of(entity()));
        assertMapped(service.getById(registrationId));
    }
    @Test void mapsProfileOnBatchLookup() {
        when(repository.findByIdIn(List.of(registrationId))).thenReturn(List.of(entity()));
        var result = service.findByIdIn(List.of(registrationId));
        assertEquals(1,result.size()); assertMapped(result.get(0));
    }
    @Test void mapsProfileOnDisciplineLookup() {
        when(repository.findByEventDisciplineId(disciplineId)).thenReturn(List.of(entity()));
        var result = service.findByEventDisciplineId(disciplineId);
        assertEquals(1,result.size()); assertMapped(result.get(0));
    }
    @Test void emptyAndNullBatchDoNotQueryRepository() {
        assertTrue(service.findByIdIn(null).isEmpty()); assertTrue(service.findByIdIn(List.of()).isEmpty());
        verifyNoInteractions(repository);
    }
    @Test void missingGetStillFails() {
        when(repository.findById(registrationId)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.getById(registrationId));
    }
    @Test void oldConstructorDoesNotInferProfile() {
        var old = new RegistrationLookupResult(registrationId,userId,disciplineId,"CONFIRMED","{}","{}");
        assertNull(old.participantProfileId()); assertEquals(userId,old.participantUserId());
    }
}
