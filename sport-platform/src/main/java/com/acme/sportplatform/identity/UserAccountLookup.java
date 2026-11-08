package com.acme.sportplatform.identity;
import java.util.Optional;
import java.util.UUID;
/** Read-lock account state in the caller's transaction without exposing identity internals. */
public interface UserAccountLookup {
    record Account(UUID id, String status) {
        public boolean active() { return "active".equalsIgnoreCase(status); }
    }
    Optional<Account> lock(UUID userId);
}
