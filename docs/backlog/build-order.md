# Build Order

A strict, dependency-resolved sequence for the 39 features in this backlog. Derived by extracting every `## Dependencies` → `Depends on:` edge from each feature file and topologically sorting them — this is not a re-guess at priority, it's the actual dependency graph, walked in an order where nothing is ever blocked on something built later.

If you implement in this exact order (or in parallel within a numbered step where noted), no ticket will ever be blocked waiting on a ticket that comes after it.

## Three dependencies had to be resolved, not just sorted

The raw dependency graph, as written across the 39 files, contains three problems a plain topological sort can't solve on its own. Each is resolved below with a documented reason — treat these as settled, not as things to re-litigate mid-build.

### 1. Circular: 012 (fixed-time slot pre-generation) ↔ 022 (buffer slot sizing)
012's file lists a dependency on 022 ("determines how many pre-generated Slots are reserved as buffer"). But 022 depends on 021 (no-show detection), which depends on 016/017 (bookings), which depend on 012. That's a cycle: 012 → 022 → 021 → 016/017 → 012.

**Resolution:** build 012 first, using the cold-start fallback that 022's own business rules already define — *"minimum 5 data points, else a flat single-slot default applies."* Build 022's full 90-day trailing formula afterward, once 021 exists to feed it real no-show data, and have it upgrade 012's slot-generation logic in place. This isn't a workaround — the flat-default behavior is already part of 022's documented business rules, just applied from day one instead of only as a rare edge case.

### 2. Circular: 029 (self-service waitlist claim) ↔ 036 (notification event pipeline)
029 depends on 036 to notify the patient of an offer; 036's file lists 029 as "a primary event source." That's circular as written.

**Resolution:** 036 is generic pub/sub infrastructure — an event pipeline plus per-patient opt-in/out storage. It doesn't need any particular emitter to exist before it does; emitters (016/017/018 for booking confirmations, 029 for waitlist offers, 008 for cascade notices) just call into it once built. Build 036's core (pipeline + opt-in schema, which does hard-depend on 039 for the patient identity to attach preferences to) early, immediately after 039. Every later feature that needs to emit a notification wires into it as part of its own build.

### 3. Overstated: 035 (public discovery search) → 008 (de-verification cascade)
035's file lists 008 as a dependency ("doctor license verification/de-verification status gates visibility"). But discovery only ever reads the `verified` / `licenseVerified` boolean flags set by 002 and 005 — it doesn't need 008's cascade *behavior* (auto-cancelling future bookings) to exist. 008 changes what happens to existing bookings when a flag flips; it doesn't change how discovery reads the flag.

**Resolution:** decoupled. 035 only hard-depends on 001, 002, 005, and can be built right after those — well before cancellation/waitlist machinery (which 008 depends on) exists.

---

## Ordered Build Sequence

Numbers in brackets are the feature IDs (matching filenames), not build order — the list itself *is* the build order, top to bottom. Items on the same lettered sub-step have no dependency between each other and can be built in parallel.

