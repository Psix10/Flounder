package com.acme.sportplatform.identity.application;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.acme.sportplatform.identity.OperatorDirectoryLookup;
import com.acme.sportplatform.identity.OperatorEligibilityLookup;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.ProfileEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.ProfileRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class OperatorDirectoryLookupService implements OperatorDirectoryLookup {
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final OperatorEligibilityLookup eligibility;

    public OperatorDirectoryLookupService(UserRepository users, ProfileRepository profiles,
            OperatorEligibilityLookup eligibility) {
        this.users = users; this.profiles = profiles; this.eligibility = eligibility;
    }

    @Override
    public Optional<OperatorView> findActiveByExactEmail(String email) {
        if (email == null || email.isBlank()) return Optional.empty();
        var matches = users.findAllByEmailIgnoreCase(email.strip());
        if (matches.size() != 1) return Optional.empty();
        var user = matches.get(0);
        if (!eligibility.isActiveOperator(user.getId())) return Optional.empty();
        return Optional.of(view(user, profiles.findByUserId(user.getId()).orElse(null), true));
    }

    @Override
    public Map<UUID, OperatorView> findByIds(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();
        var ids = new LinkedHashSet<UUID>();
        userIds.stream().filter(Objects::nonNull).forEach(ids::add);
        if (ids.isEmpty()) return Map.of();
        var profileByUser = new HashMap<UUID, ProfileEntity>();
        profiles.findByUserIdIn(ids).forEach(profile -> profileByUser.putIfAbsent(profile.getUserId(), profile));
        var result = new HashMap<UUID, OperatorView>();
        users.findAllById(ids).forEach(user -> result.put(user.getId(),
                view(user, profileByUser.get(user.getId()), eligibility.isActiveOperator(user.getId()))));
        return Map.copyOf(result);
    }

    private OperatorView view(UserEntity user, ProfileEntity profile, boolean eligible) {
        String name = profile == null ? "" : Stream.of(profile.getLastName(), profile.getFirstName())
                .filter(Objects::nonNull).map(String::strip).filter(part -> !part.isEmpty())
                .collect(Collectors.joining(" "));
        String email = user.getEmail();
        if (name.isEmpty()) name = email == null || email.isBlank() ? user.getId().toString() : email;
        return new OperatorView(user.getId(), email, name, eligible);
    }
}
