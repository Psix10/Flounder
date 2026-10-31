package com.acme.sportplatform.protocol.infrastructure.jpa;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProtocolRuleSetRepository extends JpaRepository<ProtocolRuleSetEntity, UUID> {
    boolean existsBySportIdAndCode(UUID sportId, String code);
}
