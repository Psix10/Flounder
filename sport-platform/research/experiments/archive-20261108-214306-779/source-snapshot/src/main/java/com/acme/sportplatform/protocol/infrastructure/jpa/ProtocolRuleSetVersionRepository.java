package com.acme.sportplatform.protocol.infrastructure.jpa;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProtocolRuleSetVersionRepository extends JpaRepository<ProtocolRuleSetVersionEntity, UUID> {
    Optional<ProtocolRuleSetVersionEntity> findFirstByRuleSetIdOrderByVersionNoDesc(UUID ruleSetId);
    Optional<ProtocolRuleSetVersionEntity> findByIdAndRuleSetId(UUID id, UUID ruleSetId);
}
