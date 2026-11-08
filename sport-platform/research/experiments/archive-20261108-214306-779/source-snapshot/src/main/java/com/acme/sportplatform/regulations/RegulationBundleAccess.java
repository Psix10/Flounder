package com.acme.sportplatform.regulations;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RegulationBundleAccess {
    record Binding(UUID ruleVersionId, String competitionFormat, String unitType) { }
    record Document(UUID id, UUID templateId, UUID sportId, String code, String name,
            String description, int versionNo, String status, LocalDate effectiveFrom,
            String notes, UUID createdBy, List<String> conditions, Binding binding) { }
    Document read(UUID versionId);
    Binding binding(UUID versionId);
    Document publish(UUID sourceVersionId, UUID sportId, String code, String name,
            String description, int versionNo, LocalDate effectiveFrom, String notes,
            List<String> conditions, UUID ruleVersionId, UUID actorId, boolean admin);
}
