# NF-01: admission cannot change an already generated programme

A submitted registration may become CONFIRMED only while there is no event-level
session snapshot, discipline start snapshot or competition unit in its discipline.
This reuses RegistrationProtocolGuard, including its conservative event-wide
session-snapshot policy. It is not a new schedule-reset or late-entry workflow.

ReviewRegistrationUseCase authorizes the actor and observed event before taking
shared programme locks, then uses this order:

1. Event batch advisory transaction lock (the same service used by session generation).
2. Discipline write lock (the same public lookup used by single generation).
3. Registration write lock and refresh from the database.
4. Recheck registration scope, event-management permission, status and request.
5. Check the existing programme guard before any review-field/status mutation.

The review entry point explicitly requests READ_COMMITTED. Normal generation
continues to use the project's PostgreSQL default READ_COMMITTED transaction
semantics. Joining a stronger-isolation outer transaction is not a newly supported
workflow. Direct database maintenance is not made safe by this application guard.

An administrator bypasses event-management permission checks, but NOT the
programme guard. A conflict returns HTTP 409 and a Russian explanation. Status,
reviewer, timestamps, participant snapshot, seed metadata and programme are not
written on rejection. Repeated blocked requests remain blocked without audit
mutation. REJECTED/NEEDS_CORRECTION for still-SUBMITTED unadmitted registrations
remain available; the change does not allow removal of already confirmed entries.
The UI is not changed and may still offer a button that the server now rejects.

No schema migration or production data backfill is included. Existing finalized
results, confirmed registrations, operator assignments and demo data are not
rewritten. There is no public bypass or reset endpoint.

## Tests

8 new unit methods cover the lock order, refreshing stale state, actor checks,
revoked event permission, immutable failure, admin restrictions and rejection.
11 new integration methods use real admission HTTP requests and PostgreSQL,
including versioned/legacy state, real session generation and three races:

- single generation holds its locks first -> confirmation waits and returns 409;
- session generation holds its locks first -> confirmation waits and returns 409;
- confirmation holds its locks first -> generation waits and includes that entry.

Race tests check pg_blocking_pids and bounded deadlines instead of relying only on
Thread.sleep. Existing generation and admission code runs in separate worker
transactions with a deliberate outer transaction retaining the winner's locks.
These tests have been generated, not executed in the patch-building environment.

## Historical algorithm fixtures (test sources only)

Results/category/finalization tests previously used public confirmation AFTER
creating heats, to build algorithm inputs. That is no longer a legal admission
workflow. They now use a clearly labelled test-only SQL helper for historical
confirmed pools. The original result/finalization assertions are retained. No
constraint/trigger is disabled. The helper is not in src/main and production code
must never import it. NF-01 integration tests do not use the SQL confirmation helper.

The regeneration changed-pool test explicitly names a historical fixture. The
explicit-launch research addition scenario uses that mode only for additions
AFTER generation and records additionFixtureMode in its manifest. Baseline
preparation still uses public admission. This is algorithm-input compatibility,
not evidence that late public admission is supported. No old experiment files
or measurements are overwritten. Record commit/provenance before new experiments.

## Verification

First run ReviewRegistrationAccessTest, RegistrationProtocolGuardServiceTest and
the architecture tests. Next run RegistrationConfirmationProtocolIntegrationTest,
then the full backend suite. Do not claim NF-01 closed based only on generated
code or mocked tests. Java/Maven/PostgreSQL were unavailable to the patch builder.
