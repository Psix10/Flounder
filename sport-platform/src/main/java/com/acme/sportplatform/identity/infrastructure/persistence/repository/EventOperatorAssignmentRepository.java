package com.acme.sportplatform.events.infrastructure.persistence.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.acme.sportplatform.events.infrastructure.persistence.entity.EventOperatorAssignmentEntity;

public interface EventOperatorAssignmentRepository
        extends JpaRepository<EventOperatorAssignmentEntity, UUID> {

    List<EventOperatorAssignmentEntity> findByEventIdOrderByCreatedAtAscIdAsc(
            UUID eventId
    );

    boolean existsByEventIdAndOperatorUserId(
            UUID eventId,
            UUID operatorUserId
    );

    @Modifying
    @Query(
            value = """
                    INSERT INTO event_operator_assignments (
                        id,
                        event_id,
                        operator_user_id,
                        assigned_by_user_id,
                        created_at
                    )
                    VALUES (
                        :id,
                        :eventId,
                        :operatorUserId,
                        :assignedByUserId,
                        :createdAt
                    )
                    ON CONFLICT (event_id, operator_user_id)
                    DO NOTHING
                    """,
            nativeQuery = true
    )
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("eventId") UUID eventId,
            @Param("operatorUserId") UUID operatorUserId,
            @Param("assignedByUserId") UUID assignedByUserId,
            @Param("createdAt") OffsetDateTime createdAt
    );

    @Modifying
    @Query("""
            DELETE FROM EventOperatorAssignmentEntity assignment
            WHERE assignment.eventId = :eventId
              AND assignment.operatorUserId = :operatorUserId
            """)
    int deleteAssignment(
            @Param("eventId") UUID eventId,
            @Param("operatorUserId") UUID operatorUserId
    );
}