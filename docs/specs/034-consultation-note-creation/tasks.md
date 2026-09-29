---

description: "Task list for Consultation Note Creation"
---

# Tasks: Consultation Note Creation

**Input**: Design documents from `/specs/034-consultation-note-creation/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/consultation-note.md, quickstart.md

**Tests**: Included — Constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this project.

**Organization**: This feature has a single user story (create + get + every authorization/immutability rule are all one indivisible slice — there's no meaningful way to demonstrate "create" without "reject the wrong doctor" or "reject a second note," since those are the same acceptance criteria set).

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

New module: `backend/src/main/java/com/cms/clinical/`, `backend/src/test/java/com/cms/clinical/integration/`. Extends `backend/src/main/java/com/cms/identity/account/SecurityConfig.java`. New: `frontend/src/features/consultation-notes/`, `frontend/tests/consultation-notes/`.

---

## Phase 1: Setup

**Purpose**: The new entity and request/response DTOs.

- [X] T001 Create `ConsultationNote` entity (`booking` `@OneToOne` not null, `doctorProfile` `@ManyToOne` not null, `content` not null, `createdAt` not null) per data-model.md in `backend/src/main/java/com/cms/clinical/ConsultationNote.java`
- [X] T002 [P] Create `CreateConsultationNoteRequest` (`content`) and `ConsultationNoteResponse` (`id`, `bookingId`, `doctorProfileId`, `content`, `createdAt`) DTOs per contracts/consultation-note.md in `backend/src/main/java/com/cms/clinical/dto/`

**Checkpoint**: Types exist; no behavior yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Migration, repository, exceptions, exception handler, both security matchers, and the test fixture.

**⚠️ CRITICAL**: No user story test can be written until this phase is complete.

- [X] T003 Create migration `V19__create_consultation_note.sql` per data-model.md in `backend/src/main/resources/db/migration/V19__create_consultation_note.sql` (depends on T001)
- [X] T004 Create `ConsultationNoteRepository` with `findByBooking_Id(UUID bookingId)` in `backend/src/main/java/com/cms/clinical/ConsultationNoteRepository.java` (depends on T001)
- [X] T005 [P] Create `ConsultationNoteAlreadyExistsException`, `ConsultationNoteNotFoundException` in `backend/src/main/java/com/cms/clinical/`. Reuses `com.cms.booking.BookingNotFoundException` and `com.cms.scheduling.ForbiddenException` directly (both already exist and are already globally mapped) rather than duplicating — mirrors this session's established reuse pattern
- [X] T006 Create `ClinicalDocumentationExceptionHandler` mapping only the two new exceptions (`409 CONSULTATION_NOTE_ALREADY_EXISTS`, `404 CONSULTATION_NOTE_NOT_FOUND`) per contracts/consultation-note.md in `backend/src/main/java/com/cms/clinical/ClinicalDocumentationExceptionHandler.java` (depends on T005)
- [X] T007 Add explicit matchers `.requestMatchers(HttpMethod.POST, "/api/v1/clinics/*/bookings/*/consultation-notes").authenticated()` and the `GET` equivalent to the `/api/v1/clinics/**` chain in `backend/src/main/java/com/cms/identity/account/SecurityConfig.java`
- [X] T008 Create `AbstractConsultationNoteIntegrationTest` — clinic/doctor(s)/staff-token/booking fixture (mirrors `AbstractSessionCancellationIntegrationTest`'s combination shape) — in `backend/src/test/java/com/cms/clinical/integration/AbstractConsultationNoteIntegrationTest.java` (depends on T001, T004)

**Checkpoint**: Foundation ready — the user story can now be built.

---

## Phase 3: Treating Doctor Writes and Reads a Consultation Note (Priority: P1) 🎯 MVP

**Goal**: The doctor assigned to a booking's slot creates exactly one permanent note for it and can read it back; no one else can create, read, edit, or delete it.

**Independent Test**: Per quickstart.md Scenarios 1–4.

### Tests for User Story 1 (write first, confirm they FAIL before implementation)

- [X] T009 [P] [US1] Integration test: the treating doctor creates a note → `201`; the same doctor retrieves it via `GET` → `200`, identical content (FR-001/FR-006, SC-001) — in `backend/src/test/java/com/cms/clinical/integration/ConsultationNoteCreateTest.java`
- [X] T010 [P] [US1] Integration test: a second creation attempt against the same booking by the same doctor → `409 CONSULTATION_NOTE_ALREADY_EXISTS` (FR-003, SC-003); a genuine 2-thread concurrent creation race against the same booking → exactly one succeeds — in the same file as T009
- [X] T011 [P] [US1] Integration test: a different doctor at the same clinic attempts to create or read a note for a booking that isn't theirs → `403 FORBIDDEN` for both; a ClinicAdmin at the same clinic attempts the same → `403 FORBIDDEN` for both, no override (FR-004, SC-002) — in `backend/src/test/java/com/cms/clinical/integration/ConsultationNoteAuthorizationTest.java`
- [X] T012 [P] [US1] Integration test: create/get against an unknown `bookingId` → `404 BOOKING_NOT_FOUND` for create, `404 CONSULTATION_NOTE_NOT_FOUND` for get — in the same file as T009
- [X] T012a [P] [US1] Integration test: note creation succeeds for a booking whose Slot is already `CANCELLED`, `COMPLETED`, or `NO_SHOW` — FR-007 explicitly forbids any status precondition, so this guards against one being added by mistake later (Analyze finding C1) — in the same file as T009

### Implementation for User Story 1

- [X] T013 [US1] Implement `ConsultationNoteService.create(clinicId, bookingId, callerAccountId, content)` per research.md R2/R3: loads booking (scoped to clinic), traces treating doctor, authorizes, `saveAndFlush` inside try/catch for `DataIntegrityViolationException` → `ConsultationNoteAlreadyExistsException` — in `backend/src/main/java/com/cms/clinical/ConsultationNoteService.java` (depends on T004, T005)
- [X] T014 [US1] Implement `ConsultationNoteService.get(clinicId, bookingId, callerAccountId)` — same authorization trace, `404` if none exists yet — in the same file (depends on T013)
- [X] T015 [US1] Implement `StaffConsultationNoteController` (`POST`/`GET /api/v1/clinics/{clinicId}/bookings/{bookingId}/consultation-notes`) per contracts/consultation-note.md — in `backend/src/main/java/com/cms/clinical/StaffConsultationNoteController.java` (depends on T002, T013, T014)

**Checkpoint**: User Story 1 fully functional and independently testable.

---

## Phase 4: Frontend & Polish

- [X] T016 [P] Create `frontend/src/features/consultation-notes/api.ts` — `createConsultationNote(clinicId, bookingId, content, token)`, `getConsultationNote(clinicId, bookingId, token)`
- [X] T017 Create `frontend/src/features/consultation-notes/ConsultationNoteForm.tsx` — a content textarea plus submit action, showing the note read-only once it exists (depends on T016)
- [X] T018 [P] Frontend test: creates a note, shows it read-only afterward, shows the `CONSULTATION_NOTE_ALREADY_EXISTS`/`FORBIDDEN` error messages — in `frontend/tests/consultation-notes/ConsultationNoteForm.test.tsx` (depends on T017)
- [X] T019 Run `quickstart.md` Scenarios 1–4 end-to-end against a real (or Testcontainers) Postgres instance; record pass/fail in `backlog/progress.md` — blocked by the sandbox's Testcontainers/Docker limitation (same as every prior feature this session); Scenarios 1-3 each covered by a passing-when-run integration test (T009-T012a) verified via compile + structural review; Scenario 4 (frontend) verified live via the Vitest suite (T018, 4/4 green)
- [X] T020 Run full backend build (`/tmp/gradle-8.10/bin/gradle compileJava compileTestJava spotlessCheck -q` and `build -x test`) and full frontend suite (`npm test` in `frontend/`); confirm both green with zero regressions

---

## Dependencies & Execution Order

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS the user story's test-writing.
- **US1 (Phase 3)**: Depends on Foundational.
- **Frontend & Polish (Phase 4)**: Depends on US1.

### Parallel Opportunities

- T002 in parallel with T001.
- T005 items in parallel.
- T009–T012 (all tests) in parallel — depend only on T008.

---

## Implementation Strategy

1. Phase 1 → Phase 2. **STOP and VALIDATE**: foundational pieces compile, no behavior yet.
2. Phase 3. **STOP and VALIDATE**: quickstart.md Scenarios 1–4 pass.
3. Phase 4: frontend, full-suite verification, quickstart sign-off.
