package com.acme.sportplatform.identity.application.bootstrap;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRoleAssignmentRepository;
import com.acme.sportplatform.identity.infrastructure.security.JwtService;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;

@SpringBootTest(properties = "app.bootstrap.admin.enabled=false")
@ActiveProfiles("test")
@Transactional
class AdminBootstrapIntegrationTest extends AbstractPostgresIntegrationTest {
    private static final String PASSWORD = "Strong-Demo-Password-2026!";
    @Autowired private AdminBootstrapService bootstrap;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private AuthenticationManager authentication;
    @Autowired private UserRoleAssignmentRepository assignments;
    @Autowired private JwtService jwt;

    @BeforeEach void isolateAdministratorsWithinRolledBackTestTransaction() {
        jdbc.update("""
                UPDATE users SET status = 'inactive'
                WHERE id IN (
                    SELECT a.user_id FROM user_role_assignments a
                    JOIN roles r ON r.id = a.role_id
                    WHERE lower(r.code) = 'platform_admin'
                      AND a.event_id IS NULL AND a.organization_id IS NULL
                )
                """);
    }
    private String email() { return "bootstrap-" + UUID.randomUUID() + "@example.invalid"; }
    private UUID userId(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
    }
    private String hash(String email) {
        return jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, email);
    }
    private void legacyFixture() {
        jdbc.update("""
                INSERT INTO users(id,email,password_hash,status)
                VALUES (?,?,?,'active')
                ON CONFLICT(id) DO UPDATE SET
                    email=EXCLUDED.email, password_hash=EXCLUDED.password_hash, status='active'
                """, AdminBootstrapService.LEGACY_ID, AdminBootstrapService.LEGACY_EMAIL,
                AdminBootstrapService.LEGACY_HASH);
    }
    @Test void createsRealAdministratorThatAuthenticatesAndReceivesValidToken() {
        String email = email();
        assertEquals(AdminBootstrapService.Outcome.CREATED, bootstrap.initialize(email, PASSWORD));
        assertNotEquals(PASSWORD, hash(email));
        assertTrue(encoder.matches(PASSWORD, hash(email)));
        var authenticated = authentication.authenticate(
                new UsernamePasswordAuthenticationToken(email, PASSWORD));
        var principal = (PlatformUserPrincipal) authenticated.getPrincipal();
        assertTrue(principal.isEnabled());
        assertTrue(principal.getAuthorities().stream()
                .anyMatch(a -> "ROLE_PLATFORM_ADMIN".equals(a.getAuthority())));
        var roles = assignments.findGlobalRoleCodesByUserId(principal.getUserId());
        assertTrue(roles.contains("platform_admin"));
        String token = jwt.generateAccessToken(principal, roles);
        assertEquals(principal.getUserId(), jwt.extractUserId(token));
    }
    @Test void repeatInitializationDoesNotRewritePasswordOrDuplicateRole() {
        String email = email(); bootstrap.initialize(email, PASSWORD);
        String before = hash(email); UUID id = userId(email);
        assertEquals(AdminBootstrapService.Outcome.ALREADY_CONFIGURED, bootstrap.initialize(email, PASSWORD));
        assertEquals(before, hash(email));
        assertEquals(1L, jdbc.queryForObject("""
                SELECT count(*) FROM user_role_assignments a JOIN roles r ON r.id=a.role_id
                WHERE a.user_id=? AND r.code='platform_admin'
                  AND a.event_id IS NULL AND a.organization_id IS NULL
                """, Long.class, id));
    }
    @Test void existingOrdinaryAccountCannotBePromoted() {
        String email = email(); UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash,status) VALUES (?,?,?,'active')",
                id, email, encoder.encode(PASSWORD));
        String before = hash(email);
        assertThrows(IllegalStateException.class, () -> bootstrap.initialize(email, PASSWORD));
        assertEquals(before, hash(email));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT count(*) FROM user_role_assignments WHERE user_id=?", Long.class, id));
    }
    @Test void existingConfiguredAdministratorPreventsCreationOfAnother() {
        bootstrap.initialize(email(), PASSWORD);
        String other = email();
        assertThrows(IllegalStateException.class, () -> bootstrap.initialize(other, PASSWORD));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM users WHERE email=?", Long.class, other));
    }
    @Test void unchangedLegacyPlaceholderCanBeInitializedExplicitly() {
        legacyFixture();
        assertEquals(AdminBootstrapService.Outcome.INITIALIZED_LEGACY,
                bootstrap.initialize(AdminBootstrapService.LEGACY_EMAIL, PASSWORD));
        assertEquals(AdminBootstrapService.LEGACY_ID, userId(AdminBootstrapService.LEGACY_EMAIL));
        assertTrue(encoder.matches(PASSWORD, hash(AdminBootstrapService.LEGACY_EMAIL)));
        assertEquals("active", jdbc.queryForObject("SELECT status FROM users WHERE id=?", String.class,
                AdminBootstrapService.LEGACY_ID));
    }
    @Test void migrationDisablesOnlyUnchangedPlaceholderAndPreservesConfiguredAccount() throws Exception {
        legacyFixture();
        String sql;
        try (var stream = new ClassPathResource(
                "db/migration/V035__disable_unchanged_admin_placeholder.sql").getInputStream()) {
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        jdbc.execute(sql);
        assertEquals("inactive", jdbc.queryForObject("SELECT status FROM users WHERE id=?", String.class,
                AdminBootstrapService.LEGACY_ID));
        String workingHash = encoder.encode(PASSWORD);
        jdbc.update("UPDATE users SET status='active',password_hash=? WHERE id=?", workingHash, AdminBootstrapService.LEGACY_ID);
        jdbc.execute(sql);
        assertEquals("active", jdbc.queryForObject("SELECT status FROM users WHERE id=?", String.class,
                AdminBootstrapService.LEGACY_ID));
        assertEquals(workingHash, hash(AdminBootstrapService.LEGACY_EMAIL));
    }
}
