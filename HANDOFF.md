# Handoff Note — 2026-09-23

Session context: spans five days. **Part 1** (2026-09-13): a design/UX polish pass across the
entire patient-facing booking/waitlist flow, plus a few adjacent staff pages. **Part 2**
(2026-09-13): a full-repo audit (backend + frontend) followed by fixing every Critical/Major
finding. **Part 3** (2026-09-14): resolved the accent-color decision flagged at the end of
Part 2, executed the premium-redesign brief (accessibility-hardening pass on every real finding
from the Part 2 oxlint audit), then — after live feedback that the first color pick still read
as generic — went through two more rounds of live color-demo iteration and landed on a final
**teal + cobalt two-accent system**, now implemented across the real app (see "Part 3b" below).
**Part 4** (2026-09-15): fixed a real dev-environment reliability bug (background dev servers
dying), added a `CLAUDE.md` project blueprint, and **put the project on GitHub for the first
time**. **Part 5** (2026-09-16): three full-page redesigns (landing page, patient login, patient
dashboard) for backlog feature `056-design-copy-quality-pass`, a real slot-completion-timing bug
fix, a new Super-Admin-driven ClinicAdmin password-reset feature, and a walk-in-management audit
(one real display gap fixed, three real operational gaps identified and scoped but left
unimplemented). **Part 6** (2026-09-17, this session): implemented Part 5's walk-in remediation
plan (form context, `Booking.source` + Day Sheet badge, Inbox visit-status); a real,
exploitable security bug found and fixed (patients could book a doctor's reserved walk-in
capacity directly); a new Schedule break-window feature (merges two schedules with a gap into
one) plus a safety-gated Session/Schedule-deletion capability, both used live to fix a real
doctor's broken schedule; a ClinicAdmin-facing staff password-reset feature; a new "today's
patients" roster and a "booking setup incomplete" warning (both aimed at catching exactly the
kind of human error that caused this session's last bug); and several smaller real-bug fixes
along the way (a slot completable before its own start time was really a live UI bug not a
backend bug on the frontend side, a stale Vite HMR overlay, a Day Sheet search bar blind to
just-onboarded doctors, and three clinical-record forms staying live/submittable under an access-
denied banner). See "Part 6" below for the full list. **Part 11** (2026-09-23, this session):
finished feature `059-patient-clinical-record-access` (all 47 tasks, live-verified); converted
`BookSlotForm`/`QueueBookSlotForm` to real modals and removed the redundant "Your name" field on
both; then took a new feature, `061-doctor-live-status` (a live, train-tracking-style schedule
deviation status extending the existing Session Delay Tracking feature), through
specify→clarify→plan→tasks→analyze→implement — **paused mid-implement at the user's request**,
with Foundational + User Story 1 + User Story 2 (T001–T027) done and live-verified, User Story 3
and Polish (T028–T037) not yet started. See "Part 11" below for the full detail and exact resume
point. Dev servers will need restarting per the instructions below — see the new Windows-native
Gradle note first, the existing `/tmp`-based instructions describe a different (Linux) sandbox
than this session actually ran in.

## ⚠️ 768 files uncommitted since the initial commit

`git status` currently shows **768 changed files** (was 683 as of the Part 9 handoff, 622 as of
Part 4), none of them staged or committed since the "Update handoff note with Part 4" commit
(which touched only this file). This means every substantive change described in Parts 3b–10
below — plus, apparently, work from sessions before this handoff note's own history (the working
tree already contains the `045-backend-module-layering` package-rename from
`com.cms.booking.*Controller` to `com.cms.booking.api.*Controller`, which no prior handoff note
mentions committing) — is sitting only in the local working tree, not in git history, and not on
GitHub. If this machine's working tree is ever lost or reset, all of it goes with it. Not fixed
this session (wasn't asked); flagging so the next session doesn't assume Parts 3b onward are
safely on GitHub just because Part 4 said the repo was pushed. This is now the single biggest
risk to this project's history — strongly worth committing in coherent chunks before the working
tree grows any larger.

## ⚠️ This session ran on native Windows, not the `/tmp`-based sandbox the notes below assume

> **Obsolete since Part 16 (2026-10-01):** Spring Boot 4 refuses to run under Gradle 8.10. Do **not** use the standalone `gradle-8.10` paths below. Use the project's wrapper instead: `backend/gradlew.bat` on Windows, `./gradlew` elsewhere. The wrapper pins Gradle 8.14.3.

The "Resume the environment" section right below was written for an earlier session's Linux-style
sandbox (`/tmp/gradle-8.10/bin/gradle`, bash-only). This session's actual machine is native
Windows (PowerShell/Git Bash), and Gradle was invoked instead as:
```bash
"/c/Users/risha/AppData/Local/Temp/gradle-8.10/bin/gradle.bat" -p backend bootRun
```
(a self-contained Gradle 8.10 distribution already extracted under the Windows temp dir — no
`/tmp` involved at all). If you're resuming on this same Windows machine, use that path, not the
`/tmp` one below. The OneDrive build-corruption workaround (`rm -rf backend/build`, see memory
`gradle_onedrive_build_corruption`) is still very much real and came up repeatedly this session —
this project's working tree lives under `C:\Users\risha\OneDrive\Documents\CMS2`, and OneDrive's
own background sync intermittently corrupts Gradle's incremental-build state (`processResources`/
`compileJava` "Cannot access a file"/"not a regular file" errors) even without any live server
holding a lock on it. Clear it and retry; it's always been transient.

## ⚠️ New behavior this session — read before restarting the backend

**JWT signing secrets no longer have hardcoded dev-only fallback values.** Previously
`patient.jwt.secret` / `staff.jwt.secret` / `admin.super-admin.jwt.secret` fell back to fixed
strings committed in `application.yml` if the env vars weren't set — a real security hole (this
was Critical/Major finding #4 in this session's audit, now fixed). Now, if
`PATIENT_JWT_SECRET`/`STAFF_JWT_SECRET`/`SUPER_ADMIN_JWT_SECRET` are unset, each of
`JwtService`/`StaffJwtService`/`SuperAdminJwtService` generates a **random signing key at
startup** (with a WARN log), mirroring the Super Admin username/password's own existing
fail-safe pattern.

**Practical consequence: every backend restart invalidates every previously-issued token**
(patient sessions, staff sessions, Super Admin sessions) unless those three env vars are
explicitly set to a stable value for the session. **Everyone will need to log in again after
every restart** — this is expected, not a bug. If this becomes annoying during a long working
session, export fixed values for all three before starting `bootRun`.

## Resume the environment

**Backend:**
```bash
cd backend && /tmp/gradle-8.10/bin/gradle bootRun
```
- Never use `./gradlew` in this sandbox — use `/tmp/gradle-8.10/bin/gradle` (memory
  `gradle_local_bootstrap`).
- **New this session**: `/tmp` is ephemeral in this sandbox and its contents (including the
  extracted Gradle distribution) can vanish mid-session even after working earlier in the same
  session. If `/tmp/gradle-8.10/bin/gradle` is missing, don't re-download — a complete working
  Gradle 8.10 distribution is already cached at
  `/c/Users/risha/.gradle/wrapper/dists/gradle-8.10-bin/<hash>/gradle-8.10/`; `cp -r` its `bin/`
  back into `/tmp/gradle-8.10/` (see memory `gradle_local_bootstrap` for the full recovery
  command).
- If `bootRun` fails on `processResources`/"Cannot access a file in the destination directory",
  that's the known OneDrive-sync build corruption — `rm -rf backend/build` then retry (memory
  `gradle_onedrive_build_corruption`).
- If port 8080 is already bound by a stale process, kill the real PID directly (`TaskStop`
  doesn't reliably kill the underlying JVM):
  ```powershell
  Get-NetTCPConnection -LocalPort 8080 -State Listen | Stop-Process -Force
  ```
- To reach the Super Admin console (`/super-admin-console`) with stable credentials, start with:
  ```bash
  SUPER_ADMIN_USERNAME=superadmin SUPER_ADMIN_PASSWORD='Str0ng!AdminPass' /tmp/gradle-8.10/bin/gradle bootRun
  ```
  (Only the username/password were ever randomized by default — that part is pre-existing
  behavior, not new this session. The JWT secret randomization described above is new and
  independent of these two vars.)

**Frontend:**
```bash
cd frontend && npm run dev
```
Runs on `http://localhost:5173`. (`npm run test` = `vitest run`, `npm run build` runs `tsc -b`
first, `npm run lint` = `oxlint` — see the audit section below for the exact flags used to get
real accessibility findings out of it.)

**Live test data:**
- **Star Clinic** (Noida, `13d0c877-7829-4dc1-baa9-0150f855c1bf`) — original seed data, untouched
  structurally. Doctor "Gauresh Kumar", two profiles (`844ecbd7-37f0-4a6f-903c-55fbb34c0a71`
  primary, `9794190b-61d1-413c-850b-b4e7e8122aae`), both with permanent appointment types.
  ClinicAdmin login is `harshSingh@mail.com` — **password was reset this session** via the new
  Super Admin "Reset admin password" feature (Part 5); the temporary password was shown once
  in-app and is intentionally not recorded here (same "shown once, never persisted" contract as
  staff onboarding). Reset again from `/super-admin-console/clinics` (Verified tab) if needed.
  See the *original* handoff note (git history / prior version of this file) if full seed detail
  is needed — still valid, not reproduced here.
- **Crystal Health Centre** (Indore) and **Sunrise Polyclinic** (Ahmedabad) — both pre-existing
  verified clinics; both ClinicAdmin passwords were also reset this session via the same feature,
  for the same reason (see Part 5 §3). Crystal Health Centre's real login email is
  `vikas.sharma@mail.com` — **not** `info@crystalhealth.in`, which is only `Clinic.contactEmail`
  (public info), a distinct field from the ClinicAdmin `Account.email` (login credential). This
  distinction caused a real, confusing-but-not-buggy 401 that took real investigation to
  diagnose — worth remembering before assuming a login failure means a broken password.
- **Design Test Clinic** (`b60d506f-0de3-4c29-84c8-5ef98a203d80`) — a throwaway clinic created
  *this* session purely to live-verify staff-side fixes (appointment types, schedules, walk-ins,
  the SessionDelayController authz fix). ClinicAdmin: `designadmin@example.com` /
  `Str0ng!Pass`. One doctor, "Dr. Test Doctor" (`8eda5240-eaa0-4977-bac3-1b08e4215ae9`, staff
  code `DR-5970`, General Medicine), with a Fixed-Time schedule (Mon 09:00–13:00, 15-min slots)
  and a Queue schedule (Sat 16:00–18:00), a default fee of ₹450, and two appointment types
  (General Consultation — uses default fee; Follow-up — ₹250 override). Safe to keep or delete;
  nothing else depends on it. Note: this clinic is unverified (Super Admin never verified it) —
  that only affects public discovery visibility, not staff-console functionality, which is why
  it was usable for testing without going through verification.

## Part 1 — Design/UX polish (patient booking + waitlist flow, plus a few staff pages)

Driven by a series of "check the X page too" requests, working through the patient portal's
booking/waitlist experience component by component, applying a consistent elevation upgrade
(`rounded-lg`/`shadow-sm` → `rounded-xl`/`shadow-md`), replacing native browser form controls
(radios, checkboxes) with styled selectable-chip equivalents matching the app's existing DateStrip
language, and fixing "raw data dump" issues (unformatted ISO dates/times, times with seconds)
across essentially every patient-facing view. In order:

1. **Discovery search** (`DiscoverySearch.tsx`) — result cards got the icon-badge chip treatment.
2. **Slot pickers** — `OpenSlotList.tsx` (Fixed-Time) and `QueueSessionList.tsx` (Queue): card-per-doctor
   grouping, time chips redesigned twice after live feedback (first found "massive," second found
   "dull, no soul") — landed on a two-line stacked chip (bold start time + muted duration,
   e.g. "11:15" / "30 min") replacing the old single-line time-range text. Also added
   Morning/Afternoon/Evening period grouping.
3. **Booking forms** — `BookSlotForm.tsx` / `QueueBookSlotForm.tsx`: icon-badge headers, a
   doctor/date/time summary panel, inline fee preview under the appointment-type select, and a
   receipt-style confirmation (checkmark badge + `dl` summary) replacing one flat sentence.
   **Found and fixed a real bug along the way**: the confirmation state was unreachable — the
   parent (`OpenSlotList`) cleared the selected slot immediately on a successful booking,
   unmounting `BookSlotForm` before its own confirmation could render. Fixed by having the
   parent hold onto the just-booked slot's data separately from the live slot list.
4. **Waitlist** — `JoinWaitlistForm.tsx` (previously had *zero* styling — bare page, no card, no
   header — the single biggest gap found) and `ClaimOfferCard.tsx`: same elevation/chip treatment,
   plus a new backend endpoint (`GET /api/v1/patients/clinics/{clinicId}/doctors`, and
   `GET /api/v1/patients/doctors/{doctorProfileId}/appointment-types`) so patients can pick a
   doctor/appointment-type from a real list instead of typing a raw UUID.
5. **My Bookings** (`MyBookings.tsx`, `NextAppointmentCard.tsx`) — fixed the same raw-date/time
   formatting issue.
6. **Appointment types config** (staff, `AppointmentTypeConfigForm.tsx`) and **Doctor schedule**
   (staff, `ScheduleForm.tsx`) — same elevation upgrade; `ScheduleForm`'s native day-of-week
   checkboxes became styled toggle chips; found the same raw-time-with-seconds bug live while
   testing and fixed it too.
7. **Walk-in form** (staff, `WalkInForm.tsx`) — same elevation + radio-to-chip conversion.

All verified live in-browser at each step (Star Clinic initially, Design Test Clinic once
created), not just via unit tests. **236 frontend tests passing** (was 230 at session start).

## Part 2 — Full-repo audit and fixes

User asked for a Senior-Engineer-style audit: tool check → backend scan → frontend scan →
prioritized checklist → **approval gate** → fix Critical/Major only (Minor deferred).

**Method**: no git repo exists here (`git status` confirms), so the `security-review`/`code-review`
skills (diff/PR-based) didn't apply directly. Instead: `oxlint --jsx-a11y-plugin
--react-plugin --react-perf-plugin --import-plugin` for a real automated frontend pass, plus
three parallel `Explore` background agents (backend concurrency/data-integrity, backend
security, frontend UX/dead-code) for the broad code-reading work, consolidated into one
checklist. Full findings list is in this conversation's transcript; summary:

**Fixed (all 3 Critical + all 7 Major):**
1. `SessionDelayController` had zero clinic-membership check — any staff JWT from *any* clinic
   could read another clinic's session delay data. Fixed + regression test added
   (`SessionDelayAuthorizationTest.java`) + verified live via curl (200/403/401 all correct).
2. `ScheduleService.requireNoOverlap` was a pure check-then-act race with no DB constraint —
   fixed with a Postgres advisory transaction lock keyed on the doctor
   (`ScheduleService.lockDoctorForOverlapCheck`).
3. Claiming a waitlist offer's confirmation was unmountable (same bug class as #3 in Part 1,
   different flow) — fixed in `MyWaitlistEntries.tsx`.
4. JWT secrets fail-unsafe defaults — see the callout at the top of this note.
5. `AppointmentTypeService.setDefaultFee` had no `DataIntegrityViolationException` handling on
   its insert branch — fixed with `saveAndFlush` + retry-as-update on conflict.
6. Homepage's dead `/admin` link → now `/super-admin-console`.
7. `DoctorSelect`/`PatientDoctorSelect` had no empty-roster message (silently unfillable
   required `<select>`) — fixed, tests added.
8. Raw ISO dates in `ExternalRecordReferenceForm.tsx` / `TriggerSessionGeneration.tsx` — fixed.
9. Added `@Size` caps to every free-text field across booking/waitlist request DTOs (patient
   name, phone, cancellation/override reason, specialization) + `@Valid` on the ~11 controller
   methods that needed it.
10. Added `@DecimalMin`/`@NotNull` to fee/amount fields (`feeOverride`, default-fee `amount`) —
    a negative fee override previously passed straight through as a locked booking fee.

All 10 verified: backend `compileJava`/`compileTestJava` clean, frontend 236 tests passing, and
the three most security-sensitive fixes (#1, #4, #9/#10) additionally verified live via curl
against the running backend (not just compiled/unit-tested).

**Deferred (Minor, not fixed — approval was Critical/Major only):**
- `InboxItemService` N+1 query (one `findById` per claimed inbox item).
- `InboxItemCard.tsx` falls back to a raw account UUID when `claimedByName` is null.
- `ScheduleForm`'s `existingSchedule`/edit-mode prop is fully wired but never invoked anywhere —
  editing an existing schedule is impossible through the UI (confirmed the *only* such orphaned
  pattern in the app).
- 29 real `oxlint --jsx-a11y-plugin` findings — **addressed this session, see Part 3.**

## Part 3 — Color rebrand + accessibility hardening (COMPLETE, 2026-09-14)

User resolved the decision flagged at the end of Part 2 with: "better option than the existing
accent color and start Part 3."

**1. Accent color rebrand.** Replaced "harbor blue" (OKLCH hue 210, read as teal — conflicted
with the brief's explicit "no generic teal primary buttons" constraint) with **"clinical
terracotta"** (OKLCH hue 44), chosen because it's distinctly non-teal/non-purple and ties
directly to `PRODUCT.md`'s own brand-personality reference to "Ro" (a health company built on
terracotta/coral branding) rather than being an arbitrary pick.
- `frontend/src/index.css`: full `--color-indigo-*` ramp re-derived at hue 44 (Tailwind's
  `indigo` scale name is overridden at the `@theme` level, so every `bg-indigo-600` etc.
  app-wide picked this up automatically — no component-level changes needed).
- `--color-gray-*` ramp and `--shadow-*` tint hue moved from 210 → **250** (cool slate) —
  deliberately *not* tied to the new warm accent hue, so near-white surfaces don't drift toward
  the "cream/sand" look the impeccable-skill guidance flags as an AI-cliché tell.
- Contrast re-verified with a canvas-based pixel-readback method (not `getComputedStyle`, which
  returns unconverted `oklch()` strings) — indigo-600-on-white 6.4:1, indigo-700 hover 8.9:1,
  indigo-600-on-indigo-50 6.0:1, all comfortably above WCAG AA. Full rationale and the contrast
  table are in `DESIGN.md`'s "Palette (OKLCH)" section.
- Verified live in-browser (homepage, Discovery search, patient booking slot picker, staff
  roster badges) — reads as warm/premium, not teal.

**2. Accessibility hardening** — worked through every real (non-false-positive) finding from
Part 2's 29-item oxlint `jsx-a11y` list:
- `role="status"` divs/paragraphs → native `<output>` (implicit `role="status"` semantics,
  needs an explicit `className="block"` since `<output>` defaults to `display: inline` unlike
  `<div>`/`<p>`): `ListSkeleton.tsx`, `InboxPage.tsx`, `BookingContextHeader.tsx`,
  `PatientContextHeader.tsx`, `QueuePositionIndicator.tsx`, both branches of
  `OnboardStaffForm.tsx`.
- `SortableColumnHeader.tsx`: `aria-sort` moved from the inner `<button>` to the parent
  `<th scope="col">` — `aria-sort` isn't valid ARIA on an implicit `role="button"` element.
- **`DeleteConfirmModal.tsx` / `RejectConfirmModal.tsx` / `EmployeeModal.tsx`**: converted from a
  hand-rolled `role="presentation"` backdrop + `role="dialog"` div + manual Tab-cycling/Escape
  keydown listener to a **native `<dialog>` element** (`showModal()`/`.close()`). The browser now
  handles focus trapping and Escape-to-close natively — all the manual keydown/focus-trap code
  was deleted, not just relocated. Backdrop-click-to-dismiss now works by comparing
  `event.target === dialogRef.current` on the dialog's own `onClick` (clicks on content never
  match, since they land on a descendant) instead of a separate `stopPropagation`-guarded div.
  **jsdom doesn't implement `HTMLDialogElement.showModal`/`.close()` or native Escape-to-close**
  (a known jsdom gap: https://github.com/jsdom/jsdom/issues/3294) — polyfilled in
  `frontend/src/test-setup.ts` so the existing test suite could exercise the real code paths
  instead of being weakened to work around the environment gap.
- Verified live in the real browser (Design Test Clinic → Staff → click a row → `EmployeeModal`):
  native `<dialog>` opens with correct `aria-label` and implicit role, backdrop-click closes it,
  the explicit Close button closes it. (Escape-to-close is native browser behavior with no app
  code involved, confirmed working via the automated test suite's polyfill; it didn't visibly
  close the dialog when driven through this session's browser-automation tool specifically — most
  likely a quirk of how that tool synthesizes the Escape key rather than an app defect, since
  there's no application code left that could intercept or prevent it. Worth a 30-second manual
  click-through in a real browser tab before fully trusting it.)

**Classified as linter false positives, intentionally left unchanged** (documented here so they
aren't rediscovered and "fixed" into worse code later):
- `control-has-associated-label` ×7 (`DoctorPicker.tsx` ×2, `StaffPicker.tsx` ×1,
  `PendingDoctorsList.tsx` ×2, `DaySheet.tsx` ×2) — all land on `<td>` table cells that already
  contain properly-labeled visible content (avatar + name, or a `<Link>`/button with visible
  text), or on a `<datalist><option value=... />` (valid without text children). Not real WCAG
  violations; oxlint's jsx-a11y port doesn't special-case these.
- `PatientPicker.tsx` (lines ~165, ~180) `no-noninteractive-element-to-interactive-role` /
  `prefer-tag-over-role` on `role="listbox"`/`role="option"` — this is the standard ARIA 1.2
  combobox/listbox authoring pattern for a custom autocomplete; there's no native HTML element
  that supports per-option typeahead + custom styling the way `<select>`/`<datalist>` don't.
- `DateStrip.tsx` `role="group"` — a toggle-button toolbar; none of the "prefer instead" tags the
  rule suggests (`address`, `details`, `fieldset`, `hgroup`, `optgroup`) fit a row of buttons.

**Verification after all changes**: `npx tsc -b` clean, `npx vitest run` → **236/236 passing**
(same count as before this session — no regressions, no tests deleted to make failures
disappear), final `oxlint --jsx-a11y-plugin` pass shows only the false positives listed above.

**Remaining Part 3 scope**: the brief's typography/motion/design-token requirements were already
satisfied by Part 1's extensive polish pass: the `@theme` token system, motion (`transition-all
duration-150 ease-out`, no bounce), and generous whitespace conventions were already consistent
site-wide before this session started. (The color note below superseded the terracotta accent
described above — see Part 3b.)

## Part 3b — Color pivot: terracotta → teal + cobalt (COMPLETE, 2026-09-14)

User's verdict on "clinical terracotta" once they actually saw it: *"theme white and brown is
worst, Looking standard AI work... Need far better theme."* Went through three rounds of live,
switchable color demos (published as a Claude Artifact, not just described) before landing on a
direction they approved:

1. **Round 1** (brass/navy, emerald/warm-neutral, coral/charcoal) — rejected: "didn't like any
   of them." Too close to the same "one muted accent on a SaaS dashboard" formula, wrong colors
   besides.
2. **Round 2** (Golden Hour, Coral & Cobalt, Confident Ink) — bolder, warmer, no dark nav
   chrome. User zeroed in on Coral & Cobalt but flagged the second color was barely present
   (cobalt only touched one badge and a 9px dot) — fixed by giving each hue a real functional
   job: coral/teal = action (buttons, focus, alerts), cobalt = identity/status (avatars, role
   tags, one status pill) — then asked for more of that treatment (secondary buttons, specialty
   tags, form focus states all picked up cobalt too).
3. **Round 3**: asked to see the same two-color system with **teal instead of coral** — approved
   ("okay go with it").

**Implemented in `frontend/src/index.css`**:
- `--color-indigo-*` (the primary accent every `bg-indigo-600` etc. already uses) recolored from
  terracotta (hue 44) to a saturated teal (hue 194, chroma pulled back from the demo's boldest
  version to `0.115` at the 600 step so it stays "confident jewel teal," not neon).
- **New** `--color-cobalt-*` ramp added (hue 258) — this is a genuinely new Tailwind color name
  in the `@theme` block, not an override, since nothing in the palette previously played a
  second-accent role.
- Neutrals (`gray-*`, hue 250) and semantic colors (red/green/amber) are unchanged — already
  cool-toned, already compatible with a cool teal+cobalt pairing.

**Cobalt applied to identity/status elements** (mirroring exactly what worked in the demo — teal
never appears on these, so the two accents never compete for "what do I click"):
- The brand-mark square ("C") and signed-in-user avatar circle in all four header components:
  `PatientShell.tsx`, `StaffShell.tsx`, `AdminShell.tsx`, `PublicHeader.tsx`, plus the inline
  duplicate header in `HomePage.tsx`.
- `RoleBadge.tsx`'s `Doctor` badge (`ClinicAdmin` stays amber, `Operations` stays neutral gray —
  unchanged).
- `SessionSlotsView.tsx`'s `BOOKED` slot-status pill (`OPEN`/`COMPLETED`/`NO_SHOW` unchanged).

**Verified**: `npx tsc -b` clean, `npx vitest run` → 236/236 still passing, live-verified in
browser (staff login, homepage, staff roster's Doctor badge, a booked day-sheet slot — booked a
real test slot in Design Test Clinic to see the `BOOKED` cobalt pill render, since the first
attempt had already ticked over to `NO_SHOW` by the time it was checked). Contrast re-measured
with the same canvas-pixel-readback method as the terracotta pass (not assumed by analogy):
indigo-600-on-white 5.5:1, indigo-700-on-white 7.6:1, cobalt-600-on-white 6.1:1,
cobalt-700-on-cobalt-100 7.5:1 — all clear of WCAG AA. `DESIGN.md`'s Palette section is rewritten
with the full rationale and this contrast table; its own comment block also flags that this is a
real departure from PRODUCT.md's original "Restrained, one accent" strategy, not a silent drift
from it.

**Not done / didn't come up**: no further UI elements were audited for "should this become
cobalt too" beyond the four listed above — those were the ones a `grep` for the shell/avatar/
role-badge/status-pill patterns actually turned up. If more identity-style elements exist
elsewhere in the app (e.g. any other per-user or per-doctor colored initials), they'd still be
on teal until someone applies the same treatment.

### Demo-artifact polish after approval — did NOT touch the real app

After "okay go with it" was implemented (above), the user kept looking at the still-open
"Three Directions" Artifact (`https://claude.ai/code/artifact/e8552521-cd81-47df-b9a2-af2a370690d4`,
now trimmed down to just the Teal & Cobalt mockup) and gave two more rounds of feedback on its
**"Staff on duty" panel** — a demo-only widget (avatar + name + specialty tag + staff code) that
**does not exist anywhere in the real app**. Both rounds were fixed in the artifact only:

1. Congestion + "odd blue" complaint — the specialty tag (e.g. "General Medicine") was cobalt,
   sitting immediately next to the already-cobalt avatar; two blue elements back-to-back read as
   cluttered. Fixed by switching the tag to a plain neutral-gray pill (cobalt now appears exactly
   once per row, on the avatar) and loosening the row/list gaps and panel padding.
2. "Need new design for placing this labels" — the specialty tag and staff code were still
   crammed onto one line under the name. Restructured to three zones: avatar → (name stacked
   above specialty tag) → staff code pinned to the row's right edge in muted monospace, instead
   of stacking all the metadata on the left.

**If this "staff on duty" panel pattern is ever wanted in the real product** (it isn't currently
requested anywhere), the finished version is in the live artifact and the lesson worth carrying
over is: don't put two same-hue elements next to each other in one row, and give reference IDs
(staff codes, order numbers, etc.) their own placement rather than stacking them against a tag.

## Part 4 — Dev-server reliability fix, CLAUDE.md, first GitHub push (COMPLETE, 2026-09-15)

**1. Dev-server reliability bug, found and fixed.** The backend kept dying a short time after a
*confirmed-healthy* `bootRun` startup (Tomcat bound, "Started CmsApplication" logged, responding
to requests) — no application error, no shutdown log line, it just stopped being reachable.
Happened three times in a row across different Bash backgrounding techniques (`run_in_background`,
then `nohup ... & disown`). Root cause: launching a long-lived JVM process as a raw backgrounded
Bash command doesn't reliably survive in this sandbox across tool-call boundaries — a sandbox
quirk, not a bug in the app or in Gradle. The frontend (`npm run dev`, also Bash-backgrounded)
never had this problem in the same session, so it's specific to how this environment handles a
long-lived JVM process tree.

**Fix**: switched to `preview_start` (the tool actually meant for running dev servers) instead of
Bash, via a new `.claude/launch.json`:
```json
{
  "version": "0.0.1",
  "configurations": [
    { "name": "backend", "runtimeExecutable": "C:\\Users\\risha\\AppData\\Local\\Temp\\gradle-8.10\\bin\\gradle.bat", "runtimeArgs": ["-p", "backend", "bootRun"], "port": 8080 },
    { "name": "frontend", "runtimeExecutable": "npm", "runtimeArgs": ["--prefix", "frontend", "run", "dev"], "port": 5173 }
  ]
}
```
Two non-obvious things baked into that config: `preview_start` doesn't go through Git Bash, so it
needs the real Windows path + `.bat` wrapper for Gradle (not the POSIX `/tmp/gradle-8.10/bin/gradle`
path Bash resolves), and it runs from the repo root with no `cwd` option, so the backend entry
uses Gradle's own `-p backend` project-dir flag instead of a `cd`. Full details and the discovery
process are in the `gradle_local_bootstrap` memory file. **Caveat**: a server started this way is
reachable from the Browser pane (and from any page's own `fetch()` loaded there) but *not* from a
plain `curl` in a Bash tool call — different network context. Verify it by driving the app in the
browser, not by curling it from Bash.

Separately, `README.md` (which didn't exist in earlier versions of this handoff's context) now
documents that **`./gradlew` itself works fine** in the user's own real environment — the
`/tmp/gradle-8.10` workaround is a Claude-Code-sandbox-specific fallback, not something to tell
the user to rely on in their own terminal.

**2. `CLAUDE.md` added** (repo root) — the user asked for "a blueprint for LLM models of my
current project," which turned out to mean a project-reference document for LLMs (not a runtime
AI feature — that ambiguity was resolved by asking rather than guessing). Used the `init` skill's
process: read `README.md`, `CONTRIBUTING.md`, `.specify/memory/constitution.md`, `backend/build.gradle`,
`frontend/package.json`, and the actual module/route directory structure, then wrote a
non-redundant summary covering commands (including single-test invocations, verified against a
real test class/method name rather than an invented one), the constitution's now-project-wide
governance, the 3-JWT-realm/6-filter-chain security architecture, the frontend shell/guard split,
the Tailwind `@theme` override mechanism (including the new `cobalt` scale from Part 3b), and a
callout that the backlog's cited source BDD document (`clinic-management-system-BDD-2.md`) isn't
actually in the repo. Doesn't duplicate README/CONTRIBUTING — points to them instead.

**3. Project pushed to GitHub for the first time.** No git repository existed anywhere in this
project before this session (confirmed via `git status` → "fatal: not a git repository" —
consistent with every earlier handoff note's git-related caveats). User provided a target remote,
`https://github.com/rish1789/CMS2.git`. Before the initial commit:
- `backend/` had **no `.gitignore` at all** — would have committed `backend/build/` (4.6M),
  `backend/bin/` (4.1M), and `backend/.gradle/` (the local Gradle cache). Added one covering
  `build/`, `bin/`, `.gradle/`, `*.log`, and common IDE cruft.
- Root `.gitignore` only covered `.env`/`.env.*` — added `.impeccable/` (the design-hook tool's
  own cache directory, not source; it also appears nested under `frontend/src/` and
  `frontend/src/features/`).
- Verified before committing: `.env` genuinely excluded (`git check-ignore` confirmed),
  `.env.example` included, no `node_modules`/`build`/`bin`/`dist` in the staged 1289 files, no
  actual secret/credential files (a few source files with "Password"/"Credentials" in their
  *names* — `PasswordPolicyValidator.java` etc. — are legitimate application code, not secrets).
- `git init` → initial commit → `git branch -M main` → `git remote add origin
  https://github.com/rish1789/CMS2.git` → `git push -u origin main`. Confirmed with `git log` and
  `git status` afterward: clean working tree, `main` tracking `origin/main`.

**The repo is `main`-only right now** — no branch protection, no CI status yet observed on
GitHub's side (the `.github/workflows/ci.yml` referenced by `README.md` should run on this push;
worth checking Actions on GitHub next session if that matters). No PR was opened since this was
the initial commit directly to `main`, not a feature branch.

