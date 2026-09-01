package com.acme.sportplatform.results.infrastructure.jpa;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetitionUnitEntryRepository extends JpaRepository<CompetitionUnitEntryEntity, UUID> {
    List<CompetitionUnitEntryEntity> findByCompetitionUnitId(UUID competitionUnitId);
    boolean existsByCompetitionUnitIdAndRegistrationId(UUID competitionUnitId, UUID registrationId);
}