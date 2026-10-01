# 04 — Data Model

Labels: [RUNTIME] = observed while running · [CODE] = read in source · [INFERRED] = drawn from code or docs, not proven · [UNKNOWN] = could not be determined.

**Source of truth:** Flyway migrations `backend/src/main/resources/db/migration/V1`–`V40` [CODE].

- Hibernate runs with `ddl-auto: validate`, so the entities must match these migrations.
- The backend started successfully against the local database, which means Flyway applied all migrations and Hibernate validation passed [RUNTIME].
- All primary keys are `UUID DEFAULT gen_random_uuid()`, except `inbox_item.id`, which has no default and is assigned by the application.

## 1. Tables

### Identity and tenancy

| Table | Key columns | FKs | Constraints and indexes | Notes |
|---|---|---|---|---|
| `clinic` (V1, V28, V30) | name, address, contact_email, contact_mobile, `verified` bool, `rejected` bool, rejection_reason (CHECK enum), rejection_detail, `rejected_at` **TIMESTAMP (no tz)**, `rejected_by` VARCHAR, city, created_at | — | idx on (verified, rejected), (rejected, rejected_at), created_at, lower(name), lower(city) | Lifecycle is two booleans (see §5) |
| `account` (V1) | name, email (UNIQUE), password_hash, staff_code (UNIQUE), mobile, `active`, created_at | — | idx lower(name) | Staff login identity. `active` is never read by the auth filter [CODE]. |
| `role_assignment` (V1, V25, V26) | account_id, clinic_id, role CHECK ('ClinicAdmin','Doctor','Operations'), active, deactivation_reason CHECK, created_at | → account, → clinic | idx account_id, clinic_id; **partial UNIQUE** (account_id, clinic_id, role) WHERE active | The tenancy join |
| `doctor_profile` (V3, V4, V28) | account_id (UNIQUE), specialization, license_number (UNIQUE), experience_years, license_verified, visible, rejected*, created_at | → account | idx (license_verified, rejected), (rejected, rejected_at), created_at | **One global profile per doctor**, not per clinic |
| `patient_account` (V2, V6) | email (UNIQUE), password_hash, mobile, notification_opt_in, sms_opt_in, push_opt_in, active, created_at | — | — | Global patient identity. The three opt-in flags have no API to change them [CODE]. |
| `patient` (V5, V22, V39) | clinic_id, patient_account_id (nullable), name, phone, email, anonymized_at, created_at | → clinic, → patient_account | **partial UNIQUE** (clinic_id, patient_account_id) WHERE account NOT NULL; **partial UNIQUE** (clinic_id, phone) WHERE account IS NULL | Per-clinic record. There is **no index on `patient_account_id` alone** or on `(clinic_id, name)` used by search [INFERRED from migrations]. |

### Scheduling

| Table | Key columns | FKs | Constraints and indexes | Notes |
|---|---|---|---|---|
| `schedule` (V7, V33) | doctor_profile_id, clinic_id, start_time, end_time, mode VARCHAR, slot_interval_minutes, break_start/end, created_at | → doctor_profile, → clinic | **no index** on doctor_profile_id or clinic_id | No CHECK on `mode`, no CHECK start < end (validated in the service) |
| `schedule_day` (V7) | (schedule_id, day_of_week) PK | → schedule | — | day_of_week is VARCHAR with no CHECK |
| `session` (V8, V15, V33, V34) | schedule_id (**nullable** since V34), clinic_id, doctor_profile_id, session_date, mode, start/end, slot_interval_minutes, delay_minutes, break_*, created_at | → schedule, → clinic, → doctor_profile | UNIQUE (schedule_id, session_date); idx (clinic_id, session_date) | **No status column** (no cancelled or closed state). No index on doctor_profile_id (used by the multi-clinic overlap and live status queries). |
| `slot` (V10, V11, V13, V35, V39) | session_id, start_time / end_time (nullable since V11), token_number, `status` VARCHAR, on_hold, appeared_at, completed_at, created_at | → session | idx session_id; partial UNIQUE (session_id, token_number) | **No CHECK on status.** No UNIQUE (session_id, start_time) for timed slots. `on_hold` is never set [CODE]. Carries the **visit state**. |