## Part 5 — Spec-kit redesigns, real bug fixes, password-reset feature, walk-in audit (2026-09-16)

### 1. Three full-page redesigns for `056-design-copy-quality-pass`

Started as `/speckit-orchestrate backlogs`, which found `053-visual-design-copy-quality-pass`
(spec'd as `specs/056-design-copy-quality-pass`) as the only unconverged backlog item, correctly
requiring discussion before implementation per its own business rules. Discussion repeatedly
pivoted into direct rebuild requests, each sourced from a published Claude Artifact reference
(saved locally to `design/*.html` per the user's own suggested convention) and each documented as
a spec amendment (new FRs/tasks) rather than a silent scope drift:

1. **Landing/login consolidation** — `HomePage.tsx` fully rewritten (patient-booking hero, new
   `BookingIllustration()` SVG, real `trustItems`, single "Clinic login" link consolidating what
   used to be separate clinic/admin entry points). New shared `BrandHeader.tsx`/`BrandFooter.tsx`
   extracted for reuse. `StaffLoginPage.tsx` gained a "Register your clinic" link since the
   homepage no longer links there directly. Two real CSS bugs found and fixed along the way: an
   `inline-flex` link's underline stretching past the visible arrow on wrap (fixed with plain
   `inline`), and the `margin-top` that fix then broke (`inline` elements ignore vertical margin —
   fixed by wrapping in a block-level `<p>`).
2. **Patient login redesign** — `LoginForm.tsx` rebuilt as a card ("Welcome back." heading,
   "Forgot password?" wired to an honest toast — no reset flow exists at the patient level —
   "Create an account" link). `PatientLoginPage.tsx` uses the new `BrandHeader`/`BrandFooter`.
   Tightened twice after live feedback ("Log in"/"Looking for a doctor?" read as cramped, then "no
   scroll but keep scroll bar") — reduced padding/spacing/font-size across the card until it fit
   the viewport without scrolling.
3. **Patient dashboard redesign** — personalized "Welcome back, {name}." heading (new
   `deriveDisplayNameFromEmail()` in `token.ts`, since `PatientAccount` has no name field — same
   "honest fallback, not fabrication" pattern as the login toast above), tiles reordered, custom
   card styling (not the shared `Card` component, to avoid touching unrelated staff/admin
   screens). `PatientShell.tsx` header restyled to match `BrandHeader`.
   `NextAppointmentCard.tsx` restyled to a bold indigo "next visit" strip — its existing
   fetch/soonest-upcoming logic was untouched, only the presentation changed.

**Not yet run**: `/speckit-converge` was never run against this feature's final, three-times-
pivoted scope. `backlog/progress.md`'s row for `053-...` still reads "Implementing," not
"Converged" — do that before considering this feature done.

### 2. Real bug: a slot could be marked completed before its scheduled start time

User-reported, with an exact repro: a booking for "Karan Singh" scheduled at 15:15 was marked
Completed by a clinic admin at 15:03 — 12 minutes early. Root cause: `SlotCompletionService` only
checked the slot's `BOOKED` status, never its scheduled time. Fixed with a guard comparing
`LocalDateTime.now()` against the slot's computed scheduled start
(`backend/src/main/java/com/cms/scheduling/service/SlotCompletionService.java`), a new
`SlotNotYetStartedException` (409, mapped in `ScheduleExceptionHandler`), and a matching frontend
error message in `session-delay/api.ts`. Integration test added
(`SlotCompletionRejectionTest.completingABookedSlotBeforeItsScheduledStartTimeIsRejected`) —
written, compiling, unexecuted per this sandbox's standing Docker/Testcontainers limitation.

### 3. New feature: Super Admin can reset a ClinicAdmin's password

Triggered by investigating a real Postman 401 for Crystal Health Centre — root cause turned out to
be a data-model semantic gap, not a bug: `Clinic.contactEmail` (public info) and the ClinicAdmin's
actual `Account.email` (login credential) are separate fields that can differ, and did here
(`info@crystalhealth.in` vs. the real login `vikas.sharma@mail.com`). Since the system has no
self-service "forgot password" flow at any level, and direct DB/curl password manipulation is
correctly blocked by this environment's safety guardrails ("[Credential Materialization]"/
"[Secret-Store Writes]" — not routed around, per instructions), the actual fix was a real feature:

- Backend: `ClinicVerificationService.resetClinicAdminPassword(clinicId)` — finds the clinic's
  active `ClinicAdmin` `RoleAssignment`, generates a policy-compliant password via the existing
  `TemporaryPasswordGenerator` (same one `StaffOnboardingService` already uses), encodes it, saves
  it, returns it once. New endpoint `POST /api/v1/admin/clinics/{clinicId}/reset-admin-password`,
  new `ClinicAdminAccountNotFoundException` (404), 3 new integration tests (written, unexecuted
  per the standing Docker limitation).
- Frontend: `resetClinicAdminPassword()` in `clinic-verification/api.ts`, a new
  `ResetPasswordResultModal.tsx` (shown once, same "hand this to the admin directly, it cannot be
  retrieved again" contract as staff onboarding), wired into `PendingClinicsList.tsx`'s Verified
  tab as a "Reset admin password" row action.
- Used live (not just tested) to reset all three of this environment's verified clinics' admin
  passwords — see the "Live test data" section above.

### 4. Walk-in management: one real display gap fixed, three real operational gaps identified

User reported (with screenshots) that the "Insert a walk-in" flow "didn't assign the walk-in
patient to a doctor — assigned to Clinic admin or staff maybe." Investigation
(`WalkInInsertionService.java`, `WalkInForm.tsx`, `InboxItemResponse.java`, `InboxItemCard.tsx`)
found **no actual assignment bug**: a walk-in's doctor is derived entirely from the `Session` the
staff member picks (`session.getDoctorProfile()`), never from any client-supplied value — there is
no code path by which a walk-in could land on a non-doctor. The real problem was that **the
doctor's name was never displayed anywhere** — neither the Inbox card nor the walk-in success
screen showed it, which reasonably read as "did this even go to a doctor?" Fixed:

- `InboxItemResponse.walkInSummary()` now includes `doctorName`
  (`Booking.slot.session.doctorProfile.account.name` — already-eager JPA associations, no N+1).
- `BookingResponse` (shared by walk-in, staff-booking, patient-booking, and waitlist-claim
  endpoints) gained `patientName`/`doctorName` fields — purely additive, verified to affect no
  existing consumer or test.
- `InboxItemCard.tsx` and `WalkInForm.tsx`'s success screen both now display the doctor (and
  patient) name. `InboxItemCard.tsx`'s "Claim" button relabeled "Claim task" plus new hint text
  clarifying that claiming/resolving is front-desk task coordination (038's own claim-based work-
  item model), not a re-assignment step — this was genuinely confusing given the "Claim" wording
  sat directly under a patient/doctor summary line.
- Contract docs updated: `specs/039-unified-realtime-inbox/contracts/inbox.md`,
  `specs/025-walk-in-priority-insertion/contracts/walk-in-insertion.md`.
- Verified live end-to-end in-browser (not just unit tests): logged into Star Clinic as
  `harshSingh@mail.com`, inserted a real walk-in, confirmed both the success screen and the Inbox
  card correctly showed "Gauresh Kumar."

**Then the user pushed further** ("the walk in feature... not very well designed... will create
confusion and mismanagement in operation") and named four concrete complaints. Investigation
confirmed all four point at real, separate functional gaps — **none of these are fixed yet**:

1. **Incomplete form** — `WalkInForm.tsx` never shows which doctor/session/date it's inserting
   into anywhere on the form itself (only after submission). Staff can't visually confirm context
   while filling it out.
2. **"No proper assignment to the doctor"** (perception, not a data bug — see above) — explained
   by #1: nothing on the form reassures staff of the doctor at data-entry time.
3. **"No proper table records"** — real gap. `Booking` (`backend/src/main/java/com/cms/booking/
   domain/Booking.java`) has no field marking "this came from a walk-in." A walk-in that lands in
   a buffer or reclaimed no-show slot is **completely indistinguishable** from a normal pre-booked
   appointment everywhere else in the system (`SessionSlotsView.tsx`'s Day Sheet table has no
   "Walk-in" badge at all). Only a tier-3 override-reason walk-in leaves any trace
   (`Booking.overrideReason`), and even that isn't surfaced in the table. Proposed fix: a new
   `Booking.source` field (`SCHEDULED`/`WALK_IN`) — needs a migration.
4. **"No proper resolving of the walk-in"** — real gap, most serious of the four.
   `InboxItemService.resolve()` only flips the Inbox item to `RESOLVED`; it does **nothing** to the
   underlying booking. Marking a visit actually complete is a fully separate action (Day Sheet's
   "Mark complete," `SlotCompletionService`, from feature 023). The two states can silently drift:
   an Inbox item can be resolved (disappears from the task list) while the visit is never marked
   complete (breaks delay tracking/reporting), or vice versa. Proposed fix: either couple the two
   actions, or at minimum surface the visit's completion status directly on the Inbox card.

**Status at session end**: diagnosis complete and confirmed correct against the actual code for
all four points; remediation plan proposed (form header showing doctor/session context; new
`Booking.source` schema field + Day Sheet badge; connecting or cross-surfacing Inbox-resolve and
slot-complete). Waiting on the user's go-ahead on scope/sequencing before implementing — the
schema change in particular needs a migration and touches more than the walk-in feature alone.

## Part 6 — Walk-in remediation, a real security fix, schedule merge feature, human-error-catching warnings (2026-09-17)

Long session, many independent user-reported issues investigated and fixed in sequence. Grouped
by theme, not strictly chronological.

### 1. Implemented Part 5's walk-in remediation plan (items 1, 3, and half of 4)

Part 5 ended with four diagnosed-but-unimplemented walk-in gaps. This session implemented three
of them, after the user paused mid-implementation once ("we didn't agree on anything" — only one
sub-question had actually been answered) then said "continue with your work" once I'd listed
exactly what was touched:

- **Form context header** — `WalkInForm.tsx` now shows the doctor/session/date it's inserting
  into, visible while filling out the form (previously only appeared after submission).
- **`Booking.source` field** (`SCHEDULED`/`WALK_IN`) — new column (migration), set by
  `WalkInInsertionService`, surfaced as a "Walk-in" badge on the Day Sheet
  (`SessionSlotsView.tsx`). A walk-in booking is no longer indistinguishable from a normal one.
- **Inbox visit-status display** — shown as its own, separate element on the Inbox card, **not**
  merged into the existing "Resolve" action, per explicit instruction: "we cannot put everything
  to same box." Resolving an Inbox item and marking a visit complete remain two independent
  actions; this only makes both states visible at once, it doesn't couple them (item 4 from Part
  5's plan — coupling the two actions — was **not** done, only the visibility half).

### 2. Real security bug: a patient could book a doctor's reserved walk-in capacity directly

Found while the user was screenshotting an unrelated booking, then flagged as suspicious. Buffer
slots (the walk-in-only reserved capacity `SlotGenerationService`/`WalkInInsertionService`
create/consume) were never actually protected from ordinary booking:

- `SlotRepository.findOpenFixedTimeSlots`/`findOpenFixedTimeSlotsOnDate` (what patients browse)
  never excluded `isBuffer` slots — now do (`AND s.isBuffer = false`).
- Neither `PatientBookingService.bookSlot` nor `StaffBookingService.bookSlot` (the write paths)
  checked `isBuffer` at all — a patient could browse straight to it and book it. Both now reject
  it with a new `BufferSlotNotDirectlyBookableException` → `409 SLOT_RESERVED_FOR_WALK_IN`.
- Separately, `SessionSlotsView.tsx` had a **pre-existing display bug**, unrelated to the security
  gap but surfaced by it: a booked buffer slot always showed "Reserved capacity"/"No direct
  booking" instead of the real patient's name — true for a legitimate walk-in insertion too, not
  just the exploit. Fixed to check `slot.booking` before falling back to the placeholder text.
- Live-verified: created a throwaway patient account, confirmed the previously-exploited slot no
  longer appears in `GET .../patients/clinics/{clinicId}/slots` and a direct POST to book it now
  403/409s. The user's own real test booking (their "Karan Singh" / 18:00 slot) was then cancelled
  through the app's normal cancel action — the underlying `Booking` row is retained (by design,
  audit trail), so the session itself still can't be hard-deleted; left as-is per the user's own
  call after being told exactly why.
- Contracts updated: `specs/021-patient-self-service-booking/contracts/patient-booking.md`,
  `specs/020-staff-assisted-fixed-time-booking/contracts/staff-booking.md`.

### 3. New feature: Schedule break-window (merge two schedules into one)

User complaint: Dr. Furaka Singh showed as **two separate Day Sheet rows per day** (a 9:30–14:00
schedule and a separate 16:00–18:00 one either side of a lunch gap) — asked for either a break-
window feature or a way to merge them. Built the break-window option:

- `Schedule`/`Session` gained nullable `breakStartTime`/`breakEndTime` (migration `V33`) —
  `SlotGenerationService.computeSlotStartTimes` skips (and correctly jumps past, not truncates) any
  candidate slot that would overlap the break. `ScheduleService` validates the window (both-or-
  neither, strictly ordered, contained within start/end, `FIXED_TIME`-only).
- `ScheduleForm.tsx` gained an "Add a break" toggle; `DoctorScheduleManager.tsx` (new page,
  replacing a dead-end blank-form bug found along the way — `ScheduleController`'s edit endpoint
  existed with zero tests exercising it end-to-end, and a real seconds-vs-minutes `<input
  type="time">` bug in `ScheduleForm`'s edit-mode initializer) lists/edits a doctor's schedules.

**Session/Schedule deletion, safety-gated** — a Schedule edit is deliberately non-retroactive
(existing `Session` rows keep their old wrong values forever), so merging two schedules into one
needed a way to delete the now-redundant one and its stale `Session`s:

- `SessionDeletionService` (new) — deletes a `Session`+its `Slot`s outright, but **blocks** if any
  `Booking` or waitlist offer (any status, including cancelled/lapsed) ever touched it — mirrors
  `ClinicVerificationService.deleteGuarded`'s "block, don't cascade" pattern.
- `ScheduleDeletionService` (new) — deletes a `Schedule` and every `Session` generated from it,
  but for a `Session` with real activity, **detaches** it (`session.schedule = null`, migration
  `V34` makes the FK nullable) instead of blocking the whole deletion — a `Session` already
  snapshots everything it needs, so it stays valid, complete audit-trail data with no parent
  `Schedule` row. Found and fixed a real transaction bug along the way: calling the `@Transactional
  REQUIRED` `SessionDeletionService.deleteSession` from inside `ScheduleDeletionService`'s own
  transaction and catching its exception doesn't work — Spring's AOP proxy marks the *shared*
  physical transaction rollback-only the instant the exception is thrown, regardless of the caller
  catching it, so the outer transaction then fails to commit with `UnexpectedRollbackException`.
  Fixed by doing every check-then-act step directly inside `ScheduleDeletionService`'s own single
  transaction instead of delegating to the other service's transactional method.
- Used live to actually fix Furaka Singh's real data: deleted the redundant PM schedule, edited
  the AM schedule into one Mon–Sat 09:30–18:00 schedule with a 14:00–16:00 break, deleted ~28
  stale-generated `Session`s one-by-one through the real UI (a bulk-script approach was correctly
  blocked by the auto-mode safety classifier as an unverifiable deletion scope — not routed
  around), then re-triggered session generation. Every date from the day after "today" onward now
  shows one correct merged row; **today's own date is a permanent, harmless exception** — its
  already-generated session has real (test) booking history attached and can never retroactively
  merge, by the same audit-trail rule above.

### 4. Frontend-only UX fix: "Mark complete" looked live before a slot's own start time

Backend (`SlotCompletionService`, fixed in Part 5) already correctly rejects completing a slot
before its scheduled start (`SLOT_NOT_YET_STARTED`). The Day Sheet UI never hid the button ahead
of time, though — clicking it before the slot started just bounced back with that error. Fixed in
`SessionSlotsView.tsx`: shows a plain "Starts at HH:MM" label instead of the live link until the
scheduled time actually passes.

### 5. New feature: staff-console "Reset staff password" (ClinicAdmin → Doctor/Operations)

Needed to actually log in as a treating doctor to verify an access-control fix (see item 6 below)
and discovered there was no way to do that — only Super Admin could reset a password, and only
for a ClinicAdmin's own account. New `StaffPasswordResetService`/`StaffPasswordResetController`
(`POST .../staff/{accountId}/reset-password` and `.../set-password`), scoped to an active
ClinicAdmin acting on Doctor/Operations staff at their own clinic — deliberately **excludes** a
fellow ClinicAdmin target (no override; a password reset is a silent full account takeover, more
sensitive than deactivation, and stays Super-Admin-only). Wired into `EmployeeModal.tsx`'s Actions
tab, reusing (and generalizing) the existing `ResetPasswordResultModal`. Used live to set a real
doctor's password and log in as them to confirm the treating-doctor access path.

### 6. Three clinical-record forms stayed live/submittable under an access-denied banner

`ConsultationNoteForm.tsx`/`PrescriptionForm.tsx`/`ExternalRecordReferenceForm.tsx` correctly
reject anyone but the treating doctor server-side (`TreatingDoctorAuthorizationService`: "no
ClinicAdmin or peer-doctor override of any kind" — a deliberate privacy boundary, confirmed not a
bug), but on a `FORBIDDEN` response each form showed that message as a banner **above a form that
was still live and submittable** — inviting a doomed resubmit of the same denied request. Fixed
all three to fully replace themselves with just the message, mirroring the existing
`bookingNotFound` pattern already in the same files.

### 7. New feature: "Today's patients" roster on Find a Patient

Default view above the existing name/phone search — every patient with an active booking today
(appointment-based or walk-in), across every doctor, so front-desk staff has something to look at
before typing anything. New `GET /api/v1/clinics/{clinicId}/patients/today`
(`TodayPatientsController`/`BookingRepository.findActiveByClinicAndSessionDate`) + new
`TodayPatientsTable.tsx`.

### 8. Real bug: Day Sheet's doctor search bar was blind to just-onboarded/just-edited doctors

User added a new doctor (Kamlesh Rawat) and edited an existing one's schedule (Gauresh Kumar);
neither appeared in the Day Sheet's own doctor list or its "Filter by doctor" search bar. Root
cause: both the list and the search bar's option set were sourced from `listSessions`' own
response, which only ever includes doctors who already have a **generated** `Session` in the
14-day window — session generation is nightly/manual-trigger only, so a doctor with a real,
correct `Schedule` but no `Session`s yet was invisible with no indication anything was wrong
(fixed for that specific case by manually re-triggering generation). The **search bar itself**
was then fixed structurally: it now sources its option list from `listClinicDoctors` (every
doctor actually staffed, independent of session generation) instead, plus added debounced
(300ms) server-side search via that endpoint's own `q` param, per the user's explicit "need more
power of feature in search bar" ask — a doctor outside the initial page/roster size is still
findable by typing.

### 9. New feature: "Booking setup incomplete" warning on the staff-console Doctors page

Direct follow-on from item 8 — the newly-visible doctor (Kamlesh Rawat) then hit a genuinely
empty "Appointment type" dropdown on the patient booking page, because nobody had configured any
appointment types or a default fee for him yet. Confirmed as human error, not a code bug
(`StaffOnboardingService` deliberately never creates speculative billing data) — the user asked to
"nullify" the human error, i.e. make it structurally visible instead of relying on someone
remembering. New `GET /api/v1/clinics/{clinicId}/doctors/booking-readiness`
(`DoctorBookingReadinessService`, in the `booking` module since it needs `AppointmentType`/
`DoctorDefaultFee`, not `identity.doctor`) + an amber "Booking setup incomplete" badge on
`DoctorPicker.tsx`. **Caught and fixed a real false positive during live verification**: the first
version flagged any doctor with no clinic-wide default fee, which incorrectly flagged Gauresh
Kumar too — every one of his appointment types already carries its own fee override, so
`FeeResolutionService` never needs the default fee for him and he's actually fully bookable.
Tightened to the accurate rule: not-ready only when at least one appointment type has *neither*
its own override *nor* a default fee to fall back on. **Kamlesh Rawat's own data is still
unfixed** — the badge correctly flags him, but no appointment type/fee has actually been set up
for him yet; do that next session if asked.

### Testing note

Every new/changed piece of backend logic above has unit test coverage (pure Mockito, executable
in this environment) plus integration test coverage where the existing convention calls for it
(written, compiling, unexecuted — the standing Docker/Testcontainers sandbox limitation, unchanged
from every prior session). Frontend: full suite was 334 passing at the start of this session's
frontend work and 353 passing at the end, run to green after every single change in this Part,
never left red. Backend: `compileJava`/`compileTestJava`/`spotlessCheck` all green after every
change; new unit tests (ScheduleService, SlotGenerationService, ScheduleDeletionService,
TodayPatientsController, StaffPasswordResetService, DoctorBookingReadinessService, and others)
run and passing, not just compiling.

## Part 7 — Fixed Kamlesh Rawat's booking setup (2026-09-18)

Follow-up from Part 6 item 9: the new "Booking setup incomplete" badge correctly flagged Dr.
Kamlesh Rawat (Gastroenterology, Star Clinic, `DR-1152`) — he had zero appointment types and no
default fee, so patients hit an empty dropdown when trying to book him. Pure data gap, not a code
bug.

**Fixed**: added two appointment types via the staff console (`Doctors` → `Appointment types`),
mirroring Gauresh Kumar's existing pattern at the same clinic exactly (fee overrides, no clinic
default fee needed):
- General Consultation — ₹500 override
- Follow-up — ₹300 override

**Verified live**: the amber "Booking setup incomplete" badge is gone from his row on the Doctors
page; the staff booking form's appointment-type dropdown for his session now lists both options
correctly (checked the dropdown populated, didn't submit an actual test booking).

**Process note**: Star Clinic's ClinicAdmin password (`harshSingh@mail.com`) had to be reset via
Super Admin to log in and do this (its prior temp password, from Part 5, was never recorded per
the "shown once" contract). Reset to a known temp value to do the work, then reset again to a
fresh random value afterward — nothing durable was left behind. Reset again from
`/super-admin-console/clinics` (Verified tab) next time it's needed.

## Part 8 — Login error-message split, and a new feature (057-day-sheet-status-overhaul) taken through specify→plan→tasks→implement (2026-09-21)

**Session paused mid-flow — token budget ran low.** Two pieces of real work landed; one is fully
shipped, the other is implemented-and-tested but **not yet run through `/speckit-analyze` or
`/speckit-converge`**. Read the "What's actually left" list at the end of this Part before doing
anything else next session.

### 1. Staff/patient login: split "account not found" from "wrong password" (COMPLETE)

Product owner explicitly asked for this, after being told it's a deliberate user-enumeration
tradeoff versus the original combined "invalid credentials" design (FR-004/FR-007 in the
original 002/040 specs) — confirmed and accepted the tradeoff for both staff and patient login.

**Backend**:
- New `AccountNotFoundException`/`IncorrectPasswordException` in both
  `identity/account/exception/` (staff) and `patient/account/exception/` (patient), replacing
  the deleted `InvalidCredentialsException` in each.
- `StaffAuthService.login`/`PatientAccountService.authenticate` throw the right one;
  `StaffExceptionHandler`/`PatientExceptionHandler` map to `401 ACCOUNT_NOT_FOUND` /
  `401 INCORRECT_PASSWORD`.
- Real edge case handled: typing the **Super Admin username** with a wrong password now
  correctly reports "Incorrect password," not "account not found" — required adding
  `SuperAdminAuthenticationService.identifierMatches()` so `StaffAuthService` can tell "not the
  Super Admin at all" apart from "is the Super Admin, wrong password" without that module
  leaking the password-match result directly (keeps `com.cms.identity.admin`'s one-narrow-
  contract design intact).
- 9 backend test files updated/added (unit `StaffAuthServiceTest`, contract
  `StaffAuthControllerTest`/`PatientAccountContractTest`, integration `StaffLoginTest`/
  `StaffCodeLoginWrongPasswordTest`/`StaffCodeLoginUnknownCodeTest`/
  `SuperAdminResolvedLoginTest`/`PatientLoginTest`, unit `PatientAccountServiceTest`) — all
  passing (unit/contract executed; integration compiled, standard Docker-sandbox caveat).

**Frontend**: `LoginStaffErrorBody`/`LoginPatientErrorBody` types updated; both forms already
displayed the backend's `message` verbatim so no UI logic changed, just types + one stale test
(`PatientAccountForms.test.tsx`).

**Docs updated**: `specs/002-patient-account-login/contracts/patient-account.md`,
`specs/040-super-admin-rbac-login/contracts/clinic-portal-login.md` — both flagged as a
2026-09-21 product-directed change superseding the original no-leak design.

**Live-verified** in-browser: all four cases (staff unknown identifier, staff wrong password,
Super Admin wrong password, patient unknown email, patient wrong password) showed the correct
distinct message, including a fresh signup used specifically to prove the "known email, wrong
password" path.

### 2. New feature: Day Sheet Smart Status Flow (`057-day-sheet-status-overhaul`)

Not from `backlog/*.md` — a live, conversational feature request (product owner wanted a
"smart button" replacing "Mark complete": Appeared → auto-Completed, doctor self-service
completion, checkbox-based bulk cancel). Ran through `/speckit-specify` → `/speckit-plan` →
`/speckit-tasks` → `/speckit-implement` directly (no separate `/speckit-clarify` call — the 3
resolved ambiguities were embedded straight into the specify pass's own Clarifications section
and answered live in the same turn). **`/speckit-analyze` and `/speckit-converge` have NOT been
run yet** — do that first next session, before treating this as done.

**Spec** (`specs/057-day-sheet-status-overhaul/spec.md`) — 3 clarifications resolved: a
mistakenly auto-No-Show slot is correctable back to Appeared; "select all" in the new checkbox
UI is a bulk-select convenience over the existing per-slot cancel rule, **not** an invocation of
the separate whole-day-cancellation feature; the new cancel-selection UI stays
ClinicAdmin/Operations-only (doctors get none, even though the *existing* single-cancel endpoint
today allows any active role — deliberately not touched).

**What shipped** (46/46 tasks, `specs/057-day-sheet-status-overhaul/tasks.md`):
- New `SlotStatus.APPEARED` (pure enum addition — `slot.status` is an unconstrained
  `VARCHAR(20)`, confirmed against `V10__create_slot.sql`, so **no migration** was needed at all).
- `SlotAppearedService`/`SlotAppearedController` (`POST .../slots/{slotId}/appeared`,
  ClinicAdmin/Operations-only, accepts `BOOKED` or `NO_SHOW` as source).
- `SlotAutoCompletionService`/`SlotAutoCompletionTrigger` — a second per-minute `@Scheduled`
  sweep, structurally identical to the existing `NoShowDetectionService`/`NoShowDetectionTrigger`
  (same non-`@Transactional`-outer/per-candidate-save shape, deliberately — that exact
  self-invocation bug class has bitten this codebase twice before).
- `SlotCompletionService.requireAuthorized` extended: ClinicAdmin/Operations keep their
  existing direct `BOOKED→COMPLETED` path **unchanged** (additive, nothing removed); a treating
  doctor is newly allowed but **only** from `APPEARED` (never a still-`BOOKED` slot) — the
  doctor-identity check is implemented locally inside `scheduling` rather than reusing
  `com.cms.clinical.service.TreatingDoctorAuthorizationService`, to avoid a
  `scheduling→clinical→booking→scheduling` module cycle.
- `BookingCancellationService`'s cancel-eligibility guard widened to accept `APPEARED` (was
  `BOOKED`-only) — the existing `cancelIfActive` race-guard/`BookingCancelledEvent` publication
  is otherwise untouched.
- New `BatchBookingCancellationService`/`BatchBookingCancellationController`
  (`POST .../sessions/{sessionId}/bookings/cancel-batch`) — iterates
  `BookingCancellationService.cancel(Booking)` per booking id (already its own `REQUIRES_NEW`
  transaction, from 033's cascade work), collecting per-booking success/failure rather than
  failing the whole batch. **Deliberately narrower** than the existing single-cancel endpoint:
  ClinicAdmin/Operations only, no doctor branch — the existing single-cancel endpoint's "any
  active role" authorization is untouched.
- **Real gap caught twice by the contract tests themselves**: both new POST endpoints
  (`/appeared`, `/cancel-batch`) were initially missing their `SecurityConfig` matcher and would
  have silently fallen through to `anyRequest().permitAll()` — the exact same class of bug this
  codebase has caught in 3-4 prior features. Fixed both before moving on; there's now a comment
  at each site pointing at the pattern.
- Frontend: `AppearedButton.tsx` (mirrors `CompleteSlotButton.tsx`), wired into
  `SessionOperationsPanel.tsx` alongside it (both buttons render unconditionally — the panel
  never knew the slot's live status before this feature either, so this matches its existing
  "just try it, the backend enforces real eligibility" design). `ClinicShell.tsx` now passes the
  caller's resolved `role` down via `<Outlet context={{ role }}>` — **the first real per-page
  role-based UI gating in this codebase** (previously only the sidebar's nav-item filter and
  backend 403s did any role gating at all). `SessionSlotsView.tsx`: role-conditional Appeared
  action (hidden for Doctor — the read-only status *badge* text stays visible to every role, a
  deliberate reading of FR-007's "actions and labels" as the actionable control, not the
  informational badge, since hiding real attendance state from a treating doctor seemed like a
  worse outcome than the FR's wording strictly requires — flag this interpretation to the
  product owner if it matters), new per-row selection checkbox (`BOOKED`/`APPEARED` only, hidden
  for Doctor) replacing the old inline "Cancel" link, new `BatchCancelBar.tsx` component.

**Testing**: 108 backend unit+contract tests, all green (`./gradlew test`, this session's own
run). Backend integration tests + this session's new ones (`SlotAppearedRemovesNoShowEligibilityTest`,
`SlotAutoCompletionTest`, extended `SlotCompletionAuthorizationTest`, extended
`BookingCancellationServiceTest`, new `BatchBookingCancellationServiceTest`/
`BatchBookingCancellationSuccessTest`/`BatchBookingCancellationPartialFailureTest`/
`BatchBookingCancellationAccessTest`) are written and compile clean but unexecuted — the
standing Docker/Testcontainers sandbox limitation, unchanged from every other feature in this
project. Frontend: 370/371 passing (`npm run test -- --run`) — the 1 failure is the pre-existing,
unrelated `PatientHubPage.test.tsx` date-bomb (see below, already flagged as a background task).

**Live end-to-end verification was NOT completed.** Got partway through real dev-server setup
(onboarded a new doctor "Dr Test Verify"/`DR-6247` at Sunrise Family Clinic, defined a Mon–Sun
00:00–23:45 Fixed-Time/15-min schedule), but the Super Admin "Generate sessions now" trigger
returned a `Request failed (500)` against the full multi-clinic dev dataset and the walkthrough
was stopped there (per explicit instruction not to chase it) rather than debugged. **This is
untouched dev-data/environment friction — no automated test above exercises that same code path,
and none of them are failing** — but it does mean the actual browser click-through (Appeared →
auto-Completed timing, the real checkbox/batch-cancel bar, doctor-view hiding) has only been
proven at the component/API level, not end-to-end in a real browser. Do that first next session,
and investigate the 500 on session generation (check backend logs — server was stopped without
capturing the stack trace for that specific failure).

### 3. What's actually left, in order

1. **Debug the session-generation 500** (Super Admin console → Trigger session generation →
   Generate sessions now), then finish the live quickstart walkthrough
   (`specs/057-day-sheet-status-overhaul/quickstart.md`, all 4 scenarios) against a real booked
   slot.
2. **Run `/speckit-analyze`** against 057's spec/plan/tasks (never run this pass at all this
   session — went straight from tasks to implement).
