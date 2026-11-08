package com.acme.sportplatform.organizations.application;
import java.util.*;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.identity.*;
import com.acme.sportplatform.organizations.api.*;
import com.acme.sportplatform.organizations.infrastructure.jdbc.OrganizationMembershipStore;
class OrganizationMembershipServiceTest {
    private final OrganizationMembershipStore store=mock(OrganizationMembershipStore.class);
    private final UserAccountLookup users=mock(UserAccountLookup.class);
    private final OrganizationMembershipService service=new OrganizationMembershipService(store,users);
    private final UUID actor=UUID.randomUUID(),target=UUID.randomUUID(),organization=UUID.randomUUID();
    private AddOrganizationMemberRequest request(){return new AddOrganizationMemberRequest(target,"OWNER");}
    private void login(String role) {
        var principal=mock(AuthenticatedUserPrincipal.class);when(principal.getUserId()).thenReturn(actor);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,"",List.of(new SimpleGrantedAuthority("ROLE_"+role))));
    }
    @BeforeEach void setup() {
        login("PLATFORM_ADMIN");when(store.lockOrganization(organization)).thenReturn(true);
        when(users.lock(actor)).thenReturn(Optional.of(new UserAccountLookup.Account(actor,"active")));
        when(users.lock(target)).thenReturn(Optional.of(new UserAccountLookup.Account(target,"active")));
        when(store.find(organization,target)).thenReturn(Optional.empty());
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    private OrganizationMemberResponse existing(String role) {
        return new OrganizationMemberResponse(UUID.randomUUID(),organization,target,role,OffsetDateTime.now(),actor);
    }
    @Test void anonymousCallIsRejectedBeforeStorageAccess() {
        SecurityContextHolder.clearContext();assertThrows(AccessDeniedException.class,()->service.assign(organization,request()));verifyNoInteractions(store,users);
    }
    @Test void organizerCannotAssignEvenWithKnownOrganization() {
        login("ORGANIZER");assertThrows(AccessDeniedException.class,()->service.assign(organization,request()));verifyNoInteractions(store,users);
    }
    @Test void roleWithoutIdentifiedPrincipalIsRejected() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin","",List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))));
        assertThrows(AccessDeniedException.class,()->service.assign(organization,request()));verifyNoInteractions(store,users);
    }
    @Test void invalidRoleIsRejectedWithoutAnyWrite() {
        var ex=assertThrows(BusinessException.class,()->service.assign(organization,new AddOrganizationMemberRequest(target,"PLATFORM_ADMIN")));
        assertEquals("organizations.invalid_member_request",ex.getCode());verifyNoInteractions(store,users);
    }
    @Test void missingOrganizationIsNotFound() {
        when(store.lockOrganization(organization)).thenReturn(false);
        var ex=assertThrows(BusinessException.class,()->service.assign(organization,request()));assertEquals("organizations.member_organization_not_found",ex.getCode());
        verifyNoInteractions(users);
    }
    @Test void inactiveActorCannotUseStaleAdminAuthentication() {
        when(users.lock(actor)).thenReturn(Optional.of(new UserAccountLookup.Account(actor,"blocked")));
        assertThrows(AccessDeniedException.class,()->service.assign(organization,request()));verify(store,never()).insert(any(),any(),any(),any());
    }
    @Test void missingTargetIsNotFound() {
        when(users.lock(target)).thenReturn(Optional.empty());
        var ex=assertThrows(BusinessException.class,()->service.assign(organization,request()));assertEquals("organizations.member_user_not_found",ex.getCode());
        verify(store,never()).insert(any(),any(),any(),any());
    }
    @Test void inactiveTargetCannotReceiveMembership() {
        when(users.lock(target)).thenReturn(Optional.of(new UserAccountLookup.Account(target,"blocked")));
        var ex=assertThrows(BusinessException.class,()->service.assign(organization,request()));assertEquals("organizations.member_user_inactive",ex.getCode());
    }
    @Test void newAssignmentCapturesActorAndRepeatedAssignmentPreservesOriginalRecord() {
        var row=existing("OWNER");when(store.insert(organization,target,"OWNER",actor)).thenReturn(Optional.of(row));
        var first=service.assign(organization,request());assertTrue(first.created());assertEquals(actor,first.member().assignedByUserId());
        when(store.find(organization,target)).thenReturn(Optional.of(row));
        var repeated=service.assign(organization,request());assertFalse(repeated.created());assertEquals(row,repeated.member());
        verify(store,times(1)).insert(organization,target,"OWNER",actor);
    }
    @Test void differentRoleDoesNotOverwriteExistingMembership() {
        when(store.find(organization,target)).thenReturn(Optional.of(existing("ADMIN")));
        var ex=assertThrows(BusinessException.class,()->service.assign(organization,request()));assertEquals("organizations.member_role_conflict",ex.getCode());
        verify(store,never()).insert(any(),any(),any(),any());
    }
}