### Booking

| Table | Key columns | FKs | Constraints and indexes | Notes |
|---|---|---|---|---|
| `appointment_type` (V9) | doctor_profile_id, name, fee_override NUMERIC(10,2) | → doctor_profile | **no index** on doctor_profile_id | Per doctor, global across clinics |
| `doctor_default_fee` (V9) | doctor_profile_id (UNIQUE), amount, updated_at | → doctor_profile | — | Per doctor, global |
| `booking` (V12, V14, V16, V24, V31, V32, V38, V39) | slot_id, patient_id, appointment_type_id, locked_fee, payment_status, booked_by_account_id, booked_by_patient_account_id, override_reason, status (default 'ACTIVE'), cancellation_reason, cancellation_reason_detail, cancelled_at, source (default 'SCHEDULED'), visit_reason, visit_reason_detail, created_at | → slot, → patient, → appointment_type, → account, → patient_account | **partial UNIQUE** (slot_id) WHERE status <> 'CANCELLED'; CHECK exactly one booked_by_*; CHECK visit_reason OTHER ⇒ detail | **No index on `patient_id`** (patient history, active-booking cap), none on `booked_by_patient_account_id`. No CHECK on status, payment_status, source or cancellation_reason. No `cancelled_by`. |
| `booking_attempt_log` (V36) | patient_account_id, clinic_id, attempted_at, outcome, booking_id | → patient_account, → clinic, → booking | idx (patient_account_id, attempted_at) | |
| `clinic_booking_limit_override` (+ `_change_log`) (V36) | clinic_id UNIQUE, max_active_appointments, updated_at, updated_by VARCHAR | → clinic | idx on the change log (clinic_id, changed_at) | The actor is stored as free text |

### Waitlist, clinical, inbox, notifications, protection

| Table | Key columns | FKs | Constraints and indexes | Notes |
|---|---|---|---|---|
| `waitlist_entry` (V17, V18) | clinic_id, patient_account_id, doctor_profile_id (nullable), specialization (nullable), status, joined_at, offered_at, offer_expires_at, offered_slot_id | → clinic, → patient_account, → doctor_profile, → slot | **none** | No CHECK that a doctor or specialization is present (validated in the service). No index for the per-minute expiry sweep (status, offer_expires_at) or for matching (clinic_id, status, joined_at) [INFERRED from migrations]. |
| `consultation_note` (V19) | booking_id **UNIQUE**, doctor_profile_id, content, created_at | → booking, → doctor_profile | unique booking_id | Immutability is application-level only. There is no DB trigger or permission preventing UPDATE [CODE]. |
| `prescription` / `prescription_item` (V20) | booking_id, doctor_profile_id / prescription_id, medication_name, dosage, frequency, duration, instructions | → booking / → prescription | **no index** on booking_id or prescription_id | Same immutability note |
| `external_record_reference` (V21) | booking_id, doctor_profile_id, record_type, source_provider, record_date, summary | → booking, → doctor_profile | no index on booking_id | |
| `inbox_item` (V23) | clinic_id, item_type, booking_id (ON DELETE CASCADE), waitlist_entry_id (ON DELETE CASCADE), doctor_name (denormalised), cancelled_booking_count, status default 'UNCLAIMED', `claimed_by_account_id` (**no FK**), created_at | → clinic, → booking, → waitlist_entry | idx (clinic_id, status) | The only ON DELETE CASCADE in the schema |
| `notification_event` (V6) | patient_account_id, event_type, payload TEXT (JSON as a string), push_eligible, sms_eligible, expires_at, status, created_at | → patient_account | idx (status, expires_at) | The payload is built by string concatenation in services (for example `SessionCancellationService`: `"{\"bookingId\":\"" + id + "\"}"`) [CODE] |
| `protection_setting` (+ change log) (V37) | name UNIQUE, value VARCHAR, updated_by VARCHAR | — | idx on the change log (setting_name, changed_at) | Values are untyped strings, validated in `ProtectionSettingService` |
| `suspicious_activity_flag` (V37) | patient_account_id, clinic_id, signal_type, reason, detected_at, status, resolved_at, resolved_by | → patient_account, → clinic | partial UNIQUE outstanding (patient, clinic, signal); idx (clinic_id, status) | |

