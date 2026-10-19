package com.acme.sportplatform.results.infrastructure.jpa;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CompetitionUnitRepository extends JpaRepository<CompetitionUnitEntity,UUID> {}
