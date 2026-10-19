package com.acme.sportplatform.results.infrastructure.jpa;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ResultRepository extends JpaRepository<ResultEntity, UUID> {
 Optional<ResultEntity> findByCompetitionUnitEntryId(UUID id);
 List<ResultEntity> findByCompetitionUnitEntryIdIn(List<UUID> ids);
}