## 2. Enumerations (Java enums persisted as VARCHAR) [CODE]

| Enum | Values | DB CHECK? |
|---|---|---|
| `SlotStatus` | OPEN, BOOKED, NO_SHOW, APPEARED, COMPLETED | no |
| `ScheduleMode` | FIXED_TIME, QUEUE | no |
| `BookingStatus` | ACTIVE, CANCELLED | no |
| `BookingSource` | SCHEDULED, WALK_IN | no |
| `PaymentStatus` | PENDING, PAID (never assigned) | no |
| `VisitReason` | FEVER_COLD_COUGH, PAIN, FOLLOW_UP, TEST_REPORT_REVIEW, PRESCRIPTION_REFILL, INJURY, GENERAL_CHECKUP, OTHER | partial (OTHER ⇒ detail) |
| `BookingCancellationReason` | SCHEDULE_CONFLICT, FEELING_BETTER, FOUND_ANOTHER_PROVIDER, PERSONAL_EMERGENCY, OTHER, CLINIC_REJECTED | no |
| `BookingAttemptOutcome` | SUCCESS, RATE_LIMITED, LIMIT_REACHED, OTHER_FAILURE | no |
| `WaitlistEntryStatus` | WAITING, OFFERED, CLAIMED, EXPIRED | no |
| `InboxItemType` / `InboxItemStatus` | WALK_IN, WAITLIST_OFFER, DEVERIFICATION_CASCADE / UNCLAIMED, CLAIMED, RESOLVED | no |
| `NotificationEventStatus` | PENDING, ACTIONED, EXPIRED | no |
| `SuspiciousActivitySignalType` | HIGH_ATTEMPT_VOLUME, REPEATED_CANCELLATIONS, REPEATED_NO_SHOWS, OVERLAPPING_APPOINTMENTS, REPEATED_RATE_LIMIT_VIOLATIONS | no |
| role (`role_assignment.role`), deactivation_reason, rejection_reason | as listed in §1 | **yes** |

## 3. Relationships that actually exist

```
patient_account 1 ── 0..* patient (one per clinic) ── * booking
clinic 1 ── * patient
clinic 1 ── * role_assignment * ── 1 account 1 ── 0..1 doctor_profile
doctor_profile 1 ── * schedule (per clinic) 1 ── * session (per date) 1 ── * slot 1 ── 0..1 ACTIVE booking (0..* CANCELLED)
booking * ── 1 appointment_type * ── 1 doctor_profile        (the booking's doctor is reached via slot→session, NOT via appointment_type)
booking 1 ── 0..1 consultation_note, 0..* prescription(1 ── 1..* prescription_item), 0..* external_record_reference
waitlist_entry * ── 1 patient_account, 1 clinic, 0..1 doctor_profile, 0..1 slot (offered)
inbox_item * ── 1 clinic, 0..1 booking, 0..1 waitlist_entry
```

The chain the audit brief asked about maps like this:

| Brief term | Actual path |
|---|---|
| Patient → Appointment | `patient` → `booking` (booking.patient_id) |
| Appointment → Doctor | `booking` → `slot` → `session` → `doctor_profile`. There is no direct `booking.doctor_id`. |
| Appointment → Queue | Only when `session.mode = QUEUE`; the "queue" is the token-numbered slots of that session. There is no queue table. |
| → Visit | **No visit table.** The visit state is `slot.status` plus `slot.appeared_at` / `completed_at`, and the clinical documents referencing `booking_id`. |

## 4. Important business rules enforced in the database

1. At most one non-cancelled booking per slot (`uq_booking_slot_active`). This is the double-booking guard.
2. A unique token number per session.
3. Exactly one "booked by" identity per booking (staff account XOR patient account).
4. One consultation note per booking.
5. One active role of a given type per account per clinic.
6. One linked patient record per (clinic, patient account); one unlinked record per (clinic, phone).
7. A visit reason OTHER requires detail.
8. Unique doctor licence number; unique staff email and staff code; unique patient email.

Everything else is enforced only in Java: slot state transitions, session mode, the cutoff, date checks, write-once clinical documents, and verification gates.

## 5. Suspicious duplication, missing constraints, inconsistent representation

