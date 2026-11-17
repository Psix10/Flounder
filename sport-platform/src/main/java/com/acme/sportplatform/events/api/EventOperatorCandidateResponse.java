package com.acme.sportplatform.events.api;
import java.util.UUID;
public record EventOperatorCandidateResponse(
        UUID userId, String email, String displayName, boolean assigned) {}
