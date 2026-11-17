# TIME input and exact future ranking

Scope: strictly positive ordinary TIME results; signed/zero POINTS remain valid.
DNS/DNF/DSQ retain their existing behavior: no ordinary numeric value is required.
The RecordResultUseCase validates before loading/mutating the stored result.
Existing API trimming is preserved. Values are not rounded or rewritten.

The current CreateRegistrationRequest already validated canonical seedTimeSeconds
as positive with at most two decimal places. The patch shares that rule across
the DTO, direct service invocation, resubmission of stored metadata, and protocol
preparation. Missing/null remains NT; zero is never converted into NT. Custom
non-time seed metric keys are not unconditionally restricted to positive values.
A malformed old seed is rejected, not silently repaired. The resubmission DTO
currently has no seed-time editing field: this patch does not pretend to add that
workflow. Existing invalid data requires an explicit correction procedure.

common::numeric is an explicit named interface; identity dependencies are not
expanded. No previously applied migration is changed. V036 adds a typed exact
function delegating to the existing numeric parser from V031, with a positive
TIME check. Future SQL ranking uses it rather than the double-precision V024
parser. V024 remains unchanged for historical/research compatibility.

No old result, final place, snapshot or finalized protocol is rewritten by the
installer or V036. Invalid stored TIME rows prevent future ranking atomically.
Published/finalized unit protections remain in the existing use cases/database.
The public-standings parser and stored historical snapshots are not retroactively
changed. Closing all public-read legacy-data issues is not claimed.

Added checks include Java parsing, seed validation, direct service validation,
preparation/resubmission refusal, HTTP write refusal without partial updates,
negative and zero POINTS, Java/SQL representative parity, and 186,000 clock versus
decimal SQL comparisons. Existing malformed-stored-data rollback coverage is
retained using a deliberate SQL fixture: invalid new API writes now fail earlier.

No Java, Maven or PostgreSQL execution was available when generating this patch.
Run selected unit and integration tests, then the full backend suite. Record the
new commit hash. The production ranking implementation changed, so old ranking
experiment timings must not be presented as measurements of this new commit.

NF-01 (late confirmation) is intentionally a separate patch; this patch does not
claim to prevent confirmation after a programme has been generated.
