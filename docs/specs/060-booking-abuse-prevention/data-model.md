# Data Model: Booking Protection / Appointment Abuse Prevention

## New entities

### BookingAttemptLog *(owned by `booking`)*

One row per booking attempt by a self-service patient, whatever the outcome (spec.md FR-012).

| Field | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `patientAccountId` | UUID, not null | The attempting patient's account — cross-clinic identity, matches `PatientAccount.id`. Indexed (`patient_account_id`, `attempted_at`) for the rate-limit window query. |
| `clinicId` | UUID, not null | The clinic the attempt was made at — used to attribute a rate-limit violation to a clinic for FR-022's "attributable to whichever clinic" rule. |
| `attemptedAt` | Instant, not null | Server clock at the moment the attempt was received, before any check runs. |
| `outcome` | enum, not null | `SUCCESS`, `RATE_LIMITED`, `LIMIT_REACHED`, `OTHER_FAILURE` (slot already taken, slot in the past, validation error — every existing failure mode this feature's checks don't own). |
| `bookingId` | UUID, nullable | Set only when `outcome = SUCCESS`, FK-style reference to the created `Booking` — lets a reviewer trace a successful attempt to its resulting appointment. |

**Validation rules**: `patientAccountId` and `clinicId` required on every row (SEC-004 — minimum
necessary data, no unrelated personal data). `attemptedAt` is server-assigned, never client-supplied.

**Retention**: rows older than the longest currently-configured window across the rate limiter and
every flagging signal that reads this table are eligible to age out (spec.md SEC-005) — sized in
`tasks.md` as a scheduled cleanup alongside `FlagDetectionService`'s own sweep, not a new mechanism.

**Lifecycle**: append-only. No update, no delete outside the retention sweep above.

---

### ClinicBookingLimitOverride *(owned by `booking`)*

