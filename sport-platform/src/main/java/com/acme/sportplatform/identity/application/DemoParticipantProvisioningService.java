package com.acme.sportplatform.identity.application;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import com.acme.sportplatform.common.demo.DemoDataAccess;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.identity.DemoParticipantProvisioning;
import com.acme.sportplatform.identity.ProfileLookupResult;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.ProfileEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.ProfileRepository;

@Service
public class DemoParticipantProvisioningService implements DemoParticipantProvisioning {
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final DemoDataAccess mode;
    private final PasswordEncoder passwords;
    public DemoParticipantProvisioningService(UserRepository users, ProfileRepository profiles, DemoDataAccess mode, PasswordEncoder passwords) {
        this.users = users; this.profiles = profiles; this.mode = mode; this.passwords = passwords;
    }
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ProfileLookupResult create(UUID eventId, UUID batchId, int ordinal) {
        mode.requireAllowed(eventId);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof AuthenticatedUserPrincipal principal)
                || principal.getUserId() == null
                || auth.getAuthorities().stream().noneMatch(a ->
                    "ROLE_PLATFORM_ADMIN".equals(a.getAuthority()) || "ROLE_ORGANIZER".equals(a.getAuthority()))) {
            throw new AccessDeniedException("Organizer platform principal required");
        }
        if (batchId == null || ordinal < 1 || ordinal > 64) throw new IllegalArgumentException("Invalid demo ordinal");
        var user = new UserEntity();
        user.setEmail("demo-fixture-" + batchId + "-" + String.format("%03d", ordinal) + "@example.invalid");
        user.setPasswordHash(passwords.encode(UUID.randomUUID().toString() + UUID.randomUUID()));
        user.setStatus("inactive");
        user = users.saveAndFlush(user);
        var profile = new ProfileEntity();
        profile.setUserId(user.getId());
        profile.setFirstName(ordinal % 2 == 1 ? "Демо-участник" : "Демо-участница");
        profile.setLastName(String.format("Тестовый-%03d", ordinal));
        profile.setBirthDate(LocalDate.of(2000, 1, 1));
        profile.setGender(ordinal % 2 == 1 ? "male" : "female");
        profile.setCity("Москва"); profile.setCountryCode("RU");
        profile.setClubName("Демо-клуб");
        profile.setSportMeta(Map.of("demoFixture", true, "demoBatchId", batchId.toString()));
        profile = profiles.saveAndFlush(profile);
        return new ProfileLookupResult(profile.getId(), user.getId(), profile.getFirstName(),
                profile.getLastName(), null, profile.getBirthDate(), profile.getGender(),
                profile.getCity(), profile.getCountryCode(), profile.getClubName(), profile.getSportMeta());
    }
}
