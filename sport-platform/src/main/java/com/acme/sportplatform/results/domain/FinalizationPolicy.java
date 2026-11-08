package com.acme.sportplatform.results.domain;
import java.util.List;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.results.api.ConfirmFinalStandingsRequest;
public final class FinalizationPolicy {
    private FinalizationPolicy() { }
    public static String confirmationNote(ConfirmFinalStandingsRequest request) {
        if (request == null || request.requestId() == null || !Boolean.TRUE.equals(request.programmeComplete()))
            throw new BusinessException("results.final_invalid_request", "Explicit programmeComplete=true and requestId are required");
        String note = request.note() == null ? "" : request.note().trim();
        if (note.length() > 500) throw new BusinessException("results.final_invalid_request", "Confirmation note is too long");
        return note;
    }
    public static String revocationReason(String raw) {
        if (raw == null || raw.isBlank() || raw.trim().length() > 1000)
            throw new BusinessException("results.final_invalid_reason", "A nonblank reason of at most 1000 characters is required");
        return raw.trim();
    }
    public static void requirePublished(List<String> statuses) {
        if (statuses == null || statuses.isEmpty() || statuses.stream().anyMatch(s -> !"PUBLISHED".equals(s)))
            throw new BusinessException("results.final_not_ready", "At least one heat is required and every heat must be published");
    }
}
