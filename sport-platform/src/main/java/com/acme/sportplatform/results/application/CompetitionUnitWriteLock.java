package com.acme.sportplatform.results.application;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Component
public class CompetitionUnitWriteLock {

    private final JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    public CompetitionUnitWriteLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CompetitionUnitEntity acquire(UUID unitId) {
        List<UUID> lockedIds = jdbcTemplate.query(
                """
                SELECT id
                FROM competition_units
                WHERE id = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                unitId
        );

        if (lockedIds.isEmpty()) {
            throw new BusinessException(
                    "results.unit_not_found",
                    "Competition unit not found: " + unitId
            );
        }

        CompetitionUnitEntity unit = entityManager.find(
                CompetitionUnitEntity.class,
                unitId
        );

        if (unit == null) {
            throw new BusinessException(
                    "results.unit_not_found",
                    "Competition unit not found: " + unitId
            );
        }

        entityManager.refresh(unit);

        return unit;
    }
}