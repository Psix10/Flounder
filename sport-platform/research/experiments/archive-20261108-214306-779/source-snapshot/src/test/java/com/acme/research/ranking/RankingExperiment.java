package com.acme.research.ranking;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.*;
import jakarta.persistence.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.OptimisticLockingFailureException;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.results.infrastructure.jpa.*;

final class RankingExperiment {
    enum Variant { JAVA, SQL }
    private final CompetitionUnitRepository units;
    private final CompetitionUnitEntryRepository entries;
    private final ResultRepository results;
    private final EntityManager em;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    RankingExperiment(CompetitionUnitRepository units, CompetitionUnitEntryRepository entries,
            ResultRepository results, EntityManager em, JdbcTemplate jdbc,
            PlatformTransactionManager manager) {
        this.units=units; this.entries=entries; this.results=results; this.em=em; this.jdbc=jdbc;
        tx = new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        tx.setTimeout(30);
    }
    void execute(Variant variant, UUID unitId, String strategy) {
        execute(variant,unitId,strategy,()->{});
    }
    void execute(Variant variant, UUID unitId, String strategy, Runnable afterValidation) {
        execute(variant,unitId,strategy,afterValidation,()->{});
    }
    void execute(Variant variant, UUID unitId, String strategy, Runnable afterValidation, Runnable afterWrite) {
        boolean desc;
        if ("ASC".equalsIgnoreCase(strategy)) desc=false;
        else if ("DESC".equalsIgnoreCase(strategy)) desc=true;
        else throw business("results.invalid_ranking_strategy", "rankingStrategy must be ASC or DESC");
        try {
            tx.executeWithoutResult(status -> {
                CompetitionUnitEntity unit = units.findById(unitId).orElseThrow(() ->
                        business("results.unit_not_found", "Competition unit not found: " + unitId));
                if ("PUBLISHED".equals(unit.getStatus()))
                    throw business("results.unit_published", "Published competition unit cannot be recalculated");
                em.lock(unit, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
                OffsetDateTime now = OffsetDateTime.now();
                if (variant==Variant.JAVA) javaRank(unitId, desc, now, afterValidation);
                else sqlRank(unitId, desc, now, afterValidation);
                em.flush();
                afterWrite.run();
            });
        } catch (RuntimeException failure) {
            if (isConflict(failure)) {
                BusinessException mapped = business("results.concurrent_update", "Concurrent modification; retry required");
                mapped.initCause(failure);
                throw mapped;
            }
            throw failure;
        }
    }
    private void javaRank(UUID unitId, boolean desc, OffsetDateTime now, Runnable hook) {
        List<UUID> ids = entries.findByCompetitionUnitId(unitId).stream()
                .map(CompetitionUnitEntryEntity::getId).toList();
        List<ResultEntity> all = ids.isEmpty() ? List.of() : results.findByCompetitionUnitEntryIdIn(ids);
        if (all.stream().filter(this::rankable).anyMatch(r->NumericContract.parse(r.getRawValue())==null))
            throw business("results.invalid_raw_value", "Invalid rankable result");
        hook.run();
        for (ResultEntity r:all) {
            if (rankable(r)) { r.setFinalPlace(null); r.setUpdatedAt(now); }
        }
        Comparator<Ranked> cmp = Comparator.comparing(Ranked::value);
        if (desc) cmp=cmp.reversed();
        cmp=cmp.thenComparing(r->r.result().getId().toString());
        List<Ranked> ranked = all.stream().filter(this::rankable)
                .map(r->new Ranked(r,NumericContract.parse(r.getRawValue()))).sorted(cmp).toList();
        int place=1;
        for (Ranked row:ranked) {
            row.result().setFinalPlace(place++);
            row.result().setStatus("VALID");
            row.result().setUpdatedAt(now);
        }
        results.saveAll(all);
    }
    private void sqlRank(UUID unitId, boolean desc, OffsetDateTime now, Runnable hook) {
        boolean invalid = Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS (
              SELECT 1 FROM results r JOIN competition_unit_entries e
                ON e.id=r.competition_unit_entry_id
              WHERE e.competition_unit_id=? AND r.status IN ('PENDING','VALID')
                AND ranking_experiment.numeric_value(r.raw_value) IS NULL
            )
            """, Boolean.class, unitId));
        if (invalid) throw business("results.invalid_raw_value", "Invalid rankable result");
        hook.run();
        String order = desc ? "DESC" : "ASC";
        jdbc.update("""
            WITH ranked AS MATERIALIZED (
              SELECT r.id, row_number() OVER (
                ORDER BY ranking_experiment.numeric_value(r.raw_value) %s,
                         r.id::text COLLATE "C" ASC) AS place
              FROM results r JOIN competition_unit_entries e
                ON e.id=r.competition_unit_entry_id
              WHERE e.competition_unit_id=? AND r.status IN ('PENDING','VALID')
            )
            UPDATE results r
              SET final_place=ranked.place::integer, status='VALID',
                  updated_at=?, version=r.version+1
              FROM ranked WHERE r.id=ranked.id
            """.formatted(order), unitId, now);
    }
    private boolean rankable(ResultEntity r) {
        return "PENDING".equals(r.getStatus()) || "VALID".equals(r.getStatus());
    }
    private boolean isConflict(Throwable failure) {
        for (Throwable t=failure;t!=null;t=t.getCause()) {
            if (t instanceof OptimisticLockException || t instanceof OptimisticLockingFailureException)
                return true;
            if (t instanceof SQLException s && ("40001".equals(s.getSQLState()) || "40P01".equals(s.getSQLState())))
                return true;
        }
        return false;
    }
    private static BusinessException business(String code, String message) {
        return new BusinessException(code,message);
    }
    private record Ranked(ResultEntity result, Double value) {}
}
