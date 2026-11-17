package com.acme.sportplatform.support;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Test-only preparation of historical algorithm input, NOT an admission API. */
public final class ConfirmedRegistrationFixtures {
    private ConfirmedRegistrationFixtures() { }
    public static void confirmHistoricalInput(JdbcTemplate jdbc, UUID registration, UUID actor) {
        int changed = jdbc.update("""
                UPDATE registrations SET status='CONFIRMED',
                    review_note='TEST ONLY: historical algorithm pool fixture, not admission workflow',
                    reviewed_by_user_id=?, reviewed_at=now(), updated_at=now()
                WHERE id=? AND status='SUBMITTED'
                """, actor, registration);
        org.junit.jupiter.api.Assertions.assertEquals(1, changed,
                "Fixture must confirm exactly one explicitly submitted test registration");
    }
}
