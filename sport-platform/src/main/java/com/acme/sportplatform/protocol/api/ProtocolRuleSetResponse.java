package com.acme.sportplatform.protocol.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ProtocolRuleSetResponse(

        UUID id,

        UUID sportId,

        String code,

        String name,

        String competitionFormat,

        String unitType,

        UUID ruleSetVersionId,

        Integer versionNo,

        String status,

        List<RuleBlockResponse> blocks,

        OffsetDateTime createdAt,

        OffsetDateTime publishedAt

) {
}