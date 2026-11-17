package com.acme.sportplatform.identity.application;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.acme.sportplatform.identity.OperatorEligibilityLookup;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.ProfileEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.ProfileRepository;

class OperatorDirectoryLookupServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final ProfileRepository profiles = mock(ProfileRepository.class);
    private final OperatorEligibilityLookup eligibility = mock(OperatorEligibilityLookup.class);
    private final OperatorDirectoryLookupService service = new OperatorDirectoryLookupService(users, profiles, eligibility);
    private final UUID id = UUID.randomUUID();
    private UserEntity user() {
        var user = mock(UserEntity.class);when(user.getId()).thenReturn(id);
        when(user.getEmail()).thenReturn("operator@example.com");return user;
    }
    @Test void blankLookupDoesNotListUsers() {
        assertTrue(service.findActiveByExactEmail(null).isEmpty());
        assertTrue(service.findActiveByExactEmail(" ").isEmpty());verifyNoInteractions(users, profiles, eligibility);
    }
    @Test void ambiguousCaseInsensitiveEmailIsNotResolvedArbitrarily() {
        var firstUser = user();
        var secondUser = user();
        when(users.findAllByEmailIgnoreCase("operator@example.com")).thenReturn(List.of(firstUser, secondUser));
        assertTrue(service.findActiveByExactEmail("operator@example.com").isEmpty());
        verifyNoInteractions(profiles, eligibility);
    }
    @Test void inactiveOrRoleRevokedUserIsNotReturnedAsCandidate() {
        var operatorUser = user();
        when(users.findAllByEmailIgnoreCase("operator@example.com")).thenReturn(List.of(operatorUser));
        when(eligibility.isActiveOperator(id)).thenReturn(false);
        assertTrue(service.findActiveByExactEmail("operator@example.com").isEmpty());verifyNoInteractions(profiles);
    }
    @Test void activeOperatorUsesProfileName() {
        var operatorUser = user();
        when(users.findAllByEmailIgnoreCase("operator@example.com")).thenReturn(List.of(operatorUser));
        when(eligibility.isActiveOperator(id)).thenReturn(true);
        var profile = mock(ProfileEntity.class);when(profile.getLastName()).thenReturn(" Оператор ");when(profile.getFirstName()).thenReturn(" Иван ");
        when(profiles.findByUserId(id)).thenReturn(Optional.of(profile));
        assertEquals("Оператор Иван", service.findActiveByExactEmail(" operator@example.com ").orElseThrow().displayName());
    }
    @Test void missingProfileFallsBackToEmail() {
        var operatorUser = user();
        when(users.findAllByEmailIgnoreCase("operator@example.com")).thenReturn(List.of(operatorUser));
        when(eligibility.isActiveOperator(id)).thenReturn(true);when(profiles.findByUserId(id)).thenReturn(Optional.empty());
        assertEquals("operator@example.com", service.findActiveByExactEmail("operator@example.com").orElseThrow().displayName());
    }
    @Test void emptyAssignmentsDoNotQueryIdentityRepositories() {
        assertTrue(service.findByIds(List.of()).isEmpty());verifyNoInteractions(users, profiles, eligibility);
    }
    @Test void assignedButNoLongerEligibleUserRemainsVisible() {
        when(profiles.findByUserIdIn(anyCollection())).thenReturn(List.of());
        var operatorUser = user();
        when(users.findAllById(any())).thenReturn(List.of(operatorUser));when(eligibility.isActiveOperator(id)).thenReturn(false);
        var result = service.findByIds(List.of(id));assertEquals(1, result.size());assertFalse(result.get(id).eligible());
        verify(users, never()).findAll();
    }
}