The optional, per-clinic supplementary cap (spec.md FR-004, US5). At most one row per clinic.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `clinicId` | UUID, not null, unique | One override per clinic. |
| `maxActiveAppointments` | int, not null | Must be a positive integer. BR-005: enforced at write time to be `<= ` the current global cap (`ProtectionSetting`'s `global-active-appointment-limit`) — a clinic cannot use its override to be *more* permissive than the platform default. |
| `updatedAt` / `updatedBy` | Instant / String, not null | Paired fields giving the *current* state a fast, single-row read (used by every booking-time check) — full change history is `ClinicBookingLimitOverrideChangeLog` below (AUD-003), not this row. |

**Validation rules**: absence of a row for a clinic means "no override, global limit only" (FR-004's
"optional" — not a zero-row-means-zero-limit trap). `maxActiveAppointments <= ` the global cap at
write time (BR-005) — re-validated at write time, not just at creation, since the global cap can
change after an override was set.

**Lifecycle**: created/updated by a ClinicAdmin (FR-028); deleted (or nulled) to return to
"global limit only." Every create/update/delete also appends a row to `ClinicBookingLimitOverrideChangeLog`.

---

### ClinicBookingLimitOverrideChangeLog *(owned by `booking`)*

Append-only audit history for `ClinicBookingLimitOverride` (spec.md AUD-003). Lives in `booking`,
the same module that owns `ClinicBookingLimitOverride` itself, so writing to it never crosses the
`booking`/`protection` module boundary (research.md Decision 3's one-way dependency rule — see
Decision 9 below for why this is a second, `booking`-owned log rather than one shared table with
`protection`'s equivalent).

| Field | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `clinicId` | UUID, not null | |
| `previousMaxActiveAppointments` | int, nullable | Null when this row records the override's first-ever creation (nothing to have been before). |
| `newMaxActiveAppointments` | int, nullable | Null when this row records the override being removed (returned to global-limit-only). |
| `changedAt` / `changedBy` | Instant / String, not null | |

**Validation rules**: append-only — one row per create/update/delete of `ClinicBookingLimitOverride`, written in the same transaction as the change it records. Never updated or deleted itself.

**Lifecycle**: grows monotonically; no retention/aging-out rule (unlike `BookingAttemptLog`) — an
administrative audit trail for security-relevant thresholds is kept indefinitely, matching the
reasoning in AUD-002/AUD-003.

---

### ProtectionSetting *(owned by `protection`)*

Named, Super-Admin-editable, system-wide values (spec.md FR-026, FR-027, FR-029, FR-030). One row
per name; absence of a row means "use the documented default" (spec.md Assumptions).

| Field | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `name` | String, not null, unique | One of a fixed, known set — see table below. |
| `value` | String, not null | Stored as text, parsed by the reading service per the setting's own type (int, duration, boolean) — avoids one column per possible value type for these 16 rows. |
| `updatedAt` / `updatedBy` | Instant / String, not null | Paired fields giving the *current* state a fast, single-row read (used by every booking-time and flag-detection check) — full change history is `ProtectionSettingChangeLog` below (AUD-002), not this row. |

**Known setting names and documented defaults** (from spec.md Assumptions — every value below is
Super-Admin-editable, none are fixed):

| Name | Default | Used by |
|---|---|---|
| `booking-limit.global-max-active` | `15` | Booking-limit check (FR-001) |
| `booking-limit.enabled` | `true` | Booking-limit check on/off (FR-027) |
| `rate-limit.max-attempts` | `8` | Rate-limit check (FR-007) |
| `rate-limit.window-minutes` | `10` | Rate-limit check (FR-007) |
| `rate-limit.cooldown-minutes` | `15` | Rate-limit check (FR-010) |
| `rate-limit.enabled` | `true` | Rate-limit check on/off (FR-027) |
| `flagging.enabled` | `true` | Flag detection on/off (FR-027) — a single toggle for all five signals; per-signal enable/disable was not requested by spec.md and is not built (YAGNI) |
| `flagging.high-attempt-volume.threshold` / `.window-minutes` | `10` / `60` | FR-016 |
| `flagging.repeated-cancellations.threshold` / `.window-days` | `4` / `30` | FR-017 |
| `flagging.repeated-no-shows.threshold` / `.window-days` | `3` / `90` | FR-018 |
| `flagging.overlapping-appointments.threshold` | `3` | FR-019 |
| `flagging.repeated-rate-limit-violations.threshold` / `.window-hours` | `3` / `24` | FR-020 |

**Validation rules**: `name` must be one of the fixed known set — a write to an unrecognized name is
rejected (FR-026 scopes this to *exactly* the values this feature needs, not an open-ended
key/value store). `value` is validated against its setting's expected type/range before being
stored (e.g. a threshold must be a positive integer).

**Lifecycle**: upserted by a Super Admin (FR-026); reading code always goes through
`ProtectionSettingService`, never the repository directly, so the "no row = default" fallback is
enforced in exactly one place. Every upsert also appends a row to `ProtectionSettingChangeLog`.

---

### ProtectionSettingChangeLog *(owned by `protection`)*

Append-only audit history for `ProtectionSetting` (spec.md AUD-002). Lives in `protection`, the
same module that owns `ProtectionSetting` itself — see Decision 9 in research.md for why this is a
second, `protection`-owned log rather than one shared table with `ClinicBookingLimitOverrideChangeLog`.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `settingName` | String, not null | Matches a `ProtectionSetting.name` value. |
| `previousValue` | String, nullable | Null when this row records a setting's first-ever write (no prior row existed — the "previous" state was the documented default, not a stored value). |
| `newValue` | String, not null | |
| `changedAt` / `changedBy` | Instant / String, not null | |

**Validation rules**: append-only — one row per `ProtectionSetting` upsert, written in the same transaction as the change it records. Never updated or deleted itself.

**Lifecycle**: grows monotonically; no retention/aging-out rule, same reasoning as
`ClinicBookingLimitOverrideChangeLog` above.

---

### SuspiciousActivityFlag *(owned by `protection`)*

One row per triggered signal episode (spec.md FR-014, and the Clarifications session's dedup rule).

| Field | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `patientAccountId` | UUID, not null | |
| `clinicId` | UUID, nullable | Null represents the single documented cross-clinic case (spec.md FR-022 — the global-limit-reached fact); every other signal type always sets a clinic. |
| `signalType` | enum, not null | `HIGH_ATTEMPT_VOLUME`, `REPEATED_CANCELLATIONS`, `REPEATED_NO_SHOWS`, `OVERLAPPING_APPOINTMENTS`, `REPEATED_RATE_LIMIT_VIOLATIONS`. |
| `reason` | String, not null | Human-readable, generated by `FlagDetectionService` at creation time (e.g. "4 cancellations in the last 30 days"), not recomputed later — a resolved flag's reason reflects the pattern *as it was detected*. |
| `detectedAt` | Instant, not null | |
| `status` | enum, not null | `OUTSTANDING`, `RESOLVED`. |
| `resolvedAt` / `resolvedBy` | Instant / String, nullable | Set together when `status` moves to `RESOLVED` (FR-023, AUD-001) — same paired-field convention as `DoctorProfile.reject()`. |

**Validation rules / dedup (FR-014, Clarifications)**: `FlagDetectionService` MUST NOT create a new
row for a `(patientAccountId, clinicId, signalType)` combination that already has an `OUTSTANDING`
row — enforced with a partial unique index (`UNIQUE (patient_account_id, clinic_id, signal_type)
WHERE status = 'OUTSTANDING'`) so the guarantee holds even if the sweep somehow runs the same check
twice concurrently, not only in application code.

**State transitions**: `OUTSTANDING → RESOLVED` only, via a ClinicAdmin action (FR-023). One-way —
matches `BookingStatus`'s own established one-way-transition convention in this codebase. A resolved
flag is never reopened; if the same condition re-triggers later, a *new* row is created (the dedup
rule above only suppresses duplicates of a still-*outstanding* flag).

**Lifecycle**: created by `FlagDetectionService`'s sweep; resolved by a ClinicAdmin; never deleted
(FR-024 — remains visible in history).

---

## Existing entities referenced, unchanged

- **PatientAccount** *(`patient.account`)* — the identity every new entity above keys on
  (`patientAccountId`). No new field.
- **Booking** *(`booking`)* — read for active-appointment counting (`status = ACTIVE`) and for the
  repeated-cancellation signal (`status = CANCELLED`, ordered by cancellation time). No new field, no
  new status value (spec.md's confirmed-existing-behavior section is explicit: still exactly
  `ACTIVE`/`CANCELLED`).
- **Slot** *(`scheduling`)* — read for the repeated-no-show signal (`status = NO_SHOW`, joined
  through `Booking`/`Patient`). No new field, no change to `NoShowDetectionService`'s own sweep.
- **Clinic** *(`identity.clinic`)* — referenced by `clinicId` foreign keys above; not modified.
- **RoleAssignment** *(`identity.account`)* — read (not modified) by the new ClinicAdmin-only
  authorization checks (research.md Decision 7).

## Entity relationship summary

```text
PatientAccount (existing, global)
  ├─ 1:N → Booking (existing)
  └─ 1:N → BookingAttemptLog (new)
              └─ N:1 → Clinic (existing, via clinicId)

Clinic (existing)
  ├─ 0:1 → ClinicBookingLimitOverride (new)
  │           └─ 1:N → ClinicBookingLimitOverrideChangeLog (new, append-only)
  └─ 1:N → SuspiciousActivityFlag (new, nullable clinicId for the one cross-clinic case)

SuspiciousActivityFlag (new)
  └─ N:1 → PatientAccount (existing, via patientAccountId)

ProtectionSetting (new)
  ├─ standalone, no foreign keys; read by both `booking` (synchronous checks) and `protection`
  │   (FlagDetectionService), per research.md Decision 5.
  └─ 1:N → ProtectionSettingChangeLog (new, append-only, keyed by settingName not a FK)
```
