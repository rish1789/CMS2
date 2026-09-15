# Database Schema, ORM, and Enum-Literal Audit

Exhaustive pass: all 23 Flyway migrations (`V1`–`V23`), all 19 `@Entity` classes plus the one
`@ElementCollection`, `application.yml`, and every enum literal appearing anywhere in `frontend/src`
(61 TS/TSX files) cross-checked against its Java `@Enumerated` source.

---

**[CRITICAL] - [BOOKING] - [FK_INTEGRITY_GAP]**

- Migration: `backend/src/main/resources/db/migration/V12__create_booking.sql:13` —
  `booked_by_account_id UUID NOT NULL REFERENCES account (id)`
- Entity: `backend/src/main/java/com/cms/booking/Booking.java:55-56` —
  `@Column(name = "booked_by_account_id", nullable = false) private UUID bookedByAccountId;`
  (a plain UUID field, not a JPA relation — Hibernate never validates what it actually points to)
- Faulty write sites: `backend/src/main/java/com/cms/booking/PatientBookingService.java:93` and
  `backend/src/main/java/com/cms/booking/PatientQueueBookingService.java:73`, both constructing
  `new Booking(slot, patient, appointmentType, lockedFee, patientAccountId)` where `patientAccountId`
  comes from `com.cms.patient.account.SecurityConfig.currentPatientAccountId(authentication)` —
  i.e. a `patient_account.id`.
- Contrast with the *correct* pattern used everywhere else: `StaffBookingService.java:74`,
  `StaffQueueBookingService.java:91`, `WalkInInsertionService.java:121` all pass `callerAccountId`
  sourced from `com.cms.identity.account.SecurityConfig.currentAccountId` — a genuine staff `account.id`.