| # | Finding | Evidence | Impact | Confidence |
|---|---|---|---|---|
| DM-01 | **Visit state stored on `slot`, appointment state on `booking`.** Cancelling resets the slot to OPEN, so an APPEARED visit that is later cancelled leaves no visit trace on the slot, and `appeared_at` / `completed_at` persist on a slot that may later belong to a different booking. | `BookingCancellationService.doCancel`; `Slot.setStatus` stamps | Reporting ("completed visits", "no-show rate") must join through the slot and is ambiguous across rebookings. | High [CODE] |
| DM-02 | **`session` has no status.** "Cancelled" is inferred only from the bookings. | V8 migration; `SessionCancellationService` | BUG-002/003/004 | High |
| DM-03 | Clinic lifecycle is two booleans (`verified`, `rejected`) with no CHECK preventing both true. Doctor profiles have the same pattern (`license_verified`, `rejected`). | V1, V28 | Invalid combinations are representable; the services guard them | Medium [CODE] |
| DM-04 | Status and enum columns have no CHECK constraints (about 12 columns in §2). | migrations | A bad write (manual SQL, a future bug) is not caught by the database | High |
| DM-05 | Mixed timestamp types: `rejected_at` is `TIMESTAMP` without time zone; everything else is `TIMESTAMPTZ`. Session and slot times are `DATE` + `TIME` with no zone, interpreted in the JVM zone. | V28; `NoShowDetectionService`, `SessionPartialCancellationService:112` | Time-zone dependence (PB-005) | High |
| DM-06 | Actors stored as free text or as FK-less UUIDs: `rejected_by`, `updated_by`, `changed_by`, `resolved_by` VARCHAR; `inbox_item.claimed_by_account_id` without an FK. | V23, V28, V36, V37 | No referential integrity; renames not reflected | Medium |
| DM-07 | Missing indexes on frequently filtered foreign keys: `booking.patient_id`, `booking.booked_by_patient_account_id`, `waitlist_entry.*`, `schedule.doctor_profile_id`, `session.doctor_profile_id`, `appointment_type.doctor_profile_id`, `prescription.booking_id`, `prescription_item.prescription_id`, `external_record_reference.booking_id`, `notification_event.patient_account_id`. | migrations (Postgres does not auto-index FKs) | Degrades with volume; also affects the per-minute scans | Medium [INFERRED] |
| DM-08 | Dead or unused columns: `slot.on_hold` (never set), `booking.payment_status` (always PENDING), `patient_account.notification_opt_in` / `sms_opt_in` / `push_opt_in` (no writer after signup). | code search | Misleading schema | High |
| DM-09 | Doctor-global `appointment_type` and `doctor_default_fee` in a multi-tenant system. | V9; `AppointmentTypeService:121` | One clinic's admin changes another clinic's fees (SEC-03). **Fixed by 068:** prices are per clinic (V42/V43); the doctor-wide tables are kept as an audit trail and are no longer read. | High |
| DM-10 | No `updated_at` on most mutable tables (booking, slot, session, schedule, waitlist_entry, inbox_item). | migrations | Weak auditability | High |
| DM-11 | Clinical immutability is application-only. | no triggers or revoke | A direct DB edit is undetectable | Medium |
| DM-12 | No UNIQUE (session_id, start_time) for timed slots. | V10 | Duplicate timed slots are possible if generation ran twice under a race (generation is guarded by `uq_session_schedule_date`, which reduces the risk) | Low [INFERRED] |
| DM-13 | `notification_event.payload` is hand-built JSON in a TEXT column. | services | No schema validation; escaping only safe because the values are UUIDs | Low |
| DM-14 | `session.schedule_id` nullable (V34) plus `uq_session_schedule_date` means Postgres treats NULLs as distinct, so multiple schedule-less sessions for the same date are allowed. | V8, V34 | Needs a product decision on what schedule-less sessions are | Low [INFERRED] |
| DM-15 | Soft deletion is inconsistent: `role_assignment.active`, `account.active`, `patient_account.active`, `patient.anonymized_at`, `clinic.rejected` + purge, and hard deletes (session, schedule, rejected clinic). | migrations and services | Different retention semantics per entity | Informational |
