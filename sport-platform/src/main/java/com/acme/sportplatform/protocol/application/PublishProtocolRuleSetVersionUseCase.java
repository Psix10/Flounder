package com.acme.sportplatform.protocol.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.protocol.api.*;
import com.acme.sportplatform.protocol.domain.ProtocolRuleSetStatus;
import com.acme.sportplatform.protocol.infrastructure.jpa.*;

@Service
public class PublishProtocolRuleSetVersionUseCase {
    private final ProtocolRuleSetRepository sets;
    private final ProtocolRuleSetVersionRepository versions;
    private final ProtocolRuleBlockRepository blocks;
    private final ProtocolRuleSetValidator validator;
    private final ProtocolRuleSetMapper mapper;
    @PersistenceContext private EntityManager entityManager;

    public PublishProtocolRuleSetVersionUseCase(ProtocolRuleSetRepository sets,
            ProtocolRuleSetVersionRepository versions, ProtocolRuleBlockRepository blocks,
            ProtocolRuleSetValidator validator, ProtocolRuleSetMapper mapper) {
        this.sets = sets; this.versions = versions; this.blocks = blocks;
        this.validator = validator; this.mapper = mapper;
    }

    @Transactional
    public ProtocolRuleSetResponse execute(UUID setId, UUID versionId, UUID userId, boolean admin) {
        if (userId == null) throw new AccessDeniedException("Authenticated user ID is required");
        ProtocolRuleSetEntity set = sets.findLockedById(setId).orElseThrow(() ->
                new BusinessException("protocol.rule_set_not_found", "Rule set not found"));
        if (!admin && !userId.equals(set.getCreatedByUserId())) {
            throw new AccessDeniedException("You cannot publish another organizer's rule set");
        }
        ProtocolRuleSetVersionEntity version = versions.findByIdAndRuleSetId(versionId, setId)
                .orElseThrow(() -> new BusinessException("protocol.rule_version_not_found",
                        "Rule version not found"));
        entityManager.refresh(version);
        if (version.getStatus() == ProtocolRuleSetStatus.PUBLISHED) {
            throw new BusinessException("protocol.rule_version_already_published",
                    "Rule version is already published");
        }
        if (version.getStatus() != ProtocolRuleSetStatus.DRAFT) {
            throw new BusinessException("protocol.rule_version_not_draft",
                    "Only a draft rule version can be published");
        }
        List<ProtocolRuleBlockEntity> entities =
                blocks.findByRuleSetVersionIdOrderByOrderIndexAsc(versionId);
        validator.validate(new CreateProtocolRuleSetRequest(set.getSportId(), set.getCode(),
                set.getName(), set.getCompetitionFormat().name(), set.getUnitType().name(),
                entities.stream().map(block -> new RuleBlockRequest(block.getOrderIndex(),
                        block.getExecutorCode(), block.getConfig())).toList()));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        version.setStatus(ProtocolRuleSetStatus.PUBLISHED);
        version.setPublishedAt(now);
        set.setUpdatedAt(now);
        entityManager.flush();
        return mapper.toResponse(set, version, entities);
    }
}