- Root cause: `patient_account` and `account` are two deliberately disjoint identity systems in this
  codebase — `V2__create_patient_account.sql:1-3`'s own migration comment states this explicitly
  ("Deliberately independent of `account`/`clinic`/`role_assignment` — no foreign key, no shared
  uniqueness constraint"). `Booking.bookedByAccountId`'s single FK column was designed with only the
  staff-booking case in mind, and the patient-booking code paths (added by a later feature) write the
  wrong identity system's UUID into it.
- Effect: on a real Postgres, every patient self-service booking (both Fixed-Time and Queue/Token)
  attempts to INSERT a `booking` row whose `booked_by_account_id` does not exist in `account` (for all
  practical purposes — the two UUID spaces are unrelated) and is rejected with a foreign-key-violation
  error.
  - In `PatientBookingService.bookSlot` (line 99), this is caught by the existing
    `catch (DataIntegrityViolationException e)` block and **misreported as `SlotAlreadyBookedException`**
    — every patient Fixed-Time booking attempt would appear to fail with "this slot is already booked,"
    regardless of the slot's actual availability.
  - In `PatientQueueBookingService.bookSlot`, there is no such catch block at all — the FK violation
    surfaces as an **uncaught 500**.
- Confidence and verification status: this was traced independently across migration DDL, entity
  annotations, and two separate service call sites — not a single-file assumption. A directly relevant
  existing test, `backend/src/test/java/com/cms/booking/integration/AbstractPatientBookingIntegrationTest.java`
  (used by `PatientBookingFirstTimeLinkTest.java:32-40`, which asserts `status().isCreated()` on exactly
  this code path), would very likely fail against a real Postgres — but **this has not been executed and
  confirmed**, because Testcontainers cannot reach Docker in this development sandbox (the same,
  previously-documented limitation affecting every integration test in this codebase). Treat this finding
  as high-confidence static analysis, not a confirmed live failure, until run against a real database.
- Fix: either (a) source a real staff `account.id` for `bookedByAccountId` when one genuinely exists for
  the calling context (it does not for a pure patient self-service booking), or (b) add a second, nullable
  column — e.g. `booked_by_patient_account_id UUID REFERENCES patient_account (id)` — and populate exactly
  one of the two columns per booking path, mirroring the disjoint-identity design already used correctly
  elsewhere in this codebase (`Patient.patientAccount`, itself nullable for exactly this kind of dual-path
  reason).

**STATUS: FIXED, 2026-09-07.** Took option (b). `V24__booking_disjoint_identity_fix.sql` drops
`booked_by_account_id`'s `NOT NULL`, adds nullable `booked_by_patient_account_id UUID REFERENCES
patient_account (id)`, and adds `ck_booking_booked_by_exactly_one` (a CHECK enforcing exactly one of
the two is set). `Booking.java` gained the new field plus a `Booking.bookedByPatient(...)` static
factory (the existing constructors, used by every staff-booking path and by 12 test fixture files,
were left untouched to avoid unnecessary churn). `PatientBookingService.java:93` and
`PatientQueueBookingService.java:73` now call `Booking.bookedByPatient(...)` instead of the
staff-oriented constructor. This also structurally resolves the two downstream effects described
above (the misreported `SlotAlreadyBookedException` in `PatientBookingService`, and the uncaught 500
in `PatientQueueBookingService`) — with the FK now correct, neither `DataIntegrityViolationException`
path is reachable via this bug anymore. Full backend build (compile + spotless) green. **Still not
verified against a real Postgres** — the sandbox's Testcontainers/Docker limitation is unchanged, so
`AbstractPatientBookingIntegrationTest`/`PatientBookingFirstTimeLinkTest` remain unexecuted; re-verify
in a real dev/CI environment before treating this as fully confirmed, per this file's own original
confidence caveat.

---

**[LOW] - [INBOX_ITEM] - [FK_INTEGRITY_GAP]**

- Migration: `V23__create_inbox_item.sql:10` — `claimed_by_account_id UUID,` has no `REFERENCES` clause,
  unlike every other `*_id` column in the same table (`clinic_id`, `booking_id`, `waitlist_entry_id` all
  have explicit FKs).
- Entity: `InboxItem.java:63-64` — plain `UUID claimedByAccountId` field, not a JPA relation.
- Usage is currently consistent (not actively broken): `InboxItemService.java:84-93` (`claim`) and
  `:171` (`accountRepository.findById(item.getClaimedByAccountId())`) always source this value from
  `SecurityConfig.currentAccountId` via `InboxController.java:40` — a genuine staff `account.id`. This
  is the mirror-image case of the BOOKING finding above: same missing-FK shape, but no actual mismatch
  in how the column is populated today.
- Fix: add `REFERENCES account (id)` in a follow-up migration for consistency and to close the gap at
  the data layer per this codebase's own stated data-integrity principle, even though no live bug exists
  today.

---

**[LOW] - [BOOKING / APPOINTMENT_TYPE] - [TYPE_MISMATCH] (JSON boundary)**

- Backend: `Booking.lockedFee` (`Booking.java:48-49`) and `AppointmentType.feeOverride`
  (`AppointmentType.java:33-34`) are `BigDecimal(precision=10, scale=2)` — correct on the entity/DB side.
  No custom Jackson configuration exists anywhere (`application.yml` has none; no custom `ObjectMapper`
  bean found), so Spring Boot's default Jackson serializes `BigDecimal` as a bare JSON number.
- Frontend: typed as plain `number` in multiple files (`staff-booking/api.ts:28`,
  `patient-booking/api.ts:11,51`, `waitlist/api.ts:85`, `booking-cancellation/api.ts:12`,
  `patient-booking/queueApi.ts:17`, `staff-booking/queueApi.ts:19`), displayed via `.toFixed(2)`.
- Effect: `BigDecimal`'s exact-decimal guarantee is lost once the value crosses into a JS double. Low
  practical risk today — INR consultation fees are small, two-decimal, display-only, and no client-side
  fee arithmetic exists anywhere — but it is a genuine boundary type-fidelity gap and a latent risk if
  larger amounts or client-side fee math are ever introduced.
- Fix (only worth doing if/when it matters): serialize as a string
  (`@JsonFormat(shape = JsonFormat.Shape.STRING)` on the relevant fields) and parse decimal-safely on
  the client.

---

## (a) Entity/migration pairs verified fully consistent

Every one of the following was checked for column presence, nullability agreement, and exact type
agreement (`UUID`↔`UUID`, `TIMESTAMPTZ`↔`Instant`, `DATE`↔`LocalDate`, `TIME`↔`LocalTime`,
`NUMERIC(10,2)`↔`BigDecimal` with matching precision/scale, `VARCHAR`↔`String`/enum):

- `Clinic` ↔ `V1` — `Account` ↔ `V1` — `RoleAssignment` ↔ `V1` (CHECK constraint values match `Role` enum exactly)
- `PatientAccount` ↔ `V2` + `V11` (`sms_opt_in`, `push_opt_in`)
- `DoctorProfile` ↔ `V3` + `V4` (`visible`, license uniqueness)
- `Patient` ↔ `V5` + `V22` (`anonymized_at`); both partial unique indexes (`uq_patient_clinic_account`, `uq_patient_clinic_phone_unlinked`) reference columns that still exist with unchanged meaning
- `NotificationEvent` ↔ `V6`
- `Schedule` (+ `schedule_day` `@ElementCollection`) ↔ `V7`
- `Session` ↔ `V8` + `V15` (`delay_minutes`)
- `AppointmentType` ↔ `V9`; `DoctorDefaultFee` ↔ `V9` (`uq_doctor_default_fee_doctor_profile` still valid)
- `Slot` ↔ `V10` + `V11` (nullable start/end, `token_number`) + `V13` (`on_hold`); `uq_slot_session_token` partial index still valid
- `Booking` ↔ `V12` + `V14` (`override_reason`) + `V16` (`status`) — column mapping/type/nullability fully consistent aside from the CRITICAL semantic issue above (not a mapping problem); `uq_booking_slot_active` partial index valid
- `WaitlistEntry` ↔ `V17` + `V18` (`offered_slot_id`)
- `ConsultationNote` ↔ `V19`
- `Prescription` / `PrescriptionItem` ↔ `V20` — JPA `cascade={PERSIST,REMOVE}` on `Prescription.items` is app-managed (no DB `ON DELETE CASCADE` needed, since deletion is never invoked outside that cascade)
- `ExternalRecordReference` ↔ `V21`
- `InboxItem` ↔ `V23` — DB-only `ON DELETE CASCADE` on `booking_id`/`waitlist_entry_id` (no matching JPA collection on `Booking`/`WaitlistEntry`) is consistent and harmless, since neither row type is ever hard-deleted (both use status-based soft transitions)

## (b) Every `@Enumerated` field — confirmed `STRING`, zero `ORDINAL` usages anywhere

1. `RoleAssignment.role` → `Role {ClinicAdmin, Doctor, Operations}` — `RoleAssignment.java:40`
2. `Schedule.daysOfWeek` → `DayOfWeek` (element collection) — `Schedule.java:48`
3. `Schedule.mode` → `ScheduleMode {FIXED_TIME, QUEUE}` — `Schedule.java:57`
4. `Session.mode` → `ScheduleMode` — `Session.java:52`
5. `Slot.status` → `SlotStatus {OPEN, BOOKED, NO_SHOW, COMPLETED}` — `Slot.java:49`
6. `Booking.paymentStatus` → `PaymentStatus {PENDING, PAID}` — `Booking.java:51`
7. `Booking.status` → `BookingStatus {ACTIVE, CANCELLED}` — `Booking.java:63`
8. `WaitlistEntry.status` → `WaitlistEntryStatus {WAITING, OFFERED, CLAIMED, EXPIRED}` — `WaitlistEntry.java:52`
9. `NotificationEvent.status` → `NotificationEventStatus {PENDING, ACTIONED, EXPIRED}` — `NotificationEvent.java:52`
10. `InboxItem.itemType` → `InboxItemType {WALK_IN, WAITLIST_OFFER, DEVERIFICATION_CASCADE}` — `InboxItem.java:41`
11. `InboxItem.status` → `InboxItemStatus {UNCLAIMED, CLAIMED, RESOLVED}` — `InboxItem.java:59`

Every enum with a consuming UI feature was cross-checked character-for-character against the frontend
literal it's compared against: `ScheduleMode` (`ScheduleForm.tsx:185-186`), `PaymentStatus` (6 booking-
related `api.ts` files), `BookingStatus` (`booking-cancellation/api.ts:14`, `waitlist/api.ts:87`),
`WaitlistEntryStatus` (`waitlist/api.ts:13`), `InboxItemType`/`InboxItemStatus` (`inbox/api.ts:6-7`),
`RoleAssignment.Role` (`staff-onboarding/api.ts:10,78`, `OnboardStaffForm.tsx:235-236`). **No typos or
casing drift found anywhere.** `SlotStatus` and `NotificationEventStatus` have no frontend literal
occurrences — consistent with no consuming UI existing yet for either.

## (c) Overall schema-layer health

The schema layer is otherwise in excellent shape: all 19 tables across 23 migrations map 1:1 onto 19
`@Entity` classes with zero unmapped columns and zero entity fields lacking a backing column — meaning
`ddl-auto: validate` boots cleanly. Every money column correctly uses `BigDecimal` with matching
precision/scale, every `TIMESTAMPTZ` uses `Instant` (never the timezone-dropping `LocalDateTime` for a
persisted field), nullability agrees everywhere checked, every partial unique index's referenced columns
still exist with unchanged meaning, and all 11 `@Enumerated` fields use `STRING` with zero drift against
their frontend literal counterparts across all 61 TS/TSX files. The one place this rigor genuinely breaks
is `Booking.bookedByAccountId` — the codebase is otherwise scrupulous about keeping the staff `account`
and self-service `patient_account` identity systems disjoint, but the patient-booking code paths write
the wrong identity system's id into a column whose FK constraint (and every other write path) expects the
other one.
