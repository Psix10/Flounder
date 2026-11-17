package com.acme.sportplatform.identity;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface OperatorDirectoryLookup {
    record OperatorView(UUID userId, String email, String displayName, boolean eligible) {}
    Optional<OperatorView> findActiveByExactEmail(String email);
    Map<UUID, OperatorView> findByIds(Collection<UUID> userIds);
}
