package com.acme.sportplatform.results.application;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.registrations.domain.RegistrationStatus;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationEntity;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntity;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntryEntity;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntryRepository;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitRepository;

@Service
public class AssignRegistrationToUnitUseCase {

    private final CompetitionUnitRepository competitionUnitRepository;
    private final CompetitionUnitEntryRepository entryRepository;
    private final RegistrationRepository registrationRepository;

    public AssignRegistrationToUnitUseCase(
            CompetitionUnitRepository competitionUnitRepository,
            CompetitionUnitEntryRepository entryRepository,
            RegistrationRepository registrationRepository
    ) {
        this.competitionUnitRepository = competitionUnitRepository;
        this.entryRepository = entryRepository;
        this.registrationRepository = registrationRepository;
    }

    @Transactional
    public UUID execute(
            UUID competitionUnitId,
            UUID registrationId,
            Integer laneOrPosition
    ) {
        CompetitionUnitEntity competitionUnit = competitionUnitRepository
                .findById(competitionUnitId)
                .orElseThrow(() -> new BusinessException(
                        "results.competition_unit_not_found",
                        "Заплыв/забег не найден"
                ));

        RegistrationEntity registration = registrationRepository
                .findById(registrationId)
                .orElseThrow(() -> new BusinessException(
                        "results.registration_not_found",
                        "Заявка не найдена"
                ));

        if (!RegistrationStatus.CONFIRMED.name().equals(
                registration.getStatus()
        )) {
            throw new BusinessException(
                    "results.registration_not_confirmed",
                    "В соревнование можно назначить только подтверждённую заявку"
            );
        }

        if (!competitionUnit.getEventDisciplineId().equals(
                registration.getEventDisciplineId()
        )) {
            throw new BusinessException(
                    "results.registration_discipline_mismatch",
                    "Заявка относится к другой дисциплине"
            );
        }

        if (entryRepository.existsByCompetitionUnitIdAndRegistrationId(
                competitionUnitId,
                registrationId
        )) {
            throw new BusinessException(
                    "results.entry_already_exists",
                    "Участник уже назначен в этот заплыв"
            );
        }

        if (laneOrPosition != null
                && entryRepository
                        .existsByCompetitionUnitIdAndLaneOrPosition(
                                competitionUnitId,
                                laneOrPosition
                        )) {
            throw new BusinessException(
                    "results.lane_already_taken",
                    "Эта дорожка или стартовая позиция уже занята"
            );
        }

        CompetitionUnitEntryEntity entry =
                new CompetitionUnitEntryEntity();

        entry.setId(UUID.randomUUID());
        entry.setCompetitionUnitId(competitionUnitId);
        entry.setRegistrationId(registrationId);
        entry.setLaneOrPosition(laneOrPosition);
        entry.setCreatedAt(OffsetDateTime.now());

        return entryRepository.save(entry).getId();
    }
}