package com.acme.sportplatform.results.application;
import java.util.*;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import jakarta.persistence.EntityManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.competition.*;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.results.api.*;
import com.acme.sportplatform.results.infrastructure.jdbc.FinalStandingsStore;
class FinalStandingsServiceTest {
    private final UUID event=UUID.randomUUID(),discipline=UUID.randomUUID(),actor=UUID.randomUUID(),requestId=UUID.randomUUID();
    private final FinalStandingsStore store=mock(FinalStandingsStore.class);
    private final GetPublicDisciplineStandingsUseCase standings=mock(GetPublicDisciplineStandingsUseCase.class);
    private final EventDisciplineLookup lookup=mock(EventDisciplineLookup.class);
    private final ResultsAccess access=mock(ResultsAccess.class);
    private final ObjectMapper json=new ObjectMapper();
    private final FinalStandingsService service=new FinalStandingsService(store,standings,lookup,access,json);
    @BeforeEach void setup() {
        var principal=mock(AuthenticatedUserPrincipal.class);when(principal.getUserId()).thenReturn(actor);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,"",List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))));
        when(access.canManageDiscipline(eq(discipline),any())).thenReturn(true);
        when(lookup.findById(discipline)).thenReturn(Optional.of(new EventDisciplineSummary(discipline,event,"d","D","INDIVIDUAL","HEAT","TIME","ASC",40,BigDecimal.ZERO,"RUB","{}")));
        when(store.byRequest(discipline,requestId)).thenReturn(Optional.empty());when(store.active(discipline)).thenReturn(Optional.empty());
        ReflectionTestUtils.setField(service,"em",mock(EntityManager.class));
    }
    @AfterEach void cleanup() {SecurityContextHolder.clearContext();}
    private ConfirmFinalStandingsRequest request(){return new ConfirmFinalStandingsRequest(requestId,true,"checked");}
    private FinalStandingsResponse response(String status) {
        return new FinalStandingsResponse(UUID.randomUUID(),event,discipline,1,status,OffsetDateTime.now(),actor,"checked",json.createObjectNode(),null,null,null);
    }
    @Test void refusesUnauthenticatedDirectCall() {
        SecurityContextHolder.clearContext();assertThrows(AccessDeniedException.class,()->service.confirm(event,discipline,request()));verifyNoInteractions(store);
    }
    @Test void refusesCallerWithoutManagePermission() {
        when(access.canManageDiscipline(eq(discipline),any())).thenReturn(false);
        assertThrows(AccessDeniedException.class,()->service.confirm(event,discipline,request()));verifyNoInteractions(store);
    }
    @Test void confirmsAfterLockAndReadinessCheck() {
        var expected=response("CONFIRMED");when(standings.execute(event,discipline)).thenReturn(new PublicDisciplineStandingsResponse(event,discipline,"TIME","ASC","COMPETITION",List.of()));
        when(store.insert(eq(event),eq(discipline),eq(requestId),eq(actor),eq("checked"),any())).thenReturn(expected);
        assertEquals(expected,service.confirm(event,discipline,request()));
        var order=inOrder(store,standings);order.verify(store).lock(discipline);order.verify(store).byRequest(discipline,requestId);
        order.verify(store).active(discipline);order.verify(store).requireReady(discipline);order.verify(standings).execute(event,discipline);
        order.verify(store).insert(eq(event),eq(discipline),eq(requestId),eq(actor),eq("checked"),any());
    }
    @Test void repeatingSameRequestReturnsExistingSnapshotWithoutRecalculation() {
        var old=response("CONFIRMED");when(store.byRequest(discipline,requestId)).thenReturn(Optional.of(new FinalStandingsStore.Stored(requestId,old)));
        assertEquals(old,service.confirm(event,discipline,request()));verifyNoInteractions(standings);verify(store,never()).requireReady(any());
    }
    @Test void reusedRequestWithDifferentNoteIsConflict() {
        var old=response("CONFIRMED");when(store.byRequest(discipline,requestId)).thenReturn(Optional.of(new FinalStandingsStore.Stored(requestId,old)));
        var ex=assertThrows(BusinessException.class,()->service.confirm(event,discipline,new ConfirmFinalStandingsRequest(requestId,true,"changed")));
        assertEquals("results.final_request_conflict",ex.getCode());verifyNoInteractions(standings);
    }
    @Test void differentRequestCannotOverwriteActiveSnapshot() {
        when(store.active(discipline)).thenReturn(Optional.of(new FinalStandingsStore.Stored(UUID.randomUUID(),response("CONFIRMED"))));
        var ex=assertThrows(BusinessException.class,()->service.confirm(event,discipline,request()));assertEquals("results.final_already_confirmed",ex.getCode());
        verifyNoInteractions(standings);
    }
    @Test void publicReadReturnsSavedPayloadWithoutLiveRecalculation() {
        var old=response("CONFIRMED");when(store.active(discipline)).thenReturn(Optional.of(new FinalStandingsStore.Stored(requestId,old)));
        SecurityContextHolder.clearContext();assertEquals(old,service.current(event,discipline));verifyNoInteractions(standings);
    }
    @Test void publicReadWithoutConfirmationIsNotFound() {
        var ex=assertThrows(BusinessException.class,()->service.current(event,discipline));assertEquals("results.final_not_found",ex.getCode());
    }
}