### Step 1 — Foundational identity & infrastructure
1. **[001]** Clinic Registration
2. **[039]** Patient Account & Global Login *(independent of 001 — parallel with it)*
3. **[002]** Super Admin Clinic Verification — needs 001
4. **[004]** Staff Onboarding (Direct-Hire) — needs 001
5. **[007]** Last Active ClinicAdmin Protection — needs 001 *(parallel with 004)*
6. **[003]** Staff Login (Password or Staff Code) — needs 004
7. **[005]** Doctor Profile Auto-Creation & License Verification Queue — needs 004, 002
8. **[006]** Doctor License Edit Triggers Re-Verification Reset — needs 005
9. **[019]** Patient Record Auto-Creation & Phone-Based Linking — needs 039
10. **[035]** Public Discovery Search — needs 001, 002, 005 *(see resolution #3 above)*
11. **[036]** Notification Event Pipeline & Opt-In/Out — needs 039 *(core infra; see resolution #2 above)*
12. **[037]** Notification Delivery Stub — needs 036

### Step 2 — Scheduling
13. **[009]** Recurring Schedule Definition — needs 005, 001
14. **[010]** Multi-Clinic Doctor Schedule Overlap Block — needs 009
15. **[011]** Nightly Rolling Session Generation (15-Day Horizon) — needs 009
16. **[014]** Schedule Edit Non-Retroactivity — needs 009, 011
17. **[015]** Fee Resolution & Locking at Booking Time — needs 009

### Step 3 — Slot generation & booking core
18. **[012]** Fixed-Time Session Slot Pre-Generation — needs 011 *(built with flat-default buffer; see resolution #1 above)*
19. **[013]** Queue/Token Session Slot-on-Booking Generation — needs 011
20. **[016]** Staff-Assisted Fixed-Time Booking — needs 012, 015
21. **[017]** Patient Self-Service Fixed-Time Booking — needs 012, 015, 019, 039
22. **[018]** Queue/Token Booking — needs 013, 015, 019

### Step 4 — Day-of operations
23. **[021]** Automatic No-Show Detection — needs 016, 017
24. **[022]** Buffer Slot Capacity Sizing — needs 021, 011 *(upgrades 012's flat default now that real no-show data exists)*
25. **[020]** Walk-In / Priority Insertion — needs 022, 021, 015
26. **[023]** Session Delay Tracking (Fixed-Time Only) — needs 012, 020
27. **[024]** Queue Position Tracking (Queue-Mode Only) — needs 013, 018

### Step 5 — Cancellation & waitlist
28. **[025]** Individual Booking Cancellation & Waitlist Trigger — needs 016, 017
29. **[026]** Whole-Day Session Cancellation — needs 011, 016/017/018
30. **[027]** Partial (Cutoff-Based) Session Cancellation — needs 011, 016/017/018
31. **[028]** Waitlist Matching (Longest-Waiting, Doctor/Specialization) — needs 025
32. **[029]** Self-Service Waitlist Claim — needs 028, 036, 015, 039
33. **[008]** De-Verification Cascade (Auto-Cancel Future Bookings) — needs 002, 005, 025

### Step 6 — Clinical documentation
34. **[030]** Consultation Note Creation — needs 016, 017, 018
35. **[031]** Prescription + Items Creation — needs 016, 017, 018, 005
36. **[032]** External Record Reference — needs 016, 017, 018, 005

### Step 7 — DPDP compliance
37. **[033]** Patient Immediate Anonymization — needs 025, 026, 027
38. **[034]** Monthly Automatic Retention Purge — needs 033, 030, 031, 032

### Step 8 — Cross-cutting integration
39. **[038]** Unified Real-Time Inbox — needs 020, 029, 008 *(aggregates work items from all three; build last)*

---

## Step 9-10 addendum (2026-09-15): Production Hardening & UX Redesign

All 39 original features above are Converged. Features 040-052 below are a second wave, added after the original 39-feature build was complete, per a "transform CMS2 into a production-quality SaaS product" request. They are scoped strictly to what the system already has — no billing/payments, no lab tests, no roles beyond ClinicAdmin/Doctor/Operations/Patient/Super Admin — per an explicit user decision to exclude out-of-scope features rather than amend the constitution. See `PRODUCTION_ROADMAP.md` (a prior architect review) as the source for 040-045, and a direct 2026-09-15 codebase audit as the source for 046-052.

### Step 9 — Production Hardening (backend/infra reliability, no UI change)
40. **[040]** Dev Environment & Repository Hygiene — no dependencies, foundational
41. **[041]** CI/CD Quality Gates — benefits from 040
42. **[042]** Backend Module Layering & Security Posture Documentation — no dependency on 040/041
43. **[043]** Frontend Shared API Client — no dependency on 040-042, but should land before 051 (forms) consumes it
44. **[044]** Backend Security & Scale Hardening — needs 035, 036/037, 028/029 (existing, converged)
45. **[045]** Backend Unit-Test Backfill — needs all 39 original features stable; large, may split into sub-passes at planning time

### Step 10 — Design System & UX Redesign (scoped to existing features only)
46. **[046]** Shared UI Component Library — foundational for 047-052
47. **[047]** Application Shell Sidebar Navigation — needs 046
48. **[048]** Staff Operational Dashboard Enhancement — needs 046; benefits from 047
49. **[049]** Patient Context & Clinical History Hub — needs 046; benefits from 047; reads 019, 016-025, 030-033
50. **[050]** Tables & Lists Consistency Pass — needs 046; benefits from 048, 049
51. **[051]** Forms & Inline Validation Consistency Pass — needs 043, 046
52. **[052]** Responsive & Mobile Pass — needs 046, 047, 048, 049, 050, 051

### Step 11 — Post-Redesign Corrective Pass (user-raised, added 2026-09-15)
53. **[053]** Visual Design & Copy Quality Pass — needs 046-052 shipped; starts at user discussion/clarify, not implementation (see the feature file's Context section before doing anything else with this one)

---

## Verification

Every `Depends on:` edge listed in every feature file's Dependencies section resolves to a position strictly earlier in this list than its dependent, with the three exceptions documented above (each converted from a hard blocking dependency into either a staged/layered build or a decoupled one). No ticket in this order is blocked on a ticket that appears after it. The Step 9-10 addendum follows the same rule for 040-052.
