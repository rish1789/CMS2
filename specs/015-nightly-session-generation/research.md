# Research: Nightly Rolling Session Generation (15-Day Horizon)

## Decision: Per-schedule transaction isolation + a DB unique constraint, not per-row SAVEPOINT recovery

**Rationale**: A batch loop inserting many rows inside one Postgres transaction has a sharp failure mode: a single constraint violation aborts the *entire* transaction (Postgres refuses further statements until rollback), so a naive "loop and catch per insert" design would actually lose already-inserted rows in the same transaction on any conflict, not just skip the conflicting one. True per-row recovery would need JDBC SAVEPOINTs (`Propagation.NESTED`), which this codebase has no existing precedent for anywhere. Given the realistic concurrency profile — a nightly cron plus an occasional Super Admin manual re-trigger, never expected to run for the *same* Schedule at the *same* instant — the proportionate design (Constitution Principle II) is: generate each Schedule's Sessions in its own transaction (`generateForSchedule`, default `@Transactional`, called from a plain, non-transactional `generate()` loop), so a failure processing one Schedule can never roll back another Schedule's already-committed work in the same run. Within one Schedule's transaction, a pre-check query (already-generated dates) makes a genuine constraint violation vanishingly rare in normal operation; if a true race does occur, that one Schedule's whole transaction fails cleanly and can simply be retried (the nightly job runs again the next night regardless; a manual re-trigger can be called again) — an accepted, explicit trade-off, not a silently swallowed edge case.

**Alternatives considered**: SAVEPOINT-based per-row recovery — rejected as complexity with no precedent in this codebase, disproportionate to a race window this small a system realistically never hits (Principle II). One single transaction for the entire run across all Schedules — rejected; it would mean one Schedule's rare conflict could roll back every other Schedule's Sessions generated in the same run, which is a worse failure mode for no benefit.

## Decision: A pre-check query (existing Session dates for the schedule) drives which dates to attempt, with the unique constraint as the actual guarantee

**Rationale**: `SessionRepository.findBySchedule_IdAndSessionDateIn(scheduleId, candidateDates)` (or an equivalent existing-dates lookup) lets `generateForSchedule` skip dates it already knows about, making a normal repeated run (the common case: nightly job runs every night, most dates were already generated on a prior run) do zero wasted insert attempts — not just correct, but efficient. The DB constraint (`uq_session_schedule_date`) remains the actual, load-bearing correctness guarantee (Constitution IV) — the pre-check is a performance/idempotency optimization on top of it, not a substitute for it.

**Alternatives considered**: Relying solely on catching the constraint violation with no pre-check — rejected; every normal nightly run would then attempt to re-insert dates 1–14 (already generated on the prior night) and catch-and-discard 14 failures, needlessly aborting/retrying transactions for the overwhelmingly common case instead of the rare one.

## Decision: `Session` snapshots `mode`/`startTime`/`endTime`/`slotIntervalMinutes` from `Schedule` at generation time

**Rationale**: Spec FR-003/Scope Decisions — this is what makes "generated Sessions are immune to later edits of the Recurring Schedule" true structurally, without this feature needing to build 014's actual edit-rejection logic. `Session` also denormalizes `Clinic`/`DoctorProfile` (copied from `Schedule.clinic`/`Schedule.doctorProfile` at generation time) for the same reason and for query convenience — a future booking feature (016/017/018) reading a Session shouldn't need to join through to `Schedule` for the doctor/clinic it's already scoped to.

**Alternatives considered**: `Session` holding only a `Schedule` FK and deriving mode/time/clinic/doctor by live join — rejected; it would make a future Schedule edit (014) retroactively change what an already-generated, possibly-already-booked-against Session reports, which is exactly the invariant the source business rules rule out.

## Decision: The manual-trigger endpoint reuses the existing `/api/v1/admin/**` chain unchanged

**Rationale**: `SuperAdminSecurityConfig` (003) already applies `anyRequest().authenticated()` under `/api/v1/admin/**` with HTTP Basic Auth against the single configured Super Admin identity — adding `POST /api/v1/admin/sessions/generate` under that same path prefix requires zero security-config changes, exactly mirroring how `ClinicVerificationController`/`DoctorVerificationController` already sit behind it.

**Alternatives considered**: A new, separate security chain — rejected; there is no reason to duplicate an existing, correctly-scoped chain for a path that already falls under it.

## Decision: A real `@Scheduled` cron trigger, new `@EnableScheduling` config

**Rationale**: FR-006 requires genuine nightly automatic execution, not just an on-demand capability nobody calls automatically. No `@EnableScheduling` exists anywhere in this codebase yet, so this feature adds it via a small, scoped `SessionGenerationSchedulingConfig` in `com.cms.scheduling` (not on the shared `CmsApplication` class, keeping the change local to the module that needs it) plus a `NightlySessionGenerationTrigger` `@Component` with `@Scheduled(cron = "0 0 2 * * *")` (2:00 AM server time — an implementation default per spec Assumptions, no business significance to the specific hour) calling `sessionGenerationService.generate(LocalDate.now())`.

**Alternatives considered**: An external cron/ops-triggered call to the manual endpoint instead of an in-process `@Scheduled` job — rejected; nothing in this project's infrastructure (no external scheduler, no ops runbook) exists to drive that, and Spring's built-in `@Scheduled` is the standard, zero-new-dependency way to satisfy "runs nightly... no manual intervention required" for a single-instance Spring Boot application like this one.
