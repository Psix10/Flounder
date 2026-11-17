package com.acme.sportplatform.identity.application.bootstrap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.*;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.*;

class AdminBootstrapServiceTest {
    private static final String EMAIL = "admin@example.invalid";
    private static final String PASSWORD = "Strong-Demo-Password-2026!";
    private UserRepository users;
    private RoleRepository roles;
    private UserRoleAssignmentRepository assignments;
    private PasswordEncoder passwords;
    private EntityManager entityManager;
    private Query lock;
    private Query adminCount;
    private RoleEntity role;
    private AdminBootstrapService service;

    @BeforeEach void setUp() {
        users = mock(UserRepository.class);
        roles = mock(RoleRepository.class);
        assignments = mock(UserRoleAssignmentRepository.class);
        passwords = mock(PasswordEncoder.class);
        entityManager = mock(EntityManager.class);
        lock = mock(Query.class);
        adminCount = mock(Query.class);
        when(entityManager.createNativeQuery(contains("pg_advisory_xact_lock"))).thenReturn(lock);
        when(entityManager.createNativeQuery(contains("SELECT count(*)"))).thenReturn(adminCount);
        when(lock.getSingleResult()).thenReturn(1);
        when(adminCount.setParameter(eq("placeholder"), any())).thenReturn(adminCount);
        when(adminCount.getSingleResult()).thenReturn(0L);
        role = new RoleEntity();
        role.setId(UUID.randomUUID()); role.setCode("platform_admin"); role.setName("Admin");
        when(roles.findByCode("platform_admin")).thenReturn(Optional.of(role));
        when(users.findAllByEmailIgnoreCase(anyString())).thenReturn(List.of());
        when(passwords.encode(PASSWORD)).thenReturn("encoded-password");
        when(users.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            if (user.getId() == null) user.setId(UUID.randomUUID());
            return user;
        });
        service = new AdminBootstrapService(users, roles, assignments, passwords, entityManager);
    }
    private UserEntity existing(String status, String hash) {
        var user = new UserEntity();
        user.setId(UUID.randomUUID()); user.setEmail(EMAIL);
        user.setStatus(status); user.setPasswordHash(hash);
        when(users.findAllByEmailIgnoreCase(EMAIL)).thenReturn(List.of(user));
        return user;
    }
    @Test void invalidSettingsPerformNoDatabaseOperations() {
        assertThrows(IllegalStateException.class, () -> service.initialize(EMAIL, "short"));
        verifyNoInteractions(entityManager, users, assignments);
    }
    @Test void createsActiveUserWithEncodedPasswordAndGlobalRole() {
        assertEquals(AdminBootstrapService.Outcome.CREATED, service.initialize(EMAIL, PASSWORD));
        var captured = ArgumentCaptor.forClass(UserEntity.class);
        verify(users).saveAndFlush(captured.capture());
        assertEquals(EMAIL, captured.getValue().getEmail());
        assertEquals("active", captured.getValue().getStatus());
        assertEquals("encoded-password", captured.getValue().getPasswordHash());
        var grant = ArgumentCaptor.forClass(UserRoleAssignmentEntity.class);
        verify(assignments).saveAndFlush(grant.capture());
        assertEquals(captured.getValue().getId(), grant.getValue().getUserId());
        assertEquals(role.getId(), grant.getValue().getRoleId());
        assertNull(grant.getValue().getEventId()); assertNull(grant.getValue().getOrganizationId());
    }
    @Test void databaseLockPrecedesAccountLookup() {
        service.initialize(EMAIL, PASSWORD);
        var order = inOrder(lock, users);
        order.verify(lock).getSingleResult();
        order.verify(users).findAllByEmailIgnoreCase(EMAIL);
    }
    @Test void matchingExistingAdministratorIsNotRewritten() {
        var user = existing("active", "existing-hash");
        when(assignments.existsByUserIdAndRoleIdAndEventIdIsNullAndOrganizationIdIsNull(user.getId(), role.getId())).thenReturn(true);
        when(passwords.matches(PASSWORD, "existing-hash")).thenReturn(true);
        assertEquals(AdminBootstrapService.Outcome.ALREADY_CONFIGURED, service.initialize(EMAIL, PASSWORD));
        verify(users, never()).saveAndFlush(any());
        verify(assignments, never()).saveAndFlush(any());
        verify(passwords, never()).encode(any());
    }
    @Test void existingParticipantIsNeverPromoted() {
        existing("active", "existing-hash");
        assertThrows(IllegalStateException.class, () -> service.initialize(EMAIL, PASSWORD));
        verify(users, never()).saveAndFlush(any()); verify(assignments, never()).saveAndFlush(any());
    }
    @Test void inactiveAdministratorIsNeverReactivated() {
        existing("inactive", "existing-hash");
        assertThrows(IllegalStateException.class, () -> service.initialize(EMAIL, PASSWORD));
        verify(users, never()).saveAndFlush(any());
    }
    @Test void existingAdministratorPasswordIsNeverReset() {
        var user = existing("active", "existing-hash");
        when(assignments.existsByUserIdAndRoleIdAndEventIdIsNullAndOrganizationIdIsNull(user.getId(), role.getId())).thenReturn(true);
        when(passwords.matches(PASSWORD, "existing-hash")).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> service.initialize(EMAIL, PASSWORD));
        assertEquals("existing-hash", user.getPasswordHash()); verify(users, never()).saveAndFlush(any());
    }
    @Test void anotherConfiguredAdministratorPreventsNewAccount() {
        when(adminCount.getSingleResult()).thenReturn(1L);
        assertThrows(IllegalStateException.class, () -> service.initialize(EMAIL, PASSWORD));
        verify(users, never()).saveAndFlush(any());
    }
    @Test void ambiguousCaseInsensitiveEmailIsRejected() {
        when(users.findAllByEmailIgnoreCase(EMAIL)).thenReturn(List.of(new UserEntity(), new UserEntity()));
        assertThrows(IllegalStateException.class, () -> service.initialize(EMAIL, PASSWORD));
        verify(users, never()).saveAndFlush(any());
    }
    @Test void originalPlaceholderCanBeExplicitlyInitialized() {
        var user = new UserEntity(); user.setId(AdminBootstrapService.LEGACY_ID);
        user.setEmail(AdminBootstrapService.LEGACY_EMAIL); user.setPasswordHash(AdminBootstrapService.LEGACY_HASH);
        user.setStatus("inactive");
        when(users.findAllByEmailIgnoreCase(AdminBootstrapService.LEGACY_EMAIL)).thenReturn(List.of(user));
        assertEquals(AdminBootstrapService.Outcome.INITIALIZED_LEGACY,
                service.initialize(AdminBootstrapService.LEGACY_EMAIL, PASSWORD));
        assertEquals("active", user.getStatus()); assertEquals("encoded-password", user.getPasswordHash());
    }
    @Test void placeholderHashOnUnrelatedAccountDoesNotAllowPromotion() {
        existing("active", AdminBootstrapService.LEGACY_HASH);
        assertThrows(IllegalStateException.class, () -> service.initialize(EMAIL, PASSWORD));
        verify(users, never()).saveAndFlush(any());
    }
    @Test void missingRoleDoesNotCreateUser() {
        when(roles.findByCode("platform_admin")).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> service.initialize(EMAIL, PASSWORD));
        verify(users, never()).saveAndFlush(any());
    }
    @Test void existingGlobalAssignmentIsNotDuplicated() {
        when(assignments.existsByUserIdAndRoleIdAndEventIdIsNullAndOrganizationIdIsNull(any(), any())).thenReturn(true);
        assertEquals(AdminBootstrapService.Outcome.CREATED, service.initialize(EMAIL, PASSWORD));
        verify(assignments, never()).saveAndFlush(any());
    }
}
