package com.acme.sportplatform.identity.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.acme.sportplatform.common.demo.DemoDataAccess;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.identity.DemoParticipantProvisioning.Fixture;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.ProfileEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.ProfileRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DemoParticipantProvisioningFixtureTest {
    private final UUID event = UUID.randomUUID(), batch = UUID.randomUUID(), actor = UUID.randomUUID();
    private final UserRepository users = mock(UserRepository.class);
    private final ProfileRepository profiles = mock(ProfileRepository.class);
    private final DemoDataAccess mode = mock(DemoDataAccess.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final DemoParticipantProvisioningService service =
            new DemoParticipantProvisioningService(users, profiles, mode, passwords);

    @BeforeEach void authenticate() { authenticateAs("ROLE_ORGANIZER"); }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void authenticateAs(String role) {
        AuthenticatedUserPrincipal principal = () -> actor;
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, "unused", List.of(new SimpleGrantedAuthority(role))));
    }

    private void allowWrites() {
        when(users.saveAndFlush(any(UserEntity.class))).thenAnswer(call -> call.getArgument(0));
        when(profiles.saveAndFlush(any(ProfileEntity.class))).thenAnswer(call -> call.getArgument(0));
        when(passwords.encode(anyString())).thenReturn("encoded-test-password");
    }

    @Test void femaleProfileDoesNotDependOnOddOrdinal() {
        allowWrites();
        var birth = LocalDate.of(2010, 5, 17);
        var result = service.create(event, batch, 1, new Fixture("FEMALE", birth));
        assertEquals("female", result.gender());
        assertEquals(birth, result.birthDate());
        assertEquals("Демо-участница", result.firstName());
        assertEquals("Тестовый-001", result.lastName());
        assertEquals(true, result.sportMeta().get("demoFixture"));
        assertEquals(batch.toString(), result.sportMeta().get("demoBatchId"));
        verify(mode).requireAllowed(event);
    }

    @Test void maleProfileDoesNotDependOnEvenOrdinal() {
        allowWrites();
        var birth = LocalDate.of(1980, 2, 29);
        var result = service.create(event, batch, 2, new Fixture("MALE", birth));
        assertEquals("male", result.gender());
        assertEquals(birth, result.birthDate());
        assertEquals("Демо-участник", result.firstName());
    }

    @Test void legacyCallRetainsAlternationAndBirthDate() {
        allowWrites();
        var first = service.create(event, batch, 1);
        var second = service.create(event, batch, 2);
        assertEquals("male", first.gender());
        assertEquals("female", second.gender());
        assertEquals(LocalDate.of(2000, 1, 1), first.birthDate());
        assertEquals(first.birthDate(), second.birthDate());
    }

    @Test void userRemainsInactiveWithEncodedRandomPasswordAndInvalidEmailDomain() {
        allowWrites();
        service.create(event, batch, 1, new Fixture("MALE", LocalDate.of(2000, 1, 1)));
        var capture = org.mockito.ArgumentCaptor.forClass(UserEntity.class);
        verify(users).saveAndFlush(capture.capture());
        assertEquals("inactive", capture.getValue().getStatus());
        assertEquals("encoded-test-password", capture.getValue().getPasswordHash());
        assertEquals("demo-fixture-" + batch + "-001@example.invalid", capture.getValue().getEmail());
        verify(passwords).encode(argThat(value -> value != null && value.length() >= 64));
    }

    @Test void rejectsInvalidFixtureFields() {
        for (String gender : new String[] {null, "", "male", "UNKNOWN"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new Fixture(gender, LocalDate.of(2000, 1, 1)));
        }
        assertThrows(IllegalArgumentException.class, () -> new Fixture("MALE", null));
        assertThrows(IllegalArgumentException.class,
                () -> new Fixture("FEMALE", LocalDate.now().plusDays(1)));
        verifyNoInteractions(users, profiles, passwords);
    }

    @Test void rejectsNullFixtureBeforeWrites() {
        assertThrows(IllegalArgumentException.class, () -> service.create(event, batch, 1, null));
        verifyNoInteractions(users, profiles, passwords);
    }

    @Test void rejectsInvalidOrdinalsAndBatchBeforeWrites() {
        var fixture = new Fixture("MALE", LocalDate.of(2000, 1, 1));
        for (int ordinal : new int[] {0, -1, 65}) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.create(event, batch, ordinal, fixture));
        }
        assertThrows(IllegalArgumentException.class, () -> service.create(event, null, 1, fixture));
        verifyNoInteractions(users, profiles, passwords);
    }

    @Test void rejectsOperatorBeforeWrites() {
        authenticateAs("ROLE_OPERATOR");
        assertThrows(AccessDeniedException.class, () -> service.create(
                event, batch, 1, new Fixture("MALE", LocalDate.of(2000, 1, 1))));
        verifyNoInteractions(users, profiles, passwords);
    }

    @Test void rejectsUnauthenticatedInvocationBeforeWrites() {
        SecurityContextHolder.clearContext();
        assertThrows(AccessDeniedException.class, () -> service.create(
                event, batch, 1, new Fixture("MALE", LocalDate.of(2000, 1, 1))));
        verifyNoInteractions(users, profiles, passwords);
    }

    @Test void preservesAllowlistGateBeforeWrites() {
        doThrow(new AccessDeniedException("demo disabled")).when(mode).requireAllowed(event);
        assertThrows(AccessDeniedException.class, () -> service.create(
                event, batch, 1, new Fixture("MALE", LocalDate.of(2000, 1, 1))));
        verifyNoInteractions(users, profiles, passwords);
    }

    @Test void adminCanProvisionExplicitFixture() {
        authenticateAs("ROLE_PLATFORM_ADMIN");
        allowWrites();
        assertEquals("female", service.create(event, batch, 1,
                new Fixture("FEMALE", LocalDate.of(1990, 1, 1))).gender());
    }
}
