package com.acme.sportplatform.results.infrastructure.jpa;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CompetitionUnitEntryRepository extends JpaRepository<CompetitionUnitEntryEntity,UUID> {
 List<CompetitionUnitEntryEntity> findByCompetitionUnitId(UUID id);
}
