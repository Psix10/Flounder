package com.acme.sportplatform.registrations.infrastructure.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface RegistrationRepository
        extends JpaRepository<RegistrationEntity, UUID> {

        List<RegistrationEntity> findByEventId(UUID eventId);

        List<RegistrationEntity> findByEventDisciplineId(UUID eventDisciplineId);

        List<RegistrationEntity> findByParticipantUserId(UUID participantUserId);
        
        List<RegistrationEntity> findByStatus(String status);

        List<RegistrationEntity> findByEventIdAndStatus(UUID eventId, String status);
        Optional<RegistrationEntity> findByEventDisciplineIdAndParticipantUserId(
                UUID eventDisciplineId,
                UUID participantUserId
        );

        boolean existsByEventDisciplineIdAndParticipantUserId(
                UUID eventDisciplineId,
                UUID participantUserId
        );

        long countByEventDisciplineIdAndStatusIn(
                UUID eventDisciplineId,
                List<String> statuses
        );

        List<RegistrationEntity> findByIdIn(List<UUID> ids);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select r from RegistrationEntity r where r.id = :id")
        Optional<RegistrationEntity> findForUpdate(@Param("id") UUID id);
}