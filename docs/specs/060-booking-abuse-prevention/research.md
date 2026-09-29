# Research: Booking Protection / Appointment Abuse Prevention

## Decision 1: Concurrency guard for the booking-limit and rate-limit checks

**Decision**: A database-level guard, not application-level locking alone. Specifically:

- **Rate limit**: every booking attempt (success or failure) inserts one row into a new
  `booking_attempt_log` table *before* any other work happens. The rate-limit check counts rows for
  that `patient_account_id` within the configured window using a straightforward
  `COUNT(*) ... WHERE patient_account_id = ? AND attempted_at >= ?` read, immediately followed by the
  attempt's own insert in the same transaction. Two simultaneous requests from the same patient both
  read a count, but each also inserts its own row inside its own transaction — the count that matters
  for correctness is the one enforced by the *next* attempt after both have committed, which will
  correctly see both rows. This makes the boundary case (the Nth vs. N+1th attempt landing at exactly
  the same instant) a "one of the two may slip through as the Nth" race, which is acceptable: it
  bounds the burst to at most one extra attempt beyond the configured threshold, never an unbounded
  bypass, and matches the spec's own framing of the limit as a threshold to catch bulk/bot abuse, not
  a hard real-time semaphore.
- **Booking limit**: stricter, because spec.md FR-006 and NFR-003 require the limit to hold exactly,
  not "within one." The count-then-insert sequence (count active bookings for this patient, then
  insert the new `Booking` row if under the limit) is wrapped so that a `SELECT ... FOR UPDATE`-style
  row lock is taken on a single, stable per-patient row for the duration of the check-and-insert —
  concretely, locking the `patient_account` row itself (already a natural, existing, low-contention
  per-patient serialization point) for the duration of the transaction that both counts and inserts
  the booking. This is a new pattern for this codebase (existing precedent solves a different
  problem — see below) but is the smallest change that gives an exact guarantee: two concurrent
  requests for the same patient are serialized by the row lock, so the second one always sees the
  first's freshly-inserted row when it re-reads the count.

**Rationale**: This codebase already has an established, working concurrency idiom for booking
creation — a unique index (`uq_booking_slot` on `booking.slot_id`) plus catching
`DataIntegrityViolationException` after a `saveAndFlush` (`PatientBookingService.bookSlot`). That
idiom solves "two different patients racing for the same slot" — a uniqueness constraint on a
single row is a perfect fit there. It does not translate to "count this patient's total active rows
and reject the Nth" — there is no single row whose uniqueness can encode "at most N of these exist,"
so a different mechanism is needed for this specific check. Locking the patient's own account row
for the count-then-insert window is the narrowest addition that closes the race without introducing
a new table-wide lock or a new library.

**Alternatives considered**:
- *Optimistic locking (`@Version`) on `Booking`*: doesn't apply — the race is over a *count* of rows
  belonging to one patient, not a conflicting update to one existing row.
- *Serializable transaction isolation for the whole booking-creation transaction*: rejected as
  needlessly broad — it would also serialize unrelated concurrent bookings by other patients hitting
  the same table, hurting throughput for a guarantee this feature only needs scoped to one patient
  at a time.
- *In-memory per-patient lock (e.g. a `ConcurrentHashMap<UUID, Lock>`)*: rejected — doesn't hold
  under horizontal scaling (multiple backend instances), and the existing `RateLimitingFilter`'s
  in-memory approach is already documented in this codebase as a known limitation for exactly that
  reason; this feature should not repeat it for a check that needs to be exact.

## Decision 2: Rate-limit storage and mechanism — no new dependency

**Decision**: A plain new table (`booking_attempt_log`), read with an ordinary indexed
`COUNT(*)`/`SELECT` query, written with an ordinary `INSERT` — no new library (Bucket4j,
Resilience4j, Redis) and no reuse of the existing `RateLimitingFilter`.

**Rationale**: The existing `RateLimitingFilter` (`com.cms.common`) is a deliberately
dependency-free, in-memory, per-path, IP-keyed servlet filter registered ahead of Spring Security's
own filter chain — it runs *before* authentication, which is exactly why it can only key on IP. This
feature needs the opposite: per-*account* keying, which is only available after Spring Security has
authenticated the request and populated the `Authentication` principal — i.e., inside the
controller/service layer, using the same `SecurityConfig.currentPatientAccountId(authentication)`
pattern already used throughout the patient realm. A persistent table (rather than in-memory) is
additionally required because the same attempt history has to serve capability 3 (admin flagging —
FR-016, FR-020 both read this history), which an in-memory-only counter cannot support once it's
evaluated by a separate, later `@Scheduled` sweep. Introducing a rate-limiting library for a
single-table counted-window check would be new complexity with no corresponding new capability
(Constitution II).

