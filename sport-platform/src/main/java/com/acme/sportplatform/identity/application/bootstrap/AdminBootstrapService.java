package com.acme.sportplatform.identity.application.bootstrap;

import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserRoleAssignmentEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.RoleRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRoleAssignmentRepository;

@Service
public class AdminBootstrapService {
    static final UUID LEGACY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final String LEGACY_EMAIL = "admin@sport-platform.local";
    static final String LEGACY_HASH = "$2a$10$REPLACE_WITH_REAL_BCRYPT_HASH";
    public enum Outcome { CREATED, INITIALIZED_LEGACY, ALREADY_CONFIGURED }

    private final UserRepository users;
    private final RoleRepository roles;
    private final UserRoleAssignmentRepository assignments;
    private final PasswordEncoder passwords;
    private final EntityManager entityManager;

    public AdminBootstrapService(UserRepository users, RoleRepository roles,
            UserRoleAssignmentRepository assignments, PasswordEncoder passwords,
            EntityManager entityManager) {
        this.users = users;
        this.roles = roles;
        this.assignments = assignments;
        this.passwords = passwords;
        this.entityManager = entityManager;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Outcome initialize(String email, String password) {
        var credentials = AdminBootstrapCredentials.validate(email, password);
        entityManager.createNativeQuery(
                "SELECT 1 FROM pg_advisory_xact_lock(6720441050910031)").getSingleResult();
        var role = roles.findByCode("platform_admin").orElseThrow(() ->
                new IllegalStateException("Bootstrap admin role is missing; apply database migrations."));
        var matches = users.findAllByEmailIgnoreCase(credentials.email());
        if (matches.size() > 1) {
            throw new IllegalStateException("Bootstrap admin email is ambiguous; no accounts were changed.");
        }
        if (!matches.isEmpty()) {
            var user = matches.get(0);
            boolean legacy = LEGACY_ID.equals(user.getId())
                    && LEGACY_EMAIL.equalsIgnoreCase(user.getEmail())
                    && LEGACY_HASH.equals(user.getPasswordHash())
                    && ("active".equalsIgnoreCase(user.getStatus())
                        || "inactive".equalsIgnoreCase(user.getStatus()));
            if (legacy) {
                requireNoConfiguredAdmin();
                user.setPasswordHash(passwords.encode(credentials.password()));
                user.setStatus("active");
                users.saveAndFlush(user);
                grantGlobalRoleIfMissing(user.getId(), role.getId());
                return Outcome.INITIALIZED_LEGACY;
            }
            if (!"active".equalsIgnoreCase(user.getStatus())
                    || !assignments.existsByUserIdAndRoleIdAndEventIdIsNullAndOrganizationIdIsNull(
                            user.getId(), role.getId())
                    || !passwords.matches(credentials.password(), user.getPasswordHash())) {
                throw new IllegalStateException("Bootstrap refuses to promote, reactivate or reset an existing account. "
                        + "Use an existing administrator or the account recovery procedure.");
            }
            return Outcome.ALREADY_CONFIGURED;
        }
        requireNoConfiguredAdmin();
        var user = new UserEntity();
        user.setEmail(credentials.email());
        user.setPasswordHash(passwords.encode(credentials.password()));
        user.setStatus("active");
        user = users.saveAndFlush(user);
        grantGlobalRoleIfMissing(user.getId(), role.getId());
        return Outcome.CREATED;
    }

    private void requireNoConfiguredAdmin() {
        Number count = (Number) entityManager.createNativeQuery("""
                SELECT count(*) FROM users u
                WHERE lower(u.status) = 'active'
                  AND u.password_hash <> :placeholder
                  AND EXISTS (
                      SELECT 1 FROM user_role_assignments a
                      JOIN roles r ON r.id = a.role_id
                      WHERE a.user_id = u.id
                        AND a.event_id IS NULL AND a.organization_id IS NULL
                        AND lower(r.code) = 'platform_admin'
                  )
                """)
                .setParameter("placeholder", LEGACY_HASH)
                .getSingleResult();
        if (count.longValue() != 0) {
            throw new IllegalStateException("A configured active platform administrator already exists. "
                    + "Bootstrap will not create another one or change existing accounts.");
        }
    }

    private void grantGlobalRoleIfMissing(UUID userId, UUID roleId) {
        if (!assignments.existsByUserIdAndRoleIdAndEventIdIsNullAndOrganizationIdIsNull(userId, roleId)) {
            var assignment = new UserRoleAssignmentEntity();
            assignment.setUserId(userId);
            assignment.setRoleId(roleId);
            assignments.saveAndFlush(assignment);
        }
    }
}
