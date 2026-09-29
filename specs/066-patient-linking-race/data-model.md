# Data Model: Patient-Linking Same-Account Race (066)

**No schema change.** No Flyway migration, and no new table, column, index or constraint.

## Entities involved (unchanged)

| Entity | Table | Role in this feature |
|---|---|---|
| Patient Account | `patient_account` | Global patient identity. Its row is now **locked** (`SELECT … FOR UPDATE`) at the start of `findOrCreatePatient`, and the lock is held until the surrounding transaction ends. This is the serialization point for same-account linking (research.md R3). |
| Patient | `patient` | Clinic-scoped record. At most one per `(clinic_id, patient_account_id)`, enforced by the existing partial unique index `uq_patient_clinic_account` (V5, 009 FR-005a). The index stays as the data-layer backstop. |

## Invariants

- **One record per account per clinic.** A Patient Account has at most one Patient record per clinic. The unique index enforces this; the application lock only prevents the conflict from being attempted.
- **Walk-in phone uniqueness is unchanged.** Unlinked (walk-in) Patient records never share a `(clinic_id, phone)` (`uq_patient_clinic_phone_unlinked`, 009 FR-005b).
- **Fixed-time booking stays all-or-nothing.** A Patient record created during a fixed-time slot booking commits or rolls back with that booking (spec FR-003).

## Lock lifecycle (per call to `findOrCreatePatient`)

```text
lock patient_account row (wait if another transaction holds it)
  → existing link?  → return it                        (009 FR-002)
  → phone match?    → link walk-in record, return it   (009 FR-003)
  → else            → insert new Patient, return it    (009 FR-004)
lock released at commit/rollback of the surrounding transaction
```

The lock is held until the end of the surrounding transaction:

| Caller | Lock held until |
|---|---|
| Fixed-time slot booking | The booking's commit or rollback. If the 060 booking-limit gate already took the same lock in that transaction, taking it again is a no-op. |
| Queue booking, or a direct call | The linking call's own commit or rollback. |