3. **Run `/speckit-converge`** against 057 once analyze is clean.
4. A user turn asked to re-run `/speckit-tasks` with no argument right after 057's tasks.md hit
   46/46 done — flagged that this would overwrite the completed task list, the user's answer was
   dismissed/unanswered. If they bring this up again, ask what they actually meant (a different
   feature? intentional regeneration?) rather than assuming.
5. A background task chip was spawned (not yet actioned): fix the hardcoded `2026-09-20` date in
   `frontend/tests/patient-search/PatientHubPage.test.tsx`'s `UPCOMING_BOOKING` fixture — it's
   now in the past relative to real time and makes that one test flaky/failing. Unrelated to
   either piece of work in this Part.
6. **Still outstanding from Part 4**: 683+ files uncommitted since the initial commit, never
   pushed past that first commit. Untouched again this session — flagging again so it doesn't
   get lost a second time.
7. Dev servers were stopped cleanly at the end of this session (no stray processes). New
   dev-data created this session, in case it's useful or needs cleanup: clinic "Sunrise Family
   Clinic" (`9714c97a-7da5-49eb-9f28-c31a1e74efd4`, ClinicAdmin `riya.sharma@sunriseclinic.test`
   / `TempPass!2026`), doctor "Dr Test Verify" (`DR-6247`) with the schedule described above but
   **no generated sessions** (that's the 500 above), and a throwaway patient account
   `test.patient@example.com` / `Str0ng!Pass` used only to prove the login-error-split flow.

## Part 9 — 057's 500 root-caused and fixed; analyze run; convergence found a real bug (2026-09-22)

Ran `/speckit-analyze` on 057: zero CRITICAL findings, all 15 FR + 5 SC traced to tasks, all
tests green. One HIGH finding (I1): T021/T027/T042 were checked `[x]` despite tasks.md's own
Notes admitting the live quickstart walkthrough never finished (blocked on the session-generation
500). Ran `/speckit-converge`: no code gaps against spec/plan/tasks — the only remaining item was
that same unresolved 500, not fixable by `/speckit-implement` since it required a live repro. User
asked to permanently fix it.

**Root cause found and fixed**: `SlotGenerationService.computeSlotStartTimes`
(`backend/src/main/java/com/cms/scheduling/service/SlotGenerationService.java`) built its slot
list using `LocalTime.plusMinutes()`/`isAfter()`, which wraps at midnight. The "Dr Test Verify"
dev-data schedule from last session (`00:00-23:45`, 15-minute interval) makes the last slot
boundary land exactly on `24:00` → wraps to `00:00` → the loop's termination check never fires →
infinite loop → heap exhaustion → the 500. Rewrote the loop using non-wrapping minute-of-day
integer arithmetic; added a regression test with a 2-second `@Timeout` guard
(`SlotGenerationServiceTest.aScheduleWhoseLastSlotBoundaryLandsExactlyOnMidnightTerminates`) so a
future reversion fails fast instead of hanging CI. All backend unit/contract tests green
(112 tests), zero regressions. Verified live: restarted the backend, re-ran "Generate sessions
now" from the Super Admin console against the exact same schedule that used to 500 — now succeeds
("15 sessions created for Tuesday").

Appended this as `Phase 7: Convergence` in `specs/057-day-sheet-status-overhaul/tasks.md`
(T047 done, T048/T049 open).

**Continued live verification, found a second (unrelated) bug**: booked a real slot on Dr Test
Verify's schedule (also had to add a missing appointment type for that doctor — dev-data gap, not
a bug) and re-opened the Day Sheet. The booked slot's row (10:00-10:15) doesn't sort
chronologically in `GET .../day-sheet`'s response once a background job changes its status — it
disappeared from its expected position between 09:45 and 10:15 (still present in the JSON, just
out of order; likely a missing `ORDER BY` in the query/repository method backing
`SessionDaySheetController`). This is a **pre-existing Day Sheet defect surfaced by, not
introduced by, 057** — flagged as a background task chip (`task_126094d9`) rather than fixed
inline, to stay in scope. T021/T027/T042's live walkthrough is *still* not fully complete: the
re-attempt's test booking auto-flipped to NO_SHOW (10-minute grace period elapsed) before Appeared
could be clicked — a fresh, better-timed attempt is needed (T049).