**Alternatives considered**:
- *Extend `RateLimitingFilter` with a 5th registered path for booking creation*: rejected — it's
  structurally IP-only (registered ahead of authentication), so it cannot key on
  `patientAccountId`, the identifier this feature actually needs (every booking endpoint already
  requires a valid patient JWT — spec.md's own confirmed-existing-behavior section).
  Booking-creation could additionally register for IP-based limiting via this exact existing
  mechanism as a *defense-in-depth* layer with its own separate, generous threshold, but that is an
  optional hardening addition, not a substitute for the account-keyed limiter this feature requires
  — noted as an option for `tasks.md` to size, not a blocking decision here.
- *A caching layer (e.g. count cached and invalidated per attempt)*: rejected for v1 as premature —
  the query is a single indexed `COUNT` per attempt, well within this feature's own latency goal
  (Technical Context), and adding a cache invalidation strategy before there's a measured performance
  problem is exactly the speculative complexity Constitution II rules out.

## Decision 3: Module placement and dependency direction

**Decision**: Split by synchronous-vs-asynchronous responsibility, not by "this all belongs to one
new module":

- The two **synchronous, request-blocking checks** (booking limit, rate limit) live inside the
  existing `com.cms.booking` module, as a new `BookingProtectionService` called from the very start
  of both `PatientBookingService.bookSlot` and `PatientQueueBookingService.bookSlot` — the two
  existing entry points a self-service patient's booking request always goes through.
- Everything **read-only or asynchronous** (the settings a Super Admin/ClinicAdmin configure, flag
  detection, flag review) lives in one new module, `com.cms.protection`, which depends one-way on
  `com.cms.booking` (reads `Booking` and the new `BookingAttemptLog`) and `com.cms.scheduling` (reads
  `Slot` for the no-show signal). Nothing depends on `protection` — it is a pure downstream consumer.

**Rationale**: The alternative — putting *everything* (including the synchronous checks) in a new
`protection` module — creates an unavoidable dependency cycle. `protection` would need to read
`booking`'s data (to count active bookings, to read attempt history) and `booking` would need to
call `protection`'s check before creating a booking, i.e. `booking → protection → booking`. This
codebase has exactly one prior instance of this exact shape of problem — 059-patient-clinical-record-
access needed `clinical` to read `booking`'s `Booking` entities while also needing a
booking-adjacent surface for patients — and it was resolved the same way this plan resolves it here:
by keeping the capability that must originate the call (there, a new patient-facing read endpoint;
here, a synchronous precondition inside booking creation) inside the module that already owns the
call site, rather than introducing a reverse dependency. `booking` already owns other booking-
creation preconditions of exactly this shape (fee resolution, slot-availability, patient linking) —
adding "is this patient under their limit / not rate-limited" as one more precondition, using
booking-owned data only, is consistent with what the module already does, not a new concern grafted
on. `protection`, in turn, mirrors the already-established `clinical → booking` and
`booking → scheduling` one-way dependency precedents exactly — nothing new about the *shape* of that
dependency, only its content.

**Alternatives considered**:
- *Single new `protection` module owning everything, with `booking` calling into it*: rejected — see
  above, creates a real cycle (not merely an awkward one), which Constitution III (and this
  codebase's own established one-way-dependency convention for `scheduling`) forbids.
- *Event-driven: `booking` publishes a "booking attempted" event, `protection` listens and does both
  the gating and the flagging*: rejected for the *gating* half specifically — a published-event
  listener is inherently asynchronous (fire-and-forget or eventually-consistent), and this feature's
  booking-limit/rate-limit checks must block the request synchronously *before* the booking is
  created (spec.md FR-002, FR-010) — an event arriving after the fact cannot un-create a booking that
  already succeeded. Event-driven *would* be a reasonable shape for the flag-detection half alone,
  but see Decision 4 below for why a periodic sweep was chosen there instead, keeping the whole
  `protection` module's interaction with `booking`/`scheduling` as one-way reads with no inbound
  calls or event subscriptions from either.
- *Fold the whole feature into `com.cms.booking`, including admin flagging and settings*: rejected —
  admin flag review is staff/ClinicAdmin-realm surface area with its own controllers, its own
  cross-clinic-visibility rules (spec.md FR-022, BR-004), and its own settings entity that a Super
  Admin (not booking-realm) configures; folding it into `booking` would blur a module boundary
  Constitution III asks to keep cohesive (booking's job is creating/cancelling bookings, not
  reviewing patient behavior patterns).

## Decision 4: Flag detection trigger — a periodic sweep, not an event listener

**Decision**: `protection.FlagDetectionService` runs as a `@Scheduled` periodic sweep (cadence to be
sized in `tasks.md`, e.g. hourly), evaluating all five signals (spec.md FR-016–FR-020) by querying
`booking` and `scheduling`'s repositories directly — the same one-way read relationship already
established in Decision 3, with no event subscription, no listener, no inbound call from either
module.

**Rationale**: This codebase already has exactly this shape of periodic-sweep detector —
`scheduling.NoShowDetectionService`, a `@Scheduled` job that sweeps `Slot` rows past their grace
period. Reusing that established pattern for flag detection is the simplest design that satisfies
the requirement (Constitution II) and requires zero new infrastructure (no event bus, no message
queue, no new Spring `ApplicationEventPublisher` wiring). It also naturally satisfies spec.md's
explicit non-requirement that flags never block anything — a sweep that runs independently of the
request path structurally cannot introduce request-path latency or become a new availability
dependency for booking (spec.md NFR-001).

**Alternatives considered**:
- *Evaluate signals inline, synchronously, right after the triggering event (e.g. check the
  cancellation-count signal immediately inside the cancellation endpoint)*: rejected — this would
  require `booking`'s cancellation flow to call into `protection` after the fact, reintroducing the
  exact reverse-dependency problem Decision 3 avoids, for a capability (flag detection) that spec.md
  explicitly does not require to be real-time (flags are for later human review, not an immediate
  gate).
- *Spring `ApplicationEventPublisher` events, listened to by `protection`*: technically avoids a
  compile-time dependency cycle (the publisher doesn't need to know its listeners), but is genuinely
  new infrastructure this codebase doesn't currently use anywhere, for a benefit (near-real-time
  flagging vs. sweep-interval-delayed flagging) the spec doesn't ask for — rejected under
  Constitution II.

## Decision 5: Settings mechanism — fallback-to-default read pattern

**Decision**: `protection.ProtectionSetting` is a small table of named rows (one row per
configurable value — global cap, rate-limit threshold/window/cooldown, five signal
thresholds/windows, three protection-enabled toggles). `ProtectionSettingService` exposes one read
method per named value; if no row exists yet for a given name, the method returns a hardcoded
default (the exact values and reasoning are already fixed in spec.md's Assumptions section) rather
than throwing or blocking. A Super Admin write endpoint upserts a row by name. This is the first
runtime-editable admin setting anywhere in this codebase (spec.md's own confirmed-existing-behavior
section notes today's `@Value`/`application.yml` pattern has no runtime-editable precedent) — scoped
narrowly to the 16 named values enumerated in data-model.md's settings table, not a general
settings framework.

**Rationale**: A named-row table (rather than one column per setting on a single fixed-shape row) is
the smallest schema that (a) needs no migration to add a future setting name — though this feature
adds no such future-proofing mechanism beyond what today's fixed set requires (Constitution II), it
is simply the natural shape a small key→value table takes — and (b) makes "no row yet = default"
trivial to express as a repository `findByName(...).map(...).orElse(default)` per value, matching
this codebase's own established `Optional`-based repository-then-fallback idiom used throughout
(e.g. `DoctorProfileRepository.findByAccount_Id(...).map(...).orElse(null)` fail-closed patterns
already fixed earlier in this project).

**Alternatives considered**:
- *A single fixed-column `protection_settings` row (one row, one column per setting)*: workable, but
  a named-row table reads more naturally against spec.md's own framing of each value as an
  independently-toggleable, independently-editable item (FR-026, FR-027), and keeps the "no row =
  default" fallback uniform across every value rather than needing a nullable column per setting.
- *Cache settings in memory, refreshed periodically*: rejected for v1 as premature (see Decision 2's
  parallel reasoning) — a settings table read is a single-row lookup by name, not a scan, and every
  booking-time check already does several other small reads in the same request.

## Decision 6: Check ordering and precedence

**Decision** (already fixed during `/speckit-clarify`, restated here for implementation traceability):
rate limit is checked first, inside `BookingProtectionService.checkAndRecordAttempt`, before the
booking-limit check, before any of `PatientBookingService.bookSlot`'s existing preconditions (slot
lookup, fee resolution, patient linking). A patient currently in cooldown always sees the cooldown
message, never the limit-reached message, even if both would independently apply. The attempt is
recorded to `booking_attempt_log` exactly once per call, with whichever outcome was actually reached
(`RATE_LIMITED`, `LIMIT_REACHED`, `SUCCESS`, or `OTHER_FAILURE` for every existing failure mode
below this feature's new checks, e.g. slot already taken) — see data-model.md for the outcome
enumeration.

## Decision 7: Authorization pattern for the new endpoints

**Decision**: Reuse existing, established patterns exactly, no new authorization mechanism:

- **Super Admin settings** (`/api/v1/admin/protection-settings`): `SuperAdminSecurityConfig`'s
  existing broad `.anyRequest().authenticated()` already covers any new path under `/api/v1/admin/**`
  — no new matcher needed, confirmed by reading that class.
- **ClinicAdmin flag review and clinic-limit-override** (`/api/v1/clinics/{clinicId}/protection/**`):
  two things are required, both reusing exact existing idioms: (a) an explicit new
  `.requestMatchers(...).authenticated()` entry in `identity.account.config.SecurityConfig` for each
  new path — this codebase has a documented, twice-real recurring bug where a new authenticated staff
  path is forgotten and silently falls through to `anyRequest().permitAll()`; and (b) a
  role-specific check requiring the caller hold **ClinicAdmin** specifically at that clinic (not "any
  active role," which several endpoints fixed earlier in this project's history were found to
  incorrectly use for data that should have been role- or self-scoped) — using
  `RoleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(accountId, clinicId,
  RoleAssignment.Role.ClinicAdmin)`, the exact method already used by `ScheduleService` for its own
  "must be ClinicAdmin" gate.

**Rationale**: Both patterns are proven, tested, and already the established convention for
exactly this shape of check in this codebase — reusing them is both the simplest option
(Constitution II) and the one least likely to reintroduce the authorization gaps found and fixed
earlier in this project's history.

## Decision 8: Migration numbering and schema ownership

**Decision**: Two new Flyway migrations, continuing the existing `V<n>__<snake_case>.sql` sequence
(latest existing migration is `V35`):

- `V36__booking_protection_log_and_override.sql` — `booking_attempt_log`,
  `clinic_booking_limit_override`, and `clinic_booking_limit_override_change_log`, all owned by
  `booking`.
- `V37__protection_settings_and_flags.sql` — `protection_setting`, `suspicious_activity_flag`, and
  `protection_setting_change_log`, all owned by the new `protection` module.

**Rationale**: Splitting by module ownership (rather than one migration for the whole feature)
matches this codebase's convention of one feature-scoped concern per file while keeping each
migration's ownership obvious from its content — `booking`'s own schema changes are reviewable
independently of `protection`'s new module being introduced at all. Each change-log table lands in
the same migration as the current-state table it audits, since they're always created together.

## Decision 9: Two separate settings-audit logs, not one shared table

**Decision**: `AUD-002`/`AUD-003` (full change history for every settings write) is met with two
separate, module-owned append-only tables — `ClinicBookingLimitOverrideChangeLog` in `booking`,
`ProtectionSettingChangeLog` in `protection` — rather than one shared "settings audit log" table
covering both.

**Rationale**: `ClinicBookingLimitOverride` lives in `booking` (Decision 3 — it's read synchronously
by the booking-limit check, alongside `Booking` itself) and `ProtectionSetting` lives in
`protection` (Decision 5). A single shared audit table would need writes from *both* modules: the
`booking`-owned `ClinicBookingLimitOverrideController` would need to write into it, and so would the
`protection`-owned `SuperAdminProtectionSettingController` — meaning at least one of the two
modules would need to depend on wherever that shared table's entity/repository lives. Since
`protection` already depends on `booking` one-way (Decision 3), a shared table owned by `protection`
would be fine for `protection`'s own writes but would force `booking` to newly depend on
`protection` just to log its own setting's history — reintroducing the exact cycle Decision 3
avoids. Keeping each change-log next to the table it audits, in the same module, needs no new
cross-module dependency in either direction: each controller writes to a repository already inside
its own module.

**Alternatives considered**:
- *One shared `SettingChangeLog` table, owned by a new shared location (e.g. `com.cms.common`)*:
  would avoid the cycle (both `booking` and `protection` could depend on `common`, mirroring how
  `RateLimitingFilter` already lives there) and is not unreasonable — but two settings, two owning
  modules, two small tables with near-identical shape is simpler to reason about than introducing
  `common` as a third dependency target for this feature, for a benefit (a single unified audit
  query across both setting types) the spec never asks for. Rejected under Constitution II; revisit
  if a future feature needs a genuinely shared, general-purpose settings-audit mechanism.
- *Have `booking` publish an event, `protection` logs it*: rejected for the same reason Decision 4
  rejected an event-driven approach for flag detection — new infrastructure this codebase doesn't
  use anywhere else, for a problem two small same-module tables already solve.
