# Data Model: Super Admin Clinic Verification

## No new entity

This feature introduces **no new table and no new entity**. It operates entirely on the `verified` boolean column of 001's existing `Clinic` entity (see 001's data-model.md) — reading it (for the pending list, where `verified = false`) and writing it (the toggle action).

## Super Admin (not persisted)

Per spec.md's Clarifications: a single credential pair sourced from configuration (`admin.super-admin.username` / `admin.super-admin.password`), never stored in the database, never logged. Not a JPA entity — has no table, no ID, no relationships. Authenticated per-request via HTTP Basic Auth against the configured values.

## ClinicDeVerifiedEvent (in-process event, not persisted)

A Spring `ApplicationEvent` published when `Clinic.verified` transitions `true → false`.

| Field | Type | Notes |
|---|---|---|
| `clinicId` | UUID | The clinic that was just un-verified. |
| `occurredAt` | timestamp | Event creation time. |

**Not a database table** — this is an in-process, synchronous Spring event for other beans (specifically, a future `@EventListener` in 008-deverification-cascade-auto-cancel-bookings) to react to. It is not persisted anywhere by this feature.

## State Transitions (in scope for this feature)

- `Clinic.verified`: `false → true` (verify) and `true → false` (un-verify), both idempotent — calling either action when the clinic is already in the target state is a no-op success, and specifically MUST NOT re-publish `ClinicDeVerifiedEvent` on a repeated `true → false` call when it's already `false` (FR-007).

## Out of Scope for This Data Model

- Any listener/handler for `ClinicDeVerifiedEvent` — that's 008's responsibility entirely.
- Any Super Admin database table, session, or token storage.
