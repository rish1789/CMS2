# Data Model: Patient Record Auto-Creation & Phone-Based Linking

## Patient (new entity — `com.cms.patient.record.Patient`)

The clinic-scoped clinical/visit record, first defined by this feature.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `clinic` | Clinic (FK, `clinic_id`) | `nullable=false` — every Patient is scoped to exactly one clinic (001's `Clinic`, read-only reference) |
| `patientAccount` | PatientAccount (FK, `patient_account_id`) | **nullable** — null for a walk-in record never claimed by a self-service booking; set once this feature (or a future one) links it. This feature never un-sets it once set (spec Assumptions). |
| `name` | String | `nullable=false` — supplied by the caller on creation (research.md: `PatientAccount` has no name field); never overwritten by an existing-record match |
| `phone` | String | nullable — the sole matching key (FR-008); mirrors `PatientAccount.mobile`'s optionality |
| `createdAt` | Instant | `nullable=false`, defaults to now |

### Constraints

```sql
-- FR-005(a): one account never has two Patient records at the same clinic
CREATE UNIQUE INDEX uq_patient_clinic_account
    ON patient (clinic_id, patient_account_id)
    WHERE patient_account_id IS NOT NULL;

-- FR-005(b): two unlinked (walk-in) records never share a clinic+phone
CREATE UNIQUE INDEX uq_patient_clinic_phone_unlinked
    ON patient (clinic_id, phone)
    WHERE patient_account_id IS NULL;
```

Both are partial indexes, not table-level `UNIQUE` constraints, since each only needs to hold within a subset of rows (linked vs. unlinked) — see research.md for why a single flat constraint is wrong here.

## Repository (`PatientRepository`)

- `Optional<Patient> findByClinic_IdAndPatientAccount_Id(UUID clinicId, UUID patientAccountId)` — backs FR-002 (the existing-link check, checked first).
- `Optional<Patient> findByClinic_IdAndPhoneAndPatientAccountIsNull(UUID clinicId, String phone)` — backs FR-003 (matches only an *unlinked* record — this is what implements the linked-record protection from Clarifications; a phone match on an already-linked-to-someone-else row is structurally invisible to this query).

## Service flow (`PatientLinkingService.findOrCreatePatient`)

```java
@Transactional
public Patient findOrCreatePatient(UUID patientAccountId, UUID clinicId, String name)
```

1. Load `PatientAccount` by id, or `PatientAccountNotFoundException`.
2. **FR-002**: `patientRepository.findByClinic_IdAndPatientAccount_Id(clinicId, patientAccountId)` — if present, return it. (`name` parameter is ignored on this path.)
3. **FR-003**: if `account.getMobile()` is non-blank, `patientRepository.findByClinic_IdAndPhoneAndPatientAccountIsNull(clinicId, account.getMobile())` — if present, set `patient.patientAccount = account`, save, return it. (`name` parameter is ignored on this path too — the existing record's own name is authoritative.)
4. **FR-004**: otherwise, construct `new Patient(clinic, account, name, account.getMobile())` and save.
5. On a `DataAccessException` from step 4 whose cause names `uq_patient_clinic_account` (research.md decision #4 — the only constraint *this service's own writes* can ever violate, since it never creates unlinked rows): re-run step 2's lookup and return the now-existing record. Any other `DataAccessException` propagates as an unexpected failure.

## Migration: `V5__create_patient.sql`

```sql
CREATE TABLE patient (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id           UUID NOT NULL REFERENCES clinic (id),
    patient_account_id  UUID REFERENCES patient_account (id),
    name                VARCHAR(255) NOT NULL,
    phone               VARCHAR(20),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_patient_clinic_account
    ON patient (clinic_id, patient_account_id)
    WHERE patient_account_id IS NOT NULL;

CREATE UNIQUE INDEX uq_patient_clinic_phone_unlinked
    ON patient (clinic_id, phone)
    WHERE patient_account_id IS NULL;
```
