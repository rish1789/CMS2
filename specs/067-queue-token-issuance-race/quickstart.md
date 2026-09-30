# Quickstart: Verifying 067

## Prerequisites

- Docker Desktop running (Testcontainers). On this Windows machine, set the docker-java API version for the run:
  ```powershell
  ${env:api.version} = "1.44"
  ```
- Run from `backend/`.

## 1. Red before the fix (test-first)

Run the new and tightened tests against unchanged code. Each should fail for the stated reason:

```powershell
.\gradlew.bat test --tests "*QueueSlotIssuanceConcurrencyTest" --tests "*QueueTokenConcurrentBookingTest" --tests "*WalkInConcurrentRegistrationTest" --tests "*QueueBookingFailureLeavesNoTokenTest" --tests "*TokenIssuanceLockTimeoutTest"
```

| Test | Expected red today |
|---|---|
| `QueueSlotIssuanceConcurrencyTest` (10 repeats × 20) | a `TokenIssuanceFailedException` in at least one repeat |
| `WalkInConcurrentRegistrationTest` | some registrations fail on the duplicate-token collision |
| `QueueTokenConcurrentBookingTest` (patient + staff mixed) | occasional 503, or tokens not exactly 1..N |
| `QueueBookingFailureLeavesNoTokenTest` | a `BOOKED` token with no booking remains |
| `TokenIssuanceLockTimeoutTest` | no bound exists yet, so the waiter is not refused within the bound |

The test names above are the plan's proposals; `/speckit-tasks` fixes the final names.

## 2. Green after the fix

Run the same command. All pass.

Then run the affected packages in full, including existing refusal tests (SC-005):

```powershell
.\gradlew.bat test --tests "com.cms.scheduling.*" --tests "com.cms.booking.*" --tests "com.cms.protection.*" --tests "com.cms.patient.*"
```

Finally, the full backend suite (`.\gradlew.bat test`) and CI on the PR.

## 3. Check an existing database for pre-existing orphan tokens (read-only)

This feature does not clean up orphans created before it (data-model.md). To see whether any exist in a given database:

```sql
SELECT s.session_id, s.token_number, s.status
FROM slot s
LEFT JOIN booking b ON b.slot_id = s.id
WHERE s.token_number IS NOT NULL AND b.id IS NULL;
```

Any rows found need a separate, reviewed decision. Do not delete them as part of this feature.
