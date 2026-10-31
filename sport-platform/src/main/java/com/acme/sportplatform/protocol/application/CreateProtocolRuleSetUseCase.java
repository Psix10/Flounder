package com.acme.sportplatform.protocol.application;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DataIntegrityViolationException;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.protocol.api.*;
import com.acme.sportplatform.protocol.domain.*;
import com.acme.sportplatform.protocol.infrastructure.jpa.*;
import com.acme.sportplatform.sports.SportLookup;

@Service
public class CreateProtocolRuleSetUseCase {
    private final ProtocolRuleSetRepository sets;
    private final ProtocolRuleSetVersionRepository versions;
    private final ProtocolRuleBlockRepository blocks;
    private final ProtocolRuleSetValidator validator;
    private final ProtocolRuleSetMapper mapper;
    private final SportLookup sports;

    public CreateProtocolRuleSetUseCase(ProtocolRuleSetRepository sets,
            ProtocolRuleSetVersionRepository versions, ProtocolRuleBlockRepository blocks,
            ProtocolRuleSetValidator validator, ProtocolRuleSetMapper mapper, SportLookup sports) {
        this.sets = sets; this.versions = versions; this.blocks = blocks;
        this.validator = validator; this.mapper = mapper; this.sports = sports;
    }

    @Transactional
    public ProtocolRuleSetResponse execute(CreateProtocolRuleSetRequest request, UUID userId) {
        if (userId == null) throw new AccessDeniedException("Authenticated user ID is required");
        List<RuleBlockRequest> validated = validator.validate(request);
        if (!sports.existsById(request.sportId())) {
            throw new BusinessException("protocol.sport_not_found", "Sport not found");
        }
        if (sets.existsBySportIdAndCode(request.sportId(), request.code())) throw duplicate();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ProtocolRuleSetEntity set = new ProtocolRuleSetEntity();
        set.setId(UUID.randomUUID()); set.setSportId(request.sportId());
        set.setCode(request.code()); set.setName(request.name().trim());
        set.setCompetitionFormat(CompetitionFormat.valueOf(request.competitionFormat()));
        set.setUnitType(ProtocolUnitType.valueOf(request.unitType()));
        set.setCreatedByUserId(userId); set.setCreatedAt(now); set.setUpdatedAt(now);
        try {
            set = sets.saveAndFlush(set);
        } catch (DataIntegrityViolationException ex) {
            if (Objects.toString(ex.getMostSpecificCause().getMessage(), "")
                    .contains("uq_protocol_rule_sets_sport_code")) throw duplicate();
            throw ex;
        }
        ProtocolRuleSetVersionEntity version = new ProtocolRuleSetVersionEntity();
        version.setId(UUID.randomUUID()); version.setRuleSetId(set.getId());
        version.setVersionNo(1); version.setStatus(ProtocolRuleSetStatus.DRAFT);
        version.setCreatedByUserId(userId); version.setCreatedAt(now);
        version = versions.saveAndFlush(version);
        List<ProtocolRuleBlockEntity> entities = new ArrayList<>();
        for (RuleBlockRequest block : validated) {
            ProtocolRuleBlockEntity entity = new ProtocolRuleBlockEntity();
            entity.setId(UUID.randomUUID()); entity.setRuleSetVersionId(version.getId());
            entity.setOrderIndex(block.orderIndex()); entity.setExecutorCode(block.executorCode());
            entity.setConfig(block.config().deepCopy()); entities.add(entity);
        }
        List<ProtocolRuleBlockEntity> saved = blocks.saveAllAndFlush(entities);
        return mapper.toResponse(set, version, saved);
    }

    private BusinessException duplicate() {
        return new BusinessException("protocol.rule_set_code_already_exists",
                "Rule set code already exists for this sport");
    }
}
