package com.acme.sportplatform.protocol.infrastructure.jpa;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface ProtocolRuleSetRepository extends JpaRepository<ProtocolRuleSetEntity, UUID> {
    boolean existsBySportIdAndCode(UUID sportId, String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ruleSet from ProtocolRuleSetEntity ruleSet where ruleSet.id = :id")
    Optional<ProtocolRuleSetEntity> findLockedById(@Param("id") UUID id);
}
