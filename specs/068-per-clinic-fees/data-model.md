# Data Model: Per-Clinic Fees (068)

## New tables (V42)

### `clinic_doctor_fee`: a doctor's default fee at one clinic

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | `gen_random_uuid()` |
| `clinic_id` | UUID NOT NULL → `clinic(id)` | |
| `doctor_profile_id` | UUID NOT NULL → `doctor_profile(id)` | |
| `amount` | NUMERIC(10,2) NOT NULL | `CHECK (amount >= 0)` |
| `updated_at` | TIMESTAMPTZ NOT NULL DEFAULT now() | |
| `updated_by_account_id` | UUID NULL → `account(id)` | Null for rows created by the V43 copy |

**Unique key:** `uq_clinic_doctor_fee (clinic_id, doctor_profile_id)`, which enforces FR-001.

### `clinic_appointment_type_price`: one appointment type's price at one clinic

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `clinic_id` | UUID NOT NULL → `clinic(id)` | |
| `appointment_type_id` | UUID NOT NULL → `appointment_type(id)` | |
| `amount` | NUMERIC(10,2) NOT NULL | `CHECK (amount >= 0)` |
| `updated_at` | TIMESTAMPTZ NOT NULL DEFAULT now() | |
| `updated_by_account_id` | UUID NULL → `account(id)` | |

**Unique key:** `uq_clinic_appointment_type_price (clinic_id, appointment_type_id)`, which enforces FR-002.

## Copy (V43, insert-only)

- **Default fees:** for each `doctor_default_fee` × each **active Doctor** role assignment of that doctor's account → `clinic_doctor_fee(clinic, doctor, amount)`.
- **Type prices:** for each `appointment_type` with `fee_override IS NOT NULL` × each active Doctor role assignment of its doctor → `clinic_appointment_type_price(clinic, type, fee_override)`.
- **Result:** resolution for every (doctor, active clinic, type) is unchanged (SC-003).

## Unchanged, but no longer read for prices (FR-012)

- `doctor_default_fee` and `appointment_type.fee_override` keep their data, which serves as an audit trail of the pre-068 values.
- No code reads them for resolution, readiness or display after this feature.
- Dropping them would need a later, separate migration and is not part of 068.

## Resolution (per booking, clinic C, doctor D, type T)

```text
clinic_appointment_type_price(C, T)  → if present: that amount
else clinic_doctor_fee(C, D)         → if present: that amount
else                                  → NoFeeConfiguredException (booking blocked)
```

There is no fallback to another clinic, or to the doctor-wide tables (FR-004).

## Readiness at clinic C, for doctor D (FR-010)

- `hasAppointmentTypes`: the doctor has at least one type, as today.
- `hasDefaultFee`: `clinic_doctor_fee(C, D)` exists.
- `hasAppointmentTypeMissingFeeOverride`: some type of D has no `clinic_appointment_type_price(C, ·)`.
- `bookingReady` = `hasAppointmentTypes && (hasDefaultFee || !hasAppointmentTypeMissingFeeOverride)`. This is the same formula as today, now evaluated per clinic.
