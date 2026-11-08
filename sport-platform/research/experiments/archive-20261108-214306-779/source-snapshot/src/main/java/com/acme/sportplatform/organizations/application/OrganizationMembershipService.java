package com.acme.sportplatform.organizations.application;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.identity.UserAccountLookup;
import com.acme.sportplatform.organizations.api.*;
import com.acme.sportplatform.organizations.infrastructure.jdbc.OrganizationMembershipStore;
@Service
public class OrganizationMembershipService {
    public record Assignment(boolean created, OrganizationMemberResponse member) { }
    private final OrganizationMembershipStore store;
    private final UserAccountLookup users;
    public OrganizationMembershipService(OrganizationMembershipStore store,UserAccountLookup users) {
        this.store=store;this.users=users;
    }
    @Transactional
    public Assignment assign(UUID organization,AddOrganizationMemberRequest request) {
        UUID actor=administrator();
        if(organization==null || request==null || request.userId()==null
                || !Set.of("OWNER","ADMIN").contains(request.memberRole()==null?"":request.memberRole()))
            throw error("organizations.invalid_member_request","organizationId, userId and exact OWNER/ADMIN memberRole are required");
        if(!store.lockOrganization(organization)) throw organizationNotFound();
        requireActiveActor(actor);
        var target=users.lock(request.userId()).orElseThrow(()->error("organizations.member_user_not_found","Target user not found"));
        if(!target.active()) throw error("organizations.member_user_inactive","Only an active account can receive a new or repeated membership assignment");
        var existing=store.find(organization,request.userId());
        if(existing.isPresent()) return repeated(existing.get(),request.memberRole());
        var inserted=store.insert(organization,request.userId(),request.memberRole(),actor);
        if(inserted.isPresent()) return new Assignment(true,inserted.get());
        // A concurrent non-service insert may win the unique pair; never silently change its role.
        return repeated(store.find(organization,request.userId()).orElseThrow(
                ()->error("organizations.member_concurrent_update","Membership changed concurrently; refresh and retry")),request.memberRole());
    }
    @Transactional
    public List<OrganizationMemberResponse> list(UUID organization) {
        UUID actor=administrator();requireActiveActor(actor);
        if(organization==null || !store.organizationExists(organization)) throw organizationNotFound();
        return List.copyOf(store.list(organization));
    }
    private Assignment repeated(OrganizationMemberResponse member,String role) {
        if(!role.equals(member.memberRole())) throw error("organizations.member_role_conflict",
                "Membership already exists with another role; this endpoint does not change roles");
        return new Assignment(false,member);
    }
    private void requireActiveActor(UUID actor) {
        if(users.lock(actor).filter(UserAccountLookup.Account::active).isEmpty())
            throw new AccessDeniedException("An active platform administrator account is required");
    }
    private UUID administrator() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof AuthenticatedUserPrincipal principal)
                || principal.getUserId()==null || auth.getAuthorities().stream().noneMatch(a->"ROLE_PLATFORM_ADMIN".equals(a.getAuthority())))
            throw new AccessDeniedException("Platform administrator with an identified account is required");
        return principal.getUserId();
    }
    private BusinessException organizationNotFound(){return error("organizations.member_organization_not_found","Organization not found");}
    private BusinessException error(String code,String message){return new BusinessException(code,message);}
}