### T049 completed, and two more real bugs found and fixed (2026-09-22, same day)

Ran T049 for real this time. All 4 quickstart.md scenarios verified live against running dev
servers — not just the automated suite. Along the way, live testing (as designed) surfaced two
more genuine implementation gaps in 057 itself, both fixed and tested:

- **T050 (FR-007 violation)**: `SessionOperationsPanel.tsx` showed "Mark appeared" to a doctor who
  reached it directly via their own "Mark completed" link on an Appeared slot — the table-level
  hiding in `SessionSlotsView.tsx` didn't cover this separate route. Fixed by threading `role`
  from `ClinicShellOutletContext` through `SessionOperationsPage` into a new `isDoctor` prop.
- **T051 (FR-008 gap)**: staff had silently lost the pre-057 direct `BOOKED -> Completed` shortcut
  in the UI (only `APPEARED` rows had a "Mark complete" link) even though the backend always
  accepted it and has a passing test for it. Added the link back, staff-only, time-gated the same
  way the Appeared path already was.

Both are documented as `T050`/`T051` in `specs/057-day-sheet-status-overhaul/tasks.md`, with new
tests (`SessionOperationsPanel.test.tsx`, plus two new cases in `SessionSlotsView.test.tsx`). Full
regression check after both fixes: backend unit/contract green, frontend `tsc -b` clean, lint
clean, `npm run test -- --run` 374/375 (the one failure is the pre-existing, unrelated
`PatientHubPage.test.tsx` hardcoded-date flake, not touched this session).

Also sent a correction to the separately-running `task_126094d9` (day-sheet ordering fix): the bug
is broader than first scoped — it affects **any** slot with a booking via the day-sheet query's
join, not only slots a background job (the No-Show sweep) later touched. Worth checking that
session's fix covers a plain, never-swept fresh booking too.

### T048 merged in from the peer worktree (2026-09-22, same day)

`task_126094d9` finished: found the real root cause (`SlotRepository.findBySession_Id` had no
`ORDER BY`, so *any* row `UPDATE` — not just a background sweep — could shuffle its position in
the scan) and fixed it correctly, covering the broader case per the correction sent earlier
(added a test for a plain fresh booking, not just a swept one). It also opportunistically fixed
the related "N booked" summary undercount.

Merging required care: that worktree was checked out from `HEAD` (commit `5474512`), which
predates this session's in-progress, uncommitted package-per-feature reorganization (e.g.
`com.cms.scheduling.Session` → `com.cms.scheduling.domain.Session`). Its diffs used the old flat
package paths and would have broken compilation if copied wholesale — ported the semantic changes
by hand into the current package structure instead (`SlotRepository.java`'s query, the day-sheet
summary line, and the two new backend integration tests' imports). Full suite re-verified after
merging: backend unit/contract green, `tsc -b` clean, lint clean, frontend `npm run test -- --run`
376/377 (still just the one pre-existing, unrelated `PatientHubPage.test.tsx` date flake). Details
in `specs/057-day-sheet-status-overhaul/tasks.md`'s T048.

**Worth knowing for next time**: any future worktree-based background task spawned mid-session in
a repo with in-progress uncommitted restructuring will hit this same stale-baseline mismatch — the
worktree only sees committed state, not this session's own uncommitted work. Worth committing
structural reorgs before spawning parallel worktree sessions, or expect a manual port step like
this one.

### What's actually left, in order

1. `/speckit-converge` again now that T048-T051 are all genuinely done, to confirm 057 is fully
   converged with the merge included.
2. Same unresolved items as Part 8 items 4-6: the dismissed `/speckit-tasks` re-invocation
   ambiguity, the `PatientHubPage.test.tsx` hardcoded-date background task, and the 683+
   uncommitted files never pushed past the initial commit (now includes the package-per-feature
   reorg plus everything from today — worth committing in coherent chunks rather than one giant
   commit, given how large this has grown).
3. The `.claude/worktrees/modest-tharp-3fd7e7` worktree (`task_126094d9`'s) still exists with its
   own copy of the pre-merge diff — safe to discard once its work is confirmed merged (it is), but
   left in place rather than unilaterally removed.
4. Dev-data note: "Dr Test Verify" (Sunrise Family Clinic) now has a real `General Consultation`
   appointment type (₹500) and several test bookings/slots exercised through their full lifecycle
   (Completed, No-Show, cancelled) scattered across today's session — harmless leftover dev data,
   not cleanup-critical. Servers stopped cleanly at end of session.

## Part 10 — Doctor-console multi-tenancy audit, and a new feature (060-booking-abuse-prevention) taken through specify→clarify→plan→tasks→analyze→implement, fully live-verified (2026-09-23)

**Doctor-console multi-tenancy bug audit.** User reported a doctor's home console showing
"Completed today: 1" despite that doctor not having started any slots, and being able to see a
different doctor's patient. Both were real bugs: the established correct pattern (any-active-role
authorization ≠ doctor-self-scoping — see `ClinicSessionListController`'s own fix from an earlier
session) hadn't been applied to two newer endpoints. Fixed:

- `TodaySessionStatsController`/`SlotRepository.countStatusByClinicAndDate` — "Completed today"
  was clinic-wide, not doctor-scoped.
- `TodayPatientsController`/`BookingRepository.findActiveByClinicAndSessionDate` — the roster
  showed every doctor's patients, not just the caller's own.

User asked to check every other doctor console surface for the same bug class and fix whatever
was found. Two more genuine instances turned up and were fixed the same way (doctor
self-scoping via `DoctorProfileRepository.findByAccount_Id`, fail-closed on an unresolvable
profile):

- `PatientBookingHistoryController` — a patient's booking-history tab leaked cross-doctor data.
- `SessionDelayController`/`SessionDelayService.currentDelay()` — session delay figures leaked
  across doctors; now 404s via `SessionNotFoundException` for a foreign doctor's session, mirroring
  `SessionDaySheetController`'s existing convention.

A fifth candidate, `StaffWaitlistController.count`, was flagged as genuinely ambiguous (waitlist
entries can target either a doctor or a whole specialization, so there's no obviously-correct
doctor-scoping rule) and deliberately left unfixed, for the user to decide later. All four real
fixes have unit/contract test coverage, all passing.

**Feature 060 — Booking Protection / Appointment Abuse Prevention.** User's brief: reduce fake/
abusive bookings via three capabilities — a configurable active-booking limit (global + optional
per-clinic), rate limiting on booking *attempts* (not just successes, with a temporary cooldown),
and admin flagging of suspicious patterns (never auto-classified from one signal, always
reviewable and resolvable, full audit trail). Explicitly spec-first: told not to implement until
the spec itself was reviewed.

Ran the full spec-kit lifecycle skill-by-skill as the user invoked each: `/speckit-specify` (with
4 upfront architectural decisions resolved via `AskUserQuestion` — global+per-clinic limit scope,
self-service-patients-only for now, ClinicAdmin-not-SuperAdmin as the flag-review realm, live-
editable settings), `/speckit-clarify` (4 Q&As), `/speckit-plan`, `/speckit-tasks` (71 tasks),
`/speckit-analyze` (8 findings, 7 auto-remediated, 1 HIGH-severity finding the user explicitly
asked to revisit — resolved via another `AskUserQuestion` choosing **full audit-history tables**
over latest-state-only for both settings and per-clinic-limit changes, which meant 2 new
entities/repos/endpoints and retasking to 75 total), then `/speckit-implement`.

**Backend** (all new, `protection` module plus targeted `booking` extensions, one-way dependency
only — the two *synchronous* booking-time checks deliberately live inside `booking` itself, not
`protection`, specifically to avoid a module-dependency cycle): `BookingProtectionService`
(rate-limit-checked-before-booking-limit, per Clarifications), a separate
`BookingAttemptRecorder` bean with `@Transactional(REQUIRES_NEW)` so a rejected attempt's audit
row survives the very rollback its own exception triggers, `FlagDetectionService`'s five signal
detectors on an hourly sweep, `ProtectionSettingService` (16 runtime-editable settings, default-
if-no-row semantics), full change-history tracking for both settings and per-clinic overrides.
Found and fixed one real pre-existing schema gap along the way: `Booking` had no cancellation
timestamp at all, needed for the repeated-cancellations signal — added via migration `V38` with
no changes to any of the 4 existing `cancelIfActive()` call sites (set inline via JPQL
`CURRENT_TIMESTAMP`). All new `SecurityConfig` staff-realm paths (7 of them) got explicit
`.authenticated()` matchers ahead of the trailing `anyRequest().permitAll()` — this codebase's own
recurring bug class from earlier sessions, checked deliberately.

**Frontend** (built this session): patient-facing `BOOKING_LIMIT_REACHED`/`RATE_LIMITED` handling
on both booking flows (`BookSlotForm.tsx`, `QueueBookSlotForm.tsx`/`queueApi.ts` — the latter had
a real, separate dead-code bug fixed along the way: `defaultMessageFor(body) ?? body.message`
could never reach `body.message` since `defaultMessageFor`'s `default:` case always returns a
string), a shared `rateLimitMessage()` wait-time formatter; a new ClinicAdmin "Booking protection"
screen (`clinic-protection/ProtectionFlagsList.tsx` + `ClinicLimitOverrideForm.tsx`, wired into the
staff sidebar and dashboard with a new `ShieldIcon`); a new Super Admin
`admin-protection-settings/ProtectionSettingsPage.tsx` (inline edit, per-protection toggles,
expandable per-setting history), wired into the Super Admin console. All new frontend test files
pass; one real lint issue caught and fixed before it shipped (the new screens' `useEffect`
dependency arrays referenced `session?.token` inline instead of extracting a `token` const first —
inconsistent with this codebase's own established pattern, e.g. `InboxPage.tsx` — which produced
spurious `exhaustive-deps` warnings; fixed in all three new files).

**Live-verified, not just unit-tested** — all 5 user-story scenarios run against the actual
running dev stack:

- Booked 2 slots (limit=2), confirmed a 3rd was refused with the exact non-accusatory message.
- Drove the rate limiter into a real cooldown via the actual patient UI and watched the banner
  render the live `rateLimitMessage()` text; confirmed `retryAfterSeconds` counted down from a
  fixed trigger point across several retries during the cooldown (120→119→119→119→96) rather than
  resetting — proving the Clarifications' "cooldown end time is fixed once" rule holds for real.
- **Generated a real suspicious-activity flag** by cancelling a real future booking at a test
  clinic, restarting the backend (Spring's `@Scheduled` runs its first pass immediately on
  startup, so this substitutes for waiting out the hourly sweep), then reviewing it through the
  actual ClinicAdmin UI — reason text, detected timestamp, the exact cancellation in the evidence
  panel, resolve action, and disappearance from the Outstanding view — followed by a real
  cross-clinic tenant-isolation check (a *different* clinic's ClinicAdmin sees zero flags for the
  same patient).
- Logged in as a real Super Admin, changed live settings (including toggling `booking-limit.
  enabled` off) through the actual `ProtectionSettingsPage` UI, confirmed the very next booking
  attempt picked up the change with no restart, confirmed a non-Super-Admin token is rejected.
- Set and changed a per-clinic limit override through the real `ClinicLimitOverrideForm` UI,
  confirmed its change-history view renders correctly.

All dev-environment settings changed for these live tests (rate-limit thresholds, the
repeated-cancellations threshold, the global booking cap) were restored to their documented
defaults afterward. Two new throwaway test clinics/accounts now exist in the dev database:
**Sunrise Test Clinic** (`c520405a-48e6-49df-880b-97c6d960bb6f`, ClinicAdmin
`clinicadmin060@example.com` / `P@ssw0rd123!`) and a resolved test flag left on the pre-existing
**Design Test Clinic** (`designadmin@example.com` / `Str0ng!Pass`, see Part 6's original note) —
both harmless, safe to keep or delete.

Backend unit+contract suites green throughout (integration/concurrency tests for this feature are
written and compiled but Docker-gated, same standing sandbox limitation as every other feature
this session covered). Frontend: `tsc -b` clean, lint clean (no new warning categories introduced),
full suite 401 tests, 1 pre-existing failure — the same `PatientHubPage.test.tsx` hardcoded-date
flake flagged since Part 8/9, still not touched by this or any other feature this session.

All 75 tasks in `specs/060-booking-abuse-prevention/tasks.md` are now checked off. Nothing left
outstanding for this feature. The OneDrive/Gradle build-corruption workaround (`rm -rf
backend/build`) was needed twice this session, consistent with every prior session's experience —
still not a code bug, just how this machine's OneDrive sync interacts with Gradle's incremental
build state.

### What's actually left, in order

1. The 768-uncommitted-files situation (see the warning near the top of this note) — now the
   single largest risk to this project, and growing every session. Worth a dedicated session to
   commit history in coherent chunks rather than one giant commit.
2. `StaffWaitlistController.count`'s doctor-scoping ambiguity (flagged above) — needs a product
   decision, not a code fix, before anyone touches it.
3. Same unresolved items as Parts 8/9: the `PatientHubPage.test.tsx` hardcoded-date background
   task, and the `.claude/worktrees/modest-tharp-3fd7e7` worktree (safe to discard, left in place).
4. Feature 060's Docker-gated integration/concurrency tests (`BookingLimitConcurrencyTest`,
   `BookingRateLimitConcurrencyTest`, `ProtectionFlagTenantIsolationTest`) are written and compiled
   but have never actually executed — worth a real CI/Docker-enabled environment pass before
   trusting the concurrency guarantees they're meant to prove.

## Part 11 — Finished 059, modal-ized the patient booking forms, and took a new feature (061-doctor-live-status) through specify→clarify→plan→tasks→analyze→implement — paused mid-implement (2026-09-23)

**Finished feature `059-patient-clinical-record-access`.** Found it 38/47 tasks done and
untracked in `backlog/progress.md` at the start of this session (surfaced by `/speckit-orchestrate`
when asked which feature to run, since the backlog itself showed all 53 items "Converged"). Ran
`/speckit-analyze` then `/speckit-implement` to close out the remaining tasks; all 47 now `[x]`,
live-verified.

**Modal-ized the patient booking forms.** User flagged, from a screenshot, that the "Book this
slot" panel on the patient booking page had no real reason to be a full inline section and that
the "Your name" field served no purpose (the app already knows the patient's identity from their
session). Converted `BookSlotForm.tsx` to use the existing shared `Modal` component (native
`<dialog>`-based, already used by `EmployeeModal`/`DeleteConfirmModal`) with a close button, and
removed the name field entirely — the name is now silently derived via the existing
`deriveDisplayNameFromEmail()` helper at submit time. Applied the identical treatment to
`QueueBookSlotForm.tsx` on request. All affected tests updated (patient session seeded via
`storePatientSession`, name-typing removed, `onClose` assertions added); `tsc -b` clean, lint
clean, full frontend suite 402/403 (1 pre-existing unrelated date flake). Live-verified in browser
both times, then the throwaway Queue-mode test schedule/booking created for that verification (at
**Star Clinic**, not Design Test Clinic) was deleted on request — one already-touched session
survived un-deletable through any existing product feature (a real, documented product gap: patient
cancel refuses `NOT_A_FIXED_TIME_SESSION`, session delete refuses "booking/waitlist history
attached"), left in place as harmless.

**Feature 061 — Doctor Live Status (schedule deviation, train-tracking-style).** User's brief:
extend (never rebuild) the existing Session Delay Tracking feature
(`SessionDelayService`/`SessionDelayController`, backlog 023) with a continuously-live
ON_TIME/RUNNING_EARLY/DELAYED/NOT_STARTED/COMPLETED status, computed from actual schedule
progression (not `now - first_slot_time`), against a new 04:30 AM "operational day" boundary,
reusing the existing `QueuePositionIndicator` polling pattern (not a new SSE/WebSocket
mechanism) for both a staff/doctor view and a brand-new patient-facing view. Explicitly spec-first
— told not to write any implementation code until the spec was reviewed and approved.

Ran the full spec-kit lifecycle skill-by-skill as the user invoked each:

- `/speckit-specify` — wrote `specs/061-doctor-live-status/spec.md` with an explicit
  "Relationship to the Existing Feature" section documenting the deliberate reversal of backlog
  023's "NOT a live timer" decision, and 6 flagged assumptions requiring approval (no
  `appearedAt`/`completedAt` timestamp exists so the algorithm had to be redesigned around Slot
  *status* instead of a new timestamp column; the 04:30 boundary scoped to this feature only, not
  retroactive to session-generation/no-show-sweep/day-sheet logic; no clinic-timezone concept, so
  server-local time continues to stand in for clinic-local time; multiple-Sessions-per-doctor-per-day
  handled as fully independent, no cross-session merge).
- `/speckit-clarify` — 3 Q&As, all recorded in the spec's own Clarifications section (04:30
  boundary is scoped to this feature only; live status is independent per session, never merged
  across a doctor's same-day sessions; polling reuses `QueuePositionIndicator`'s existing ~20s
  interval).
