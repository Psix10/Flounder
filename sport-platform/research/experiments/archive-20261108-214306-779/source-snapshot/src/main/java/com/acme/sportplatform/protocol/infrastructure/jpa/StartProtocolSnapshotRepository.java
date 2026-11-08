package com.acme.sportplatform.protocol.infrastructure.jpa;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface StartProtocolSnapshotRepository extends JpaRepository<StartProtocolSnapshotEntity, UUID> {
    Optional<StartProtocolSnapshotEntity> findFirstByEventDisciplineIdOrderByRevisionNoDesc(UUID eventDisciplineId);
    long countByEventDisciplineId(UUID eventDisciplineId);
}
