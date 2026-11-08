package com.acme.sportplatform.results.application;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import jakarta.persistence.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.results.api.*;
import com.acme.sportplatform.results.domain.FinalizationPolicy;
import com.acme.sportplatform.results.infrastructure.jdbc.FinalStandingsStore;

@Service
public class FinalStandingsService {
    private final FinalStandingsStore store;
    private final GetPublicDisciplineStandingsUseCase standings;
    private final EventDisciplineLookup disciplines;
    private final ResultsAccess access;
    private final ObjectMapper json;
    @PersistenceContext private EntityManager em;
    public FinalStandingsService(FinalStandingsStore store,GetPublicDisciplineStandingsUseCase standings,
            EventDisciplineLookup disciplines,ResultsAccess access,ObjectMapper json) {
        this.store=store;this.standings=standings;this.disciplines=disciplines;this.access=access;this.json=json;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED)
    public FinalStandingsResponse confirm(UUID event,UUID discipline,ConfirmFinalStandingsRequest request) {
        UUID actor=authorize(event,discipline);
        String note=FinalizationPolicy.confirmationNote(request);
        store.lock(discipline);em.clear();authorize(event,discipline);
        var repeated=store.byRequest(discipline,request.requestId());
        if(repeated.isPresent()) {
            var old=repeated.get().response();
            if(!actor.equals(old.confirmedBy()) || !note.equals(old.confirmationNote()))
                throw error("results.final_request_conflict","requestId was used with different confirmation data");
            return old;
        }
        if(store.active(discipline).isPresent()) throw error("results.final_already_confirmed","Revoke the current finalization before creating a new revision");
        store.requireReady(discipline);
        com.fasterxml.jackson.databind.JsonNode payload=json.valueToTree(standings.execute(event,discipline));
        return store.insert(event,discipline,request.requestId(),actor,note,payload);
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED)
    public FinalStandingsResponse revoke(UUID event,UUID discipline,UUID id,String rawReason) {
        UUID actor=authorize(event,discipline);String reason=FinalizationPolicy.revocationReason(rawReason);
        store.lock(discipline);em.clear();authorize(event,discipline);
        var old=store.byId(discipline,id).orElseThrow(()->error("results.final_not_found","Finalization not found")).response();
        if("REVOKED".equals(old.status())) {
            if(!actor.equals(old.revokedBy()) || !reason.equals(old.revocationReason()))
                throw error("results.final_request_conflict","Finalization was already revoked with different audit data");
            return old;
        }
        return store.revoke(discipline,id,actor,reason);
    }
    @Transactional(readOnly=true)
    public FinalStandingsResponse current(UUID event,UUID discipline) {
        requireEvent(event,discipline);
        return store.active(discipline).orElseThrow(()->error("results.final_not_found","No confirmed final standings")).response();
    }
    @Transactional(readOnly=true)
    public List<FinalStandingsResponse> history(UUID event,UUID discipline) {
        authorize(event,discipline);return store.history(discipline);
    }
    private UUID authorize(UUID event,UUID discipline) {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication==null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUserPrincipal principal)
                || principal.getUserId()==null || !access.canManageDiscipline(discipline,authentication))
            throw new AccessDeniedException("Only the event manager or platform administrator can finalize standings");
        requireEvent(event,discipline);return principal.getUserId();
    }
    private void requireEvent(UUID event,UUID discipline) {
        if(event==null || discipline==null || disciplines.findById(discipline).filter(d->event.equals(d.eventId())).isEmpty())
            throw error("results.final_not_found","Discipline not found for this event");
    }
    private BusinessException error(String code,String message){return new BusinessException(code,message);}
}
