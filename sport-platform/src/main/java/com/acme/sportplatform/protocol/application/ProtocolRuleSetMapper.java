package com.acme.sportplatform.protocol.application;
import java.util.List;
import org.springframework.stereotype.Component;
import com.acme.sportplatform.protocol.api.*;
import com.acme.sportplatform.protocol.infrastructure.jpa.*;

@Component
public class ProtocolRuleSetMapper {
    public ProtocolRuleSetResponse toResponse(ProtocolRuleSetEntity set,
            ProtocolRuleSetVersionEntity version, List<ProtocolRuleBlockEntity> blocks) {
        return new ProtocolRuleSetResponse(set.getId(), set.getSportId(), set.getCode(),
                set.getName(), set.getCompetitionFormat().name(), set.getUnitType().name(),
                version.getId(), version.getVersionNo(), version.getStatus().name(),
                blocks.stream().map(block -> new RuleBlockResponse(block.getId(),
                        block.getOrderIndex(), block.getExecutorCode(), block.getConfig().deepCopy())).toList(),
                set.getCreatedAt(), version.getPublishedAt());
    }
}
