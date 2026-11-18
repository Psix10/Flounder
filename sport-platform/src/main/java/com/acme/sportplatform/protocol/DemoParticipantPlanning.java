package com.acme.sportplatform.protocol;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Read-only planning. Does not provision identities or create registrations. */
public interface DemoParticipantPlanning {
    record Candidate(String gender, LocalDate birthDate, String categoryCode, String seedingGroupCode) { }
    List<Candidate> plan(UUID ruleVersionId, String disciplineSettingsJson, int count);
}
