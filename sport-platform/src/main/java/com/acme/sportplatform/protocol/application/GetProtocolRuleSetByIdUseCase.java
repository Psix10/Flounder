package com.acme.sportplatform.protocol.application;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.protocol.api.ProtocolRuleSetResponse;
import com.acme.sportplatform.protocol.infrastructure.jpa.*;

@Service
public class GetProtocolRuleSetByIdUseCase {
    private final ProtocolRuleSetRepository sets;
    private final ProtocolRuleSetVersionRepository versions;
    private final ProtocolRuleBlockRepository blocks;
    private final ProtocolRuleSetMapper mapper;

    public GetProtocolRuleSetByIdUseCase(ProtocolRuleSetRepository sets,
            ProtocolRuleSetVersionRepository versions, ProtocolRuleBlockRepository blocks,
            ProtocolRuleSetMapper mapper) {
        this.sets = sets; this.versions = versions; this.blocks = blocks; this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public ProtocolRuleSetResponse execute(UUID id, UUID versionId, UUID userId, boolean admin) {
        if (userId == null) throw new AccessDeniedException("Authenticated user ID is required");
        ProtocolRuleSetEntity set = sets.findById(id).orElseThrow(() ->
                new BusinessException("protocol.rule_set_not_found", "Rule set not found"));
        if (!admin && !userId.equals(set.getCreatedByUserId())) {
            throw new AccessDeniedException("You cannot read another organizer's rule set");
        }
        ProtocolRuleSetVersionEntity version = (versionId == null
                ? versions.findFirstByRuleSetIdOrderByVersionNoDesc(id)
                : versions.findByIdAndRuleSetId(versionId, id)).orElseThrow(() ->
                        new BusinessException("protocol.rule_version_not_found", "Rule version not found"));
        return mapper.toResponse(set, version,
                blocks.findByRuleSetVersionIdOrderByOrderIndexAsc(version.getId()));
    }
}