- `/speckit-plan` → `research.md` (6 decisions), `data-model.md` (confirms **zero schema
  change** — the whole algorithm derives from existing Slot status + scheduled time, a genuine
  design insight that corrected an earlier hypothesis formed before the calculation was fully
  designed through), `contracts/doctor-live-status.md` (both new endpoints' full JSON shapes),
  `quickstart.md` (9 live-verification scenarios).
- `/speckit-tasks` — 37 tasks across Foundational + 3 user stories + Polish.
- `/speckit-analyze` — 5 findings (E1–E5), all auto-remediated into `tasks.md` on request (missing
  frontend test task added; explicit Queue-mode/cancellation/already-resolved-slot edge cases added
  to existing test tasks; the privacy live-verify task extended to check all five statuses
  including Session complete).
- `/speckit-implement` — executed phase-by-phase, test-first. **Paused here at the user's
  explicit request**, after Foundational + User Story 1 + User Story 2 (T001–T027, 27 of 37
  tasks). User Story 3 (04:30 boundary edge-case coverage) and Polish (full-suite regression runs,
  security-config grep, final live-verify pass) — T028–T037 — **not started**.

**What's built and live-verified as of the pause:**

- Backend: `OperationalDayService` (new, the single 04:30-boundary function), `SessionLiveStatusService`
  (new, `Clock`-injected for deterministic tests, the pure calculation plus a patient-facing
  estimated-wait helper), a new `GET /api/v1/clinics/{clinicId}/sessions/{sessionId}/live-status`
  (staff/doctor, reuses `SessionDelayService`'s existing authorization, extracted not duplicated)
  and `GET /api/v1/patients/bookings/{bookingId}/live-status` (patient, booking-ownership-scoped,
  patient-safe shape only). `SessionDelayService`/`SessionDelayController`'s existing `/delay`
  endpoint is completely untouched. Found and fixed one real pre-existing gap along the way:
  `ScheduleExceptionHandler` had no handler for `SessionNotFoundException` at all — a real bug
  that also silently affected the old `/delay` endpoint (which has no contract-tier test, so it
  never caught it).
- Frontend: `LiveScheduleStatusIndicator.tsx` (new, replaces `DelayIndicator` inside
  `SessionOperationsPanel.tsx` — `DelayIndicator.tsx` itself left in place, unused there, since
  backlog 023's underlying `Session.delayMinutes` mechanism may still have other consumers),
  wired into both the staff per-slot operations panel and the patient booking detail page
  (`PatientPages.tsx`, alongside the existing `QueuePositionIndicator`/`CancelBookingButton`/
  `VisitRecordSection`).
- Backend unit (`SessionLiveStatusServiceTest` — 11 cases via `Clock.fixed(...)`,
  `OperationalDayServiceTest` — 5 cases) and contract tests (staff + patient controllers, 9 cases)
  all green. Integration tests (`SessionLiveStatusFullLifecycleTest`,
  `SessionLiveStatusAuthorizationTest`, `PatientSessionLiveStatusAccessTest`) written and compile,
  Docker-gated per this sandbox's standing limitation. Frontend: `LiveScheduleStatusIndicator.test.tsx`
  now 14 cases (staff + patient mode), `tsc -b` clean.
- **Live-verified end-to-end in the real dev stack**, not just unit-tested: created a real
  5-minute-slot Fixed-Time schedule on **Design Test Clinic**'s Dr. Test Doctor starting a few
  minutes in the past, booked walk-ins plus one real patient booking, then drove the actual
  Appeared/Completed actions through the real staff UI and watched the status walk
  **Delayed (5 min late) → On time → Running early (5 min early)** — matching quickstart Scenario 2/3
  exactly. Confirmed from the **patient's own browser session** too: the booking detail page showed
  the same transitions, and the raw network response body for `/live-status` was inspected
  directly — `{bookingId, applicable, doctorName, currentPatientOrdinal, statusText,
  estimatedWaitMinutes}` only, no raw status code, no other patient's data (FR-011/SC-003,
  quickstart Scenario 8). Along the way, found and fixed a **dev-environment-only** issue (not a
  product bug): the backend process that was already running predated the User Story 2 backend
  files on disk, so `/api/v1/patients/bookings/*/live-status` 404'd until the dev server was
  restarted — a reminder to always restart the backend after resuming a `/speckit-implement` run
  that touched backend code, not just trust an already-running `preview_start` reuse.

**Test data left behind (harmless, on the same throwaway Design Test Clinic from Part 6):** a
`Wed 21:35–23:00, Fixed-Time, 5-min slots` schedule on Dr. Test Doctor, today's session under it
with 4 real bookings (3 walk-ins, 1 patient — `livestatus.patient.<timestamp>@example.com` /
`Str0ng!Pass`), 2 slots marked Completed. Safe to delete via the normal staff UI (Doctors → Dr.
Test Doctor → schedule → Delete) whenever convenient; nothing else depends on it.

### Exact resume point

`specs/061-doctor-live-status/tasks.md` — T001 through T027 are `[x]`. Next up, in order:

1. **T028** [P] [US3] Extend `OperationalDayServiceTest` with a month/year-rollover case (e.g.
   `2026-01-01T04:29` → still `2025-12-31`).
2. **T029** [P] [US3] Grep-verify no second, duplicate 04:30-boundary calculation exists anywhere
   outside `OperationalDayService`.
3. **T030** [US3] Live-verify quickstart Scenario 7 (the explicit 04:29/04:30/04:31 boundary
   cases) — noted in `tasks.md` as automated-test-only, since real wall-clock time can't be forced
   to exactly straddle the boundary live.
4. **T031–T037** (Polish): backend `spotlessApply`+full `scheduling`/`booking` module test run,
   frontend `tsc -b`, frontend lint, frontend full suite, grep both `SecurityConfig`s for the two
   new paths, confirm no accidental edits beyond T001's `SessionOperationsPanel` swap, and a final
   live-verify of quickstart Scenario 9 (cross-doctor/cross-patient refusal — already proven by
   `SessionLiveStatusAuthorizationTest`/`PatientSessionLiveStatusAccessTest`'s own test cases, but
   not yet re-driven through the live UI).

Then `/speckit-implement`'s own Completion Report, and — only if the user asks for it next —
`/speckit-converge`.

## Part 12 — 061 finished; Super Admin delete fix; 062, 063 and 064 built; 008/037 cascade bugs fixed (2026-09-24)

Every item below is **Converged** in `backlog/progress.md`. Each was live-verified on throwaway clinics, which were purged afterwards.

- **061-doctor-live-status** is finished and converged. The "resume point" in Part 11 is done. A stale session now hides its status.
- **Super Admin bulk delete of rejected clinics** failed with "cannot delete rows due to unexpected reason". Cause: an FK on `clinic_booking_limit_override` rolled back the whole batch. Fixed in `ClinicVerificationService.deleteGuarded`.
  - A one-off DB purge then force-removed the rejected clinics at the user's request.
  - Backup taken first: `cms-before-rejected-purge-2026-09-24.dump`, in the session scratchpad.
- **062 (rejected clinics can't operate).** Rejection auto-cancels the clinic's future bookings, and patients see a message in their console. Only the clinic's admin can still sign in. Relevant code: `ClinicRejectionCascadeService` and `RejectedClinicAccessGate`/`Interceptor`.
- **008 cascade bug (real) and 037 anonymization gap, both fixed.** There were two causes:
  - The AFTER_COMMIT listeners lost their writes; they now use `REQUIRES_NEW`.
  - The queries missed queue bookings.
- **063-front-desk-walk-in.** A new clinic-level Walk-in screen with a required visit reason, today's sessions with live status and Doctor free/busy, and a waiting-line panel. Migration: V39. The old 025/058 walk-in slot insertion was retired.
- **064-queue-send-in-complete** fixes the queue-position bug (option B).
  - Queue tokens are now minted `BOOKED`.
  - Staff Appeared/Complete/cancel work on queue sessions. Patient self-cancel of a queue booking is still refused.
  - Position counts only the waiting tokens ahead.
  - The front-desk panel supports queue sessions.
  - Migration V40 moved existing active queue tokens from OPEN to BOOKED; Star Clinic's 2 rows were confirmed.
  - Side effect: whole-session and cutoff cancellation now actually reach queue bookings.

### State at end of session

- **Nothing is committed.** All work from Parts 11–12 is in the working tree on `main`.
- Backend unit and contract tests are green. Integration tests compile but are Docker-gated here, so they haven't run.
- Frontend: 436 tests pass; `tsc` and lint are clean.
- Dev servers were left running: backend on :8080 and frontend on :5173.

### Suggested next

1. Review the diff and commit, probably one commit per feature: 061, delete-fix, 062, 008/037, 063, 064.
2. Run the integration suite somewhere Docker is available, e.g. CI, to exercise the Testcontainers tests added in 062–064.
3. Backlog 053 (visual/copy quality pass) is still Not Started. It begins with a discussion, not implementation.

## Part 13 — 065 verified with Docker, CI repaired, test-harness repair started, waitlist-offer bug fixed (2026-09-29, cloud sandbox)

This session ran in a Claude Code cloud container, not the Windows machine. All work is pushed. The only exception is an unused local branch, `claude/protection-settings-draft-race`, which holds just the two CI commits and no fix.

### 065-phase1-stabilization: verified and closed (PR rish1789/CMS2#11)

- All 38 tasks in `specs/065-phase1-stabilization/tasks.md` are checked. `backlog/progress.md` marks 065 **Implemented**; no `/speckit-converge` pass has been run.
- **Runtime (port 8081, fresh PostgreSQL 16):**
  - Flyway applied V1–V41, Hibernate `validate` passed, and there were no WARN or ERROR lines.
  - All quickstart §3 smoke checks returned the expected codes.
  - Booking inside a cancelled range, booking an elapsed slot, a repeat whole-cancel and deleting a cancelled session each return the expected 409.
- **Browser (Playwright, staff login):** the day sheet shows cancelled ranges and whole-session cancellation correctly.
- **Frontend:** `tsc` clean, lint at the 24-warning baseline, Vitest 441/441.
- **T025** `SessionAvailabilityIntegrationTest`: 9/9 with Docker. Audit docs 07, 08 and 10 were updated. 07 now has a "Full backend run with Docker" section with the full failure breakdown.
- **CI was completely broken on `main`**, and PR #11 now also fixes it:
  - `ci.yml` used `secrets` in step-level `if:`, so every run failed at parse time with 0 jobs. The steps now test a job-level boolean `HAS_NVD_API_KEY`, checked with `actionlint`.
  - `backend/gradlew` was mode 100644 (`Permission denied`) and is now executable.
  - The frontend CI job passed for the first time.

### Backend test-harness repair (PR rish1789/CMS2#12, branch `claude/test-harness-fixes`)

These are test-only commits, each verified by re-running the affected classes before pushing:

1. `66d4166`: **root cause of the mass CI failure.** The 23 integration base classes each stop their `@Container` Postgres after the class, but Spring's context cache reused the context bound to the stopped container. `@DirtiesContext(AFTER_CLASS)` fixes it. A plain `./gradlew test` run is now 848/1,046 passing with 0 `Connection refused`, in 24 min instead of 83 min with the fork-per-class workaround.
2. `d6c070f`: fixture staff codes fit `VARCHAR(20)` (31 classes).
3. `91ad452`: the inbox walk-in fixture produced 11-digit mobiles.
4. `9f77665`: `@Import` the nested `RecordingNotificationSender` config, which Spring does not detect on a superclass (16 tests).
5. `5bb8410`: `Instant` comparisons at microsecond precision.
6. `f0441de`: no `entityManager.refresh()` or `@Modifying` calls outside a transaction; uses a `findById` re-read and `TransactionTemplate`.

**First real backend CI run** on `f0441de`: 1,046 tests, **101 failed** (down from 198), 0 connection-refused, 17 min. Still to do on this branch:

- teardown foreign-key order, and the duplicate-email knock-ons it causes (the largest group)
- stale assertions:
  - hard-coded `2026-09-03` dates in `BookingDetailControllerTest` and `PartialSessionCancellationSuccessTest.queueMode…`; derive the date from the session instead
  - the 15-day session window
  - entity-identity `contains`
  - 057's 403→409 and the waitlist 404→409 status changes
  - the retention-purge fixture blocked by the 2026-09-24 anonymization rule
  - the no-show fixture with a nonexistent booker
- tests that now reach their assertions:
  - `SessionDaySheetControllerTest` (no fee configured)
  - `ClinicStaffControllerTest.excludesADeactivatedRoleAssignment` (2 vs 1)
  - `OnboardDoctorReuseTest`

### Real product bugs found (pre-existing, none caused by 065)

- **Waitlist offer never saved after a cancellation.** Fixed in **PR rish1789/CMS2#13** (`claude/waitlist-offer-persistence`, stacked on #12): `REQUIRES_NEW` on `WaitlistBumpListener`. Over 392 tests, failures went from 49 to 44, with 5 fixed and 0 new.
- **Still open**, for option C; each needs a spec first:
  - PB-003: queue-token issuance under concurrency
  - unhandled duplicate-key races (clinic registration, patient signup, Super Admin license edit, staff onboarding email/license) return a 500 instead of a 409
  - a patient-linking race that retries inside an aborted transaction
  - `ClinicDeVerificationCascadeTest…WaitlistOffer`, which has a second cause beyond #13
  - the booking rate limiter exceeding its margin (12 vs 9)

### Open items and decisions

- **PR merge order:** #11 first, then #12, then retarget #13 to `main`. Nothing has been merged; the owner merges.
- **`ProtectionSettingsPage` "85" CI failure** on PR #12's frontend job:
  - The mirror-prop-in-effect (TD-16) hypothesis could not be reproduced locally (24 isolated runs plus 3 parallel full suites).
  - The owner chose to wait for the CI re-run before opening a fix PR.
  - Worktree `CMS2-protection` / branch `claude/protection-settings-draft-race` is local only, not pushed.
- **Housekeeping:**
  - a stray gitlink `docs/.claude - Copy/worktrees/modest-tharp-3fd7e7` (post-job warning only)
  - upgrading `actions/*@v4` to v5 (Node 20 deprecation)

### Sandbox gotchas (cloud container)

- **Docker:**
  - Start it with `DOCKER_MIN_API_VERSION=1.24 dockerd &`. Docker 29's minimum API is too new for Testcontainers 1.21's docker-java.
  - The daemon died once, most likely because broad `pkill -f` patterns killed it. Kill by PID, and check `docker info` before test runs.
- **Maven Central** can return 429; retry.
- **Git worktrees used:**
  - `/home/user/CMS2`: #11
  - `/home/user/CMS2-harness`: #12
  - `/home/user/CMS2-waitlist`: #13
  - `/home/user/CMS2-protection`: local branch with the CI commits only, on hold

## Part 14 — Everything merged; duplicate-key, patient-linking and waitlist races fixed; flaky test hardened (2026-09-29, cloud sandbox, continued)

This continues Part 13 in the same cloud container. **Part 13's "Open items" and "Still open" lists are superseded by this part.**

### State of `main`

`main` is at **`8e7a224`** (plus this docs PR, #19, once merged). Every PR from this session was merged with **merge commits** (no squash, so stacked branches stayed valid), in this order:

| PR | What it did |
|---|---|
| #11 | 065 verification and audit docs, plus the CI repair (`ci.yml` secrets-in-`if`, `gradlew` mode) |
| #12 | Test-harness repair: `@DirtiesContext`, teardown order, stale assertions, and the ported waitlist-claim self-deadlock fix `6ed89c0`. The later commits (`888240a` retention-purge fixture, `f682771` inbox teardown, roster `active=true`, reuse counts) are also in. |
| #13 | `WaitlistBumpListener` `REQUIRES_NEW`: waitlist offers are now actually saved |
| #16 | Duplicate email/license submissions return 409, not 500. Clinic registration, patient signup, staff onboarding (email and license) and the Super Admin license edit each had the right catch, but `save()` deferred the INSERT past it; now `saveAndFlush()` inside the `try`. The license edit failed even without concurrency. |
| #15 | `ProtectionSettingsPage` test sets the number input in one `fireEvent.change` (CI had shown `"85"`, a lost clear). This is a hardening: it was never reproduced locally, so only a quiet CI history proves it. |
| #17 | **Feature 066-patient-linking-race**, the full spec-kit set with 17/17 tasks. See below. |
| #18 | The claim/decline race is fixed; the de-verification offer test was re-dated. See below. |

PR #14 (the standalone deadlock fix) was closed after being ported into #12. The Dependabot PRs #1–#10 are untouched and still open.

### 066-patient-linking-race (spec → plan → tasks → implement, test-first)

- **Bug:** concurrent first bookings by one patient at a new clinic. The loser's INSERT hit `uq_patient_clinic_account`, PostgreSQL aborted the transaction, and the catch's re-read re-flushed the failed INSERT, so the request returned 500.
- **Fix:** `PatientLinkingService.findOrCreatePatient` takes the existing 060 row lock (`PatientAccountRepository.findWithLockById`), so same-account calls serialize. The broken re-read was removed, and the unique index stays as the backstop. There is no schema change.
- **Research finding:** 060's booking-limit gate already serialized fixed-time bookings, but only with the limit enabled. The queue path is not `@Transactional` (022), so it was always exposed.
- **Spec change:** FR-003 was narrowed during planning. The queue path already commits the Patient record separately, and that is unchanged.
- **Tests:**

  | Test | Before | After |
  |---|---|---|
  | `PatientLinkingSameAccountRaceTest` | RED | GREEN |
  | new fixed-time race test (booking limit off) | RED | GREEN |
  | new queue race test | RED in 3 of 5 runs | GREEN in 10 of 10 |
  | winner-rollback test | green (characterization) | green |
  | failed-booking-leaves-no-patient test | green (characterization) | green |

  Observed results are recorded in `specs/066-patient-linking-race/tasks.md`.
- **Not done:** no `/speckit-converge` pass has been run for 066, or for 065.

### PR #18 findings

- **Claim/decline race (a real bug, 032 FR-010/SC-004):** `decline()` reported success after losing `expireIfOffered` to a concurrent claim. Now `WaitlistReleaseService.release()` returns a boolean, and `decline()` throws `WaitlistOfferNotClaimableException` on a loss. `WaitlistClaimConcurrencyTest` went from 5 of 8 failing to 0 of 10, including on combined `main` with 066's lock.
- **`ClinicDeVerificationCascadeTest…WaitlistOffer` was not a product bug.** Its fixture generated *today's* 09:00–13:00 session, so after 09:00 the slot had already started, and 065's availability rule correctly refused the offer (traced: verdict `ELAPSED`). The test now uses tomorrow's session, via a new `saveFixedTimeSessionWithSlotsOn` overload in `AbstractSessionCancellationIntegrationTest`.
  - **Watch for this pattern elsewhere:** several fixtures still date sessions *today* at 09:00–13:00, so any assertion that needs a still-bookable slot is time-of-day dependent.

### Test status

- **Last full backend run:** on the 066 branch before #18. 1,050 tests, 4 failures, 2 of which #18 has since fixed.
- **Expected on current `main`, not yet run as a full suite:**
  - `BookingRateLimitConcurrencyTest`: a real bug; the margin is 12 against 9.
  - `PatientWaitlistJoinTest.rejectsADoctorNotStaffedAtTheClinic`: **owner decision pending**, 404 or 409. The code maps `DOCTOR_NOT_STAFFED_AT_CLINIC` to 409, and the test expects 404. The owner said "decide later".
- **Intermittent:**
  - `PartialSessionCancellationRangeTest.toTimeLeavesSlotsAtOrAfterTheUpperBoundUntouched`: seen once in a full run; it passes alone (3 of 3). It may be order- or time-dependent, like the de-verification fixture; not investigated.
  - PB-003 `QueueSlotIssuanceConcurrencyTest`: not seen in the last three full runs.
- **Frontend:** Vitest 441/441 on `main`.
- **CI on `main` after the merges was not observed.** Watching stopped at the owner's request.
- **CI has no `timeout-minutes`,** so a hung backend job holds the runner for 6 h. Recommended: 45.

### Still open (next candidates)

- the booking rate-limit margin;
- PB-003;
- the 404/409 decision;
- the `PartialSessionCancellationRangeTest` flake;
- SEC-03 and PB-005 (both need owner decisions);
- a converge pass for 065 and 066;
- CI `timeout-minutes`;
- the stray `docs/.claude - Copy/worktrees/...` gitlink;
- `actions/*@v4` → v5.

### Sandbox gotchas (in addition to Part 13)

- **spec-kit scripts:** `.specify/scripts` are **PowerShell-only**, and `pwsh` is not installed in the cloud container. Their steps (copy the template, write `.specify/feature.json`) were done by hand. `update-agent-context` was not run.
- **Transient permission-classifier errors:** the auto-mode permission classifier sometimes returned "no verdict" and blocked `Bash`. Retrying later worked; editing via the file tools did not need it.
- **Worktrees in this container:**
  - `/home/user/CMS2-066`, `CMS2-wl`, `CMS2-dup`, `CMS2-psp` and `CMS2-harness`: all merged.
  - `CMS2-main`: detached, scratch.
  - `CMS2-doc`: PR #19.

A separate, self-contained note for OpenAI Codex is in `CODEX_HANDOFF.md`.

## Part 15 — Remaining bugs fixed, all dependency PRs resolved, Spring Boot 4 scoped (2026-09-30)

On 2026-09-30, work happened in other sessions and in this one. **Part 14's "Test status" and "Still open" lists are superseded by this part.** `main` is at **`8df853b`**, and there are **no open PRs**.

### Merged on 2026-09-30 (merge commits)

| PR | What it did |
|---|---|
| #20 | Booking rate limit is exact under concurrent attempts (060). The waitlist not-staffed case now expects **409**, which resolves the 404/409 decision. undici bumped for high-severity advisories (#21 was closed as part of this). |
| #23 | **Feature 067-queue-token-issuance-race:** PB-003 fixed. Queue-token issuance serializes on the session row, and queue booking is atomic, so there are no orphan tokens. The full spec-kit set is in `specs/067-queue-token-issuance-race/`. |
| #22 | Scheduled sweeps no longer run inside integration tests. |
| #19 | Docs (audit 07, Part 14, `CODEX_HANDOFF.md`), plus the ProtectionSettingsPage test now waits for initial effects before editing. CI had shown the value stuck at 8 again after #15, so #15 alone was not enough. |
| #24 | CI `timeout-minutes` (backend 45, frontend 15), Node 24 action majors, and removal of the stray `docs/.claude - Copy` folder and gitlink. |
| #25 | Testcontainers 1.21.3 → 2.0.5 (supersedes Dependabot #6). The full suite passed on Windows with Docker 29.8.1 *without* the `api.version` workaround. |
| #26 | The frontend CI job runs on Node 24, a prerequisite for Vitest 5. |
| Dependabot | #2 (dependency-management 1.1.7), #4 (JJWT 0.13.0, all three modules, so #3 was closed), #5 (vite 8.3.1), #7 (react-router-dom 7), #8 (Vitest 5), #9 (TypeScript 7), #10 (postcss). |

### TypeScript 7 (#9): verification done in this session before the owner merged it

- #9's own CI ran on a base without react-router 7 (#7). So it was re-verified merged onto `main` `d025e65`:
  - `tsc` 7.0.2: 0 errors, and a planted type error was correctly reported (TS2322);
  - lint clean, Vitest 441/441, `npm run build` OK, `npm audit --audit-level=high` clean.
- TS 7 is the native compiler, shipped as per-platform binaries that include `win32-x64`. Nothing in the project uses the removed TypeScript JS API: only `tsc -b` runs it, oxlint does the linting, and Vite/Vitest don't depend on it.

### Spring Boot 4 (#1): closed, needs a planned migration

- CI failed in 25 s: `Spring Boot plugin requires Gradle 8.x (8.14 or later) or 9.x. The current version is Gradle 8.10`. Dependabot cannot bump the Gradle wrapper, so the PR could never pass on its own. It was closed with a scoping comment ([#1 comment](https://github.com/rish1789/CMS2/pull/1#issuecomment-5916579809)).
- **Scope measured in a scratch worktree** (wrapper bumped to 8.14.3; nothing pushed):
  - **Production code:** 1 file, 4 errors. `common/ApiErrorController.java` imports `org.springframework.boot.web.servlet.error`, which moved in the module split. The first compile may have stopped early.
  - **Tests (~45 files):** `@MockBean`/`@SpyBean` were removed (26 files); the `@WebMvcTest`/`@AutoConfigureMockMvc` imports moved (45 files). The test sources were not compiled yet.
  - **Flyway** auto-configuration moved to its own starter. With only `flyway-core`, migrations could silently not run, so check at startup that Flyway reports V41.
  - springdoc 2.6.0 needs its Boot 4 line. `RateLimitingFilter` uses a Jackson 2 `ObjectMapper`, but Boot 4 defaults to Jackson 3.
  - **Security** (6 filter chains, 3 JWT realms): needs the full suite plus a runtime login smoke test for all 3 roles.
- **Estimate:** 2–3 hours, mostly the test-annotation changes.

### Test and CI status

- **Last counted full backend run:** `363cd73`, [CI run 36694968509](https://github.com/rish1789/CMS2/actions/runs/36694968509). **1,071 passed, 0 failed.** Frontend 441/441.
- **Last completed CI on `main`:** `d025e65` (after react-router 7): success.
- **CI on `8df853b`** (TypeScript 7, [run 36753530061](https://github.com/rish1789/CMS2/actions/runs/36753530061)) was **in progress** when this was written; check its result.
- The backend OWASP scan is still skipped, because the `NVD_API_KEY` secret is not configured. That is the owner's decision.

### Still open

- **Spring Boot 4 migration:** the next big item.
- **Convergence passes** for 065, 066 and 067 have not been run.
- **067 known gap:** cancellation does not take the session lock that token issuance now uses. Closing it would be new behaviour, so it needs a spec (068).
- **`PartialSessionCancellationRangeTest`:** historical intermittency; not proven fixed.
- **Owner decisions:** SEC-03 (per-clinic pricing), PB-005 (time zones), and the `NVD_API_KEY` secret.

### Sandbox gotchas (new)

- **Maven Central 429s:** it rate-limits the cloud sandbox. Wait a few minutes and retry. `--refresh-dependencies` makes it worse.
- **Scratch worktrees in this container** (not pushed; safe to delete):
  - `/home/user/CMS2-ts7` (`scratch/ts7-check`, a local merge of #9 onto `main`);
  - `/home/user/CMS2-sb4` (`scratch/sb4-check`, #1 plus a Gradle 8.14.3 wrapper, for scoping only).

## Part 16 — Spring Boot 3.3 → 4.1 migration (2026-10-01, cloud sandbox)

This was done on branch `claude/spring-boot-4` while the owner was away. **It is not merged; the owner reviews and merges.** It replaces the closed Dependabot PR #1.

### Changes

- **Gradle wrapper 8.10 → 8.14.3**, regenerated with `./gradlew wrapper`. `gradlew` stays executable, and `gradlew.bat` keeps the repo's LF line endings.
- **`build.gradle`:**
  - Boot 4.1.1;
  - `spring-boot-starter-web` → `spring-boot-starter-webmvc`;
  - `flyway-core` → **`spring-boot-starter-flyway`**. Boot 4 moved Flyway's auto-configuration into this starter; without it, migrations silently stop running at startup.
  - springdoc 2.6.0 → **3.1.1** (built against Boot 4.1.0);
  - new test starters `spring-boot-starter-webmvc-test` and `spring-boot-starter-security-test`.
- **Production code:** one import. `ApiErrorController` now implements `org.springframework.boot.webmvc.error.ErrorController`. The `RateLimitingFilter`'s private Jackson 2 `ObjectMapper` is unchanged; it still works.
- **Tests:** 47 files, all mechanical. `@MockBean` → `@MockitoBean` (Spring Framework 7's `org.springframework.test.context.bean.override.mockito`); every use was a test-class field, which is what `@MockitoBean` supports. The `@WebMvcTest`/`@AutoConfigureMockMvc` imports moved to `org.springframework.boot.webmvc.test.autoconfigure`.
- **`application.yml`: `spring.jackson.use-jackson2-defaults: true`.**
  - Boot 4 uses Jackson 3, and its `FAIL_ON_NULL_FOR_PRIMITIVES` default made a request body that omitted a primitive field fail with 400. For example, the front-desk walk-in request omits `confirmDuplicate`. This broke 3 integration tests (`QueueSendInCompleteTest`, `WalkInLineLifecycleTest`, `RejectedClinicBookingRefusalTest.walkInIsRefused`).
  - The setting restores Boot 3's exact JSON behaviour for every client. Adopting Jackson 3 defaults would be a separate decision.
- **`.claude/launch.json`:** both backend configurations now run `./backend/gradlew.bat -p backend bootRun` instead of the standalone `gradle-8.10`.
  - **Not testable from the cloud sandbox.** On first use the wrapper downloads Gradle 8.14.3.
  - If that fails on the Windows machine, extract Gradle 8.14.3 the same way 8.10 was extracted, and point the configuration at it.
- **README:** the tech stack now says Spring Boot 4.1.

### Verification (cloud sandbox, Docker 29.3.1, Testcontainers 2.0.5)

- **Build:** compile has 0 errors and 0 warnings (main and test), and `spotlessCheck` is clean.
- **Full backend suite: 1,071 tests, 1,070 passed, 0 failed, 1 skipped (18m 20s).** The skip is `SessionAvailabilityIntegrationTest.todaysElapsedSlotIsUnlisted…`, which by design runs only between 02:30 and 21:00 in the JVM's clock; the run was at about 01:30 UTC. Main's baseline is 1,071/0.
- **Runtime:** the boot jar was started against a fresh Postgres 16.
  - **Database:** Flyway migrated an empty schema V1 → **V41** (41 rows, all successful), Hibernate `validate` passed, health was `UP`, and there were 0 ERROR log lines. The only WARNs were springdoc's "docs endpoint enabled" notices (known SEC-09).
  - **Logins:** a patient (signup → login → `GET /patients/bookings`), a ClinicAdmin (register → `/staff/login` → `GET /clinics/{id}/staff`) and the Super Admin (`/staff/login` with the env credentials → `GET /admin/clinics`) all returned **200**.
  - **Access control:**
    - every cross-realm token and every missing token returned **401**, including an unmapped `/clinics/{id}/x`;
    - `/discovery/cities` returned 200;
    - CORS preflights returned 403 for a foreign origin and 200 for `localhost:5173`;
    - `/actuator/env` returned 403, and validation errors keep the coded `{error, message}` body.

### Found along the way (pre-existing, not Spring Boot)

- **Two midnight-wrap test bugs, both fixed in this PR at the owner's instruction** ("fix the issues, make it green, then merge"). Each was reproduced red, then shown green at the failing clock time:
  - **`SlotCompletionServiceTest`** failed between 00:00 and 01:00 in the JVM's clock. The slot start was `LocalTime.now().minusHours(1)` on today's date, which wraps to 23:xx, so 3 tests hit `SlotNotYetStartedException`. It now takes date and time from one `LocalDateTime.now().minusHours(1)`. Verified at a 00:02 JVM clock (8/8; it was 3 failed before), and at 02:03 and 07:33.
  - **`FrontDeskWalkInPage.test.tsx`** failed between 00:00 and 03:00. The "ended" session's `endTime: hoursFromNow(-3)` wrapped to tonight, and it failed PR #28's first frontend CI run at 01:44 UTC. The file now pins only `Date` to today's midday before the fixtures are built (timers stay real). Verified 10/10 under clocks of 02:01, 07:31, 23:01 and 12:01; full frontend suite 441/441.

### Still open after this

- Merge this PR and confirm CI on `main`.
- Confirm the Windows launch configuration starts the backend.
- Convergence passes for 065, 066 and 067.
- 068 (cancellation vs. 067's session lock).
- Owner decisions: SEC-03, PB-005, and the `NVD_API_KEY` secret.

## Part 17 — IST time-zone pin merged; 068 per-clinic fees (2026-10-01, cloud sandbox)

### PB-005: IST pin (rish1789/CMS2#29, merged by the owner)

- The owner's decision was "Time zone - IST". `CmsApplication.main` calls `TimeZone.setDefault(Asia/Kolkata)` before Spring starts. The test JVM runs with `-Duser.timezone=Asia/Kolkata`. `ServerTimeZoneTest` was added.
- Full backend suite: 1,074 passed, 0 failed. CI green.

### SEC-03: 068-per-clinic-fees (owner decision B; branch `claude/068-per-clinic-fees`)

- **Owner answers:** only the clinic's admin edits prices; migration copies today's prices to every clinic where the doctor is actively staffed.
- **Schema:**
  - V42 creates `clinic_doctor_fee` and `clinic_appointment_type_price`.
  - V43 is the insert-only copy. `updated_by_account_id IS NULL` marks a copied row.
  - `doctor_default_fee` and `appointment_type.fee_override` are kept but no longer read.
- **Resolution:** a booking's clinic type price, else its clinic default, else 409 `NO_FEE_CONFIGURED`. It never uses another clinic's price.
- **API:**
  - `GET/PUT/DELETE /api/v1/clinics/{c}/doctors/{d}/fees[/default|/appointment-types/{t}]`. Writes need an active ClinicAdmin of `c`, and the doctor must be staffed at `c`. Reads are open to any staff member of `c`.
  - The retired `PUT /api/v1/doctors/{d}/default-fee` returns 410, and creating or renaming a type with `feeOverride` returns 400 `FEE_MOVED_TO_CLINIC`.
  - New patient listing: `GET /api/v1/patients/clinics/{c}/doctors/{d}/appointment-types`.
  - `AppointmentTypeResponse.feeOverride` is now `fee`, the effective price in clinic context; it is null without one.
- **Frontend:** the appointment-types page edits the current clinic's prices and is read-only for non-admins. The patient booking and waitlist claim forms show the clinic's fee.
- **Test fixtures:** `ClinicPriceFixtures` (a test bean) seeds clinic prices the way V43 does. Every integration cleanup deletes price rows first.
- **Verification:**
  - full backend suite 1,108/0/0, `spotlessCheck` green;
  - frontend `tsc`, lint and Vitest 445/445;
  - runtime V41 → V43 upgrade on a fresh Postgres, plus every quickstart step (see `specs/068-per-clinic-fees/tasks.md`, "Observed results").
- **Honest notes:**
  - The US2–US4 tests were written first but were not observed red. A mutation check (removing the admin check fails 3 tests) stands in for that.
  - The upgrade test re-runs V43's SQL rather than rewinding Flyway.
- **Numbering:** the earlier "068 (cancellation vs. 067's session lock)" idea is now **069**, if the owner wants it.

### Closed later the same day

- **068 merged:** rish1789/CMS2#30. CI was green on the head; the owner merged it.
- **Windows launch confirmed:** the owner started the backend on their Windows machine with the wrapper (`.\backend\gradlew.bat -p backend bootRun`, after loading `.env`), and started the frontend with `npm --prefix frontend run dev`. Both ran.
- **`NVD_API_KEY`, owner decision (2026-10-01): defer, but required before launch.**
  - The app is not live with real patient data yet, and Dependabot covers dependency updates meanwhile.
  - **Before the first deployment with real patient data:** the owner requests a free key at https://nvd.nist.gov/developers/request-an-api-key and adds it as the repository secret `NVD_API_KEY`.
  - In the same change, raise the backend job's `timeout-minutes` (45 → about 90): the first NVD download adds about 20 minutes.
  - Run one full scan, fix any serious findings, and keep the scan on from then on.

### Convergence 065–068 (rish1789/CMS2#32, merged)

- **065, 066 and 067 converged** with no findings. Every FR and SC was checked against the code, along with the named tests.
- **068 had two LOW findings**, appended as Phase 8 and implemented in the same PR:
  - **T033 (FR-006):** the doctor concerned can read their own prices at a clinic even after their role there was deactivated. Writes stay clinic-admin-only. The access check runs before "doctor not found", so outsiders cannot probe doctor ids. Test-first: red 403, then green.
  - **T034:** removed three repository queries that 068 left dead (the doctor-wide readiness queries and `DoctorDefaultFeeRepository.findByDoctorProfile_Id`).
- **Verification:** full backend suite 1,113 passed, 0 failed, `spotlessCheck` green; CI green.

### State at end of day (2026-10-01) — superseded by Part 18

- **`main` is the only branch.** The owner deleted every merged `claude/…` branch; there are no open PRs.
- **Every tracked feature is converged.** Nothing required is open.
- **Optional, only if the owner asks:** 069, the gap between cancellation and 067's session lock.
- **`NVD_API_KEY`:** deferred (see above). **The owner asked not to be reminded about it**; raise it only if they bring it up, or when a real deployment is actually being prepared.

## Part 18 — Live-audit repairs, 2B, Phase 3 decisions; 3C paused mid-way (2026-10-01, cloud sandbox)

Everything below was driven by `docs/NEXT_PHASES_ACTION_PLAN.md` and `docs/LIVE_SOFTWARE_AUDIT_2026-10-01.md`. Every package was test-first, got a browser check against a real backend with synthetic data, and went through its own PR. The owner merged each PR after CI was green.

### Merged

| PR | Spec | What it fixed |
|---|---|---|
| rish1789/CMS2#34 | 069 | Live-audit findings 1–3: a no-show shown as "Visit complete" and as the next visit; cancel offered when it can't succeed. The server derives `visitOutcome` and cancellation eligibility. |
| rish1789/CMS2#36 | 070 | Finding 4: login now returns to the clinic and doctor picked in discovery (validated `returnTo`). |
| rish1789/CMS2#37 | 071 | Finding 5: a 429 was unreadable in the browser. CORS now runs before the rate limiter on throttled paths, `Retry-After` is exposed, and signup shows rate-limit, unknown and network errors. |
| rish1789/CMS2#38 | 072 | Finding 6: discovery stopped at 20 results. It now has page controls, an `X-Total-Count` header (body unchanged) and a unique tie-break sort. |
| rish1789/CMS2#39 | 073 | Finding 7: doctors saw admin-only tools. One role table drives the sidebar, tiles and direct URLs, and every role at the current clinic is used. Frontend only. |
| rish1789/CMS2#40 | 074 | Phase 2B, PB-001/PB-002: a duplicate phone gave "slot already booked" or a 500. All three staff paths now return 409 `PATIENT_PHONE_ALREADY_REGISTERED`, naming the existing patient; nothing is merged. The owner merged this with the naming as proposed. |

All seven live-audit findings are fixed. The last full backend suite, on the 074 head, passed **1,158/0/0**; Vitest passed 526/526.

### Phase 3 decisions (rish1789/CMS2#41, docs only, open and green at the time of writing)

`docs/PHASE_3_DECISION_RECORD.md` records the owner's answers:

- **3C, staff deactivation and login (B-05, B-06, B-07):**
  - A staff member with **no active role at any clinic** loses existing sessions on the next request and cannot log in.
  - Login errors follow the **industry standard (OWASP/NIST)**: one generic message.
  - **5 failed attempts lock an identifier for 15 minutes**, whether or not the account exists.
- **3D, loss of verification (PB-009):**
  - **No new bookings on any path** while a clinic or doctor is de-verified, and they stay out of search.
  - **Re-verifying restores both.**
  - Permanent deletion stays the existing guarded Super Admin tool.

### Paused: 3C, spec 075 (branch `claude/075-login-hardening`, WIP commit, **no PR yet**)

The branch is stacked on #41's branch. The owner paused the work while the full backend suite was running; that run was stopped and gave no results.

**Done and green:**
- **V44 `login_attempt` table:** keyed by realm and a SHA-256 hash of the normalised identifier, so unregistered emails are never stored.
- **Lockout code:** `common.login.LoginAttemptGuard` (row lock, `REQUIRES_NEW`; configurable through `app.login.max-failures`, `window-minutes` and `lock-minutes`) and `LoginAttemptState`, the pure arithmetic.
- **Login errors:** staff and patient login return `INVALID_CREDENTIALS` (401). An unknown identifier still runs a bcrypt comparison, so timing doesn't reveal which accounts exist. A lockout returns 429 `TOO_MANY_LOGIN_ATTEMPTS` with `Retry-After`.
- **Deactivated staff:**
  - `NO_ACTIVE_CLINIC_ACCESS` (403) after a correct password.
  - The `StaffSessionPolicy` interface is checked in `StaffJwtAuthenticationFilter`; `ActiveRoleStaffSessionPolicy` is the real implementation.
  - `@WebMvcTest` slices import `support.AllowAllStaffSessionsTestConfig`.
- **Retired:** the old `AccountNotFoundException` and `IncorrectPasswordException` classes in both realms. The tests that pinned the old codes were updated to the decided behaviour.
- **Login forms:** both show the lockout wait time, via `lib/loginLockout.ts`.
- **Tests:**
  - `LoginHardeningTest` 9/9 against real Postgres. It covers identical errors, the lockout for real and unregistered identifiers, concurrent failures, the deactivated-session cutoff, and a member deactivated at one clinic only.
  - `LoginAttemptStateTest` 5/5.
  - Contract slices 147/147.
  - Vitest 528/528; lint at the 24 baseline.

**Left to do, in order:**
1. **Run the full backend suite.** About 11 integration test files mint staff tokens for account ids that don't exist (`issueToken(UUID.randomUUID())`). Those tokens now get 401 where a test may expect 403 or 404. Update each such assertion to the decided behaviour, or give the fixture a real account with an active role, whichever keeps the test's intent.
2. **Fix a spec wording slip:** 062's refusal code is `CLINIC_NOT_ACTIVE`; `spec.md` says `STAFF_CLINIC_NOT_ACTIVE`.
3. **Runtime check:**
   - the generic errors;
   - the lockout, using a short `app.login.lock-minutes` to see it expire;
   - a deactivated staff session cut off in the browser.
4. **Docs:** a progress row, the B-05 and B-07 statuses, and the plan line. Then open the PR.
5. **Then 3D (spec 076)**, per the decision record.

### Session notes

- **Docker:** the daemon died twice; `nohup dockerd &` brings it back.
- **Stopping processes:** `pkill -f` and `pgrep -f` match their own shell command line. Use `ps -eo pid,args | grep '[j]ava -jar'`.
- **Stacking:** 072 and 073 were stacked on the previous PR, because they edited adjacent doc lines and the same CORS line. Merging in order kept every diff clean.

## Reference

Memory files at `C:\Users\risha\.claude\projects\C--Users-risha-OneDrive-Documents-CMS2\memory\`
— gradle bootstrap (updated this session with the `/tmp`-is-ephemeral gotcha), OneDrive
build-corruption workaround, Docker/Testcontainers sandbox limitation, Impeccable Architect mode
ground rules.

---
Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
