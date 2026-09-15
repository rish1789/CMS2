# Clinic Management System — Feature Backlog

Source document: `clinic-management-system-BDD-2.md` (Business Design Document, v2).

This backlog breaks that document into **39 atomic, fully-groomed features**, each in its own file, ready to run through spec-kit's full lifecycle via `/speckit-orchestrate`. This is a **fresh build** — nothing in this repo is implemented yet; the source document describes target behavior, not existing code.

Every file follows the same structure: User Story → Context → Business Rules → Acceptance Criteria (Given/When/Then) → Dependencies → Explicitly Out of Scope → Source References. Business rules are written to preserve the source doc's exact numbers, ordering, and trigger conditions rather than paraphrasing them loosely.

## How to use this backlog

**Recommended:** run `/speckit-orchestrate` — it drives one feature (or the whole backlog) through the full spec-kit lifecycle (specify → clarify → plan → tasks → analyze → implement → converge) automatically, respecting each stage's own gates:

- One feature: `/speckit-orchestrate backlog/001-clinic-registration.md`
- The whole backlog in build order, pausing between features for review: `/speckit-orchestrate backlog`
- The whole backlog, unattended between features (still stops on any critical gate): `/speckit-orchestrate backlog auto`

It tracks progress in `backlog/progress.md` so a batch run can resume where it left off.

Manual/step-by-step alternative: work through features in the order in `build-order.md`, and for each one run `/speckit-specify` with that file's content, followed by `/speckit-clarify`, `/speckit-plan`, `/speckit-tasks`, `/speckit-analyze`, `/speckit-implement`, and `/speckit-converge` yourself.

---

## Feature Index (by module)

### Identity & Access
| # | Feature | File |
|---|---|---|
| 001 | Clinic Registration | [001-clinic-registration.md](001-clinic-registration.md) |
| 002 | Super Admin Clinic Verification | [002-super-admin-clinic-verification.md](002-super-admin-clinic-verification.md) |
| 003 | Staff Login (Password or Staff Code) | [003-staff-login-password-or-code.md](003-staff-login-password-or-code.md) |
| 004 | Staff Onboarding (Direct-Hire) | [004-staff-onboarding-direct-hire.md](004-staff-onboarding-direct-hire.md) |
| 005 | Doctor Profile Auto-Creation & License Verification Queue | [005-doctor-profile-auto-creation-license-queue.md](005-doctor-profile-auto-creation-license-queue.md) |
| 006 | Doctor License Edit Triggers Re-Verification Reset | [006-doctor-license-edit-reverification-reset.md](006-doctor-license-edit-reverification-reset.md) |
| 007 | Last Active ClinicAdmin Protection | [007-last-active-clinicadmin-protection.md](007-last-active-clinicadmin-protection.md) |
| 008 | De-Verification Cascade (Auto-Cancel Future Bookings) | [008-deverification-cascade-auto-cancel-bookings.md](008-deverification-cascade-auto-cancel-bookings.md) |
| 039 | Patient Account & Global Login | [039-patient-account-global-login.md](039-patient-account-global-login.md) |

### Scheduling & Session Generation
| # | Feature | File |
|---|---|---|
| 009 | Recurring Schedule Definition | [009-recurring-schedule-definition.md](009-recurring-schedule-definition.md) |
| 010 | Multi-Clinic Doctor Schedule Overlap Block | [010-multi-clinic-doctor-schedule-overlap-block.md](010-multi-clinic-doctor-schedule-overlap-block.md) |
| 011 | Nightly Rolling Session Generation (15-Day Horizon) | [011-nightly-rolling-session-generation.md](011-nightly-rolling-session-generation.md) |
| 012 | Fixed-Time Session Slot Pre-Generation | [012-fixed-time-slot-pregeneration.md](012-fixed-time-slot-pregeneration.md) |
| 013 | Queue/Token Session Slot-on-Booking Generation | [013-queue-mode-slot-on-demand-generation.md](013-queue-mode-slot-on-demand-generation.md) |
| 014 | Schedule Edit Non-Retroactivity | [014-schedule-edit-non-retroactivity.md](014-schedule-edit-non-retroactivity.md) |

### Booking
| # | Feature | File |
|---|---|---|
| 015 | Fee Resolution & Locking at Booking Time | [015-fee-resolution-and-locking.md](015-fee-resolution-and-locking.md) |
| 016 | Staff-Assisted Fixed-Time Booking | [016-staff-assisted-fixed-time-booking.md](016-staff-assisted-fixed-time-booking.md) |
| 017 | Patient Self-Service Fixed-Time Booking | [017-patient-self-service-fixed-time-booking.md](017-patient-self-service-fixed-time-booking.md) |
| 018 | Queue/Token Booking | [018-queue-token-booking.md](018-queue-token-booking.md) |
| 019 | Patient Record Auto-Creation & Phone-Based Linking | [019-patient-record-auto-creation-phone-linking.md](019-patient-record-auto-creation-phone-linking.md) |

### Day-of Operations
| # | Feature | File |
|---|---|---|
| 020 | Walk-In / Priority Insertion | [020-walk-in-priority-insertion.md](020-walk-in-priority-insertion.md) |
| 021 | Automatic No-Show Detection | [021-automatic-no-show-detection.md](021-automatic-no-show-detection.md) |
| 022 | Buffer Slot Capacity Sizing | [022-buffer-slot-capacity-sizing.md](022-buffer-slot-capacity-sizing.md) |
| 023 | Session Delay Tracking (Fixed-Time Only) | [023-session-delay-tracking.md](023-session-delay-tracking.md) |
| 024 | Queue Position Tracking (Queue-Mode Only) | [024-queue-position-tracking.md](024-queue-position-tracking.md) |

### Cancellation & Waitlist
| # | Feature | File |
|---|---|---|
| 025 | Individual Booking Cancellation & Waitlist Trigger | [025-individual-booking-cancellation-waitlist-trigger.md](025-individual-booking-cancellation-waitlist-trigger.md) |
| 026 | Whole-Day Session Cancellation | [026-whole-day-session-cancellation.md](026-whole-day-session-cancellation.md) |
| 027 | Partial (Cutoff-Based) Session Cancellation | [027-partial-cutoff-session-cancellation.md](027-partial-cutoff-session-cancellation.md) |
| 028 | Waitlist Matching (Longest-Waiting, Doctor/Specialization) | [028-waitlist-matching-longest-waiting.md](028-waitlist-matching-longest-waiting.md) |
| 029 | Self-Service Waitlist Claim | [029-self-service-waitlist-claim.md](029-self-service-waitlist-claim.md) |

### Clinical Documentation
| # | Feature | File |
|---|---|---|
| 030 | Consultation Note Creation | [030-consultation-note-creation.md](030-consultation-note-creation.md) |
| 031 | Prescription + Items Creation | [031-prescription-and-items-creation.md](031-prescription-and-items-creation.md) |
| 032 | External Record Reference | [032-external-record-reference.md](032-external-record-reference.md) |

### DPDP Compliance
| # | Feature | File |
|---|---|---|
| 033 | Patient Immediate Anonymization | [033-patient-immediate-anonymization.md](033-patient-immediate-anonymization.md) |
| 034 | Monthly Automatic Retention Purge | [034-monthly-retention-purge.md](034-monthly-retention-purge.md) |

### Discovery & Notifications
| # | Feature | File |
|---|---|---|
| 035 | Public Discovery Search | [035-public-discovery-search.md](035-public-discovery-search.md) |
| 036 | Notification Event Pipeline & Opt-In/Out | [036-notification-event-pipeline-opt-in-out.md](036-notification-event-pipeline-opt-in-out.md) |
| 037 | Notification Delivery Stub (Log-Only Send) | [037-notification-delivery-stub.md](037-notification-delivery-stub.md) |

### Inbox
| # | Feature | File |
|---|---|---|
| 038 | Unified Real-Time Inbox | [038-unified-realtime-inbox.md](038-unified-realtime-inbox.md) |

---

## Build Order

See **[build-order.md](build-order.md)** for the precise, dependency-resolved build sequence — derived directly from every feature file's `Dependencies` section (not just a rough module ordering). It also documents three real dependency cycles/overstatements found in the graph and how each was resolved, so nothing in the sequence is ever blocked on a ticket that comes later.

---

## Resolved Scope Decisions (v1)

These were open questions in the source document (§7.9, §8) or gaps the backlog-grooming pass surfaced. All were decided explicitly for this v1 build — treat them as settled, not as items to re-litigate during implementation unless something concrete changes.

| # | Decision | Chosen for v1 |
|---|---|---|
| 1 | Clinical record retention window | Fixed 3 years, hardcoded system-wide constant (not clinic-configurable) |
| 2 | Clinic/doctor license verification process | Fully manual, off-platform review — Super Admin flips a verified flag, no in-app document upload |
| 3 | Online payment collection | Out of scope — `paymentStatus` stays a manual staff-flipped flag, no gateway |
| 4 | Notification delivery | Stubbed — full event pipeline built, but the send step logs only, no real provider |
| 5 | Reschedule | Cancel-then-rebook only; no atomic reschedule feature |
| 6 | Self-service waitlist claim | **Built** (reverses the source doc's gap) — in-app claim/decline action |
| 7 | Live "session in progress" tracking | Slot-level status only (OPEN/BOOKED/COMPLETED/NO_SHOW); no session-level state machine |
| 8 | Buffer-sizing constants (90-day window, 5-sample floor, 20%/3-slot caps) | Hardcoded system-wide, not clinic-configurable |
| 9 | Platform billing/subscription model for clinics | Out of scope entirely — no Subscription/Plan/Invoice entities |
| 10 | Multi-clinic doctor travel-time buffer | Not built — overlap-only check remains, accepted operational risk |
| 11 | De-verification cascade (clinic or doctor) | **Cascades** — auto-cancels all future bookings for the affected clinic/doctor |
| 12 | Walk-in-to-self-registered patient history merge | **Built, automatic** — linked by exact phone number match, no staff confirmation step |
| 13 | Insurance / sliding-scale discounting | Out of scope — every fee is a flat cash amount |
| 14 | Grievance Officer field | Dropped entirely for v1 — not collected, not stored |
| 15 | Multi-language / localization | English-only for v1 |
| 16 | Founding ClinicAdmin permanence | **Confirmed intended** — no path to a second ClinicAdmin exists in v1, so the founding admin's role can never be removed by anyone, including Super Admin |
| 17 | Patient self-service cancellation cutoff | 2 hours before scheduled slot time |
| 18 | Partial (cutoff-based) session cancellation definition | Time-based cutoff (a clock time), applied uniformly to fixed-time and queue-mode sessions |
| 19 | Waitlist doctor-match vs. specialization-only priority | Doctor-match is an absolute priority tier — never outranked by a longer-waiting specialization-only entry |
| 20 | Waitlist offer expiry/decline behavior | Cascades automatically to the next-longest-waiting eligible entry, fresh 30-minute window each time |

---

## Explicitly Out of Scope (v1, system-wide)

Carried forward from the source document's own out-of-scope list, reconfirmed above:
- Online payment collection, refunds, reconciliation
- Any file/document upload
- Insurance, sliding-scale pricing, discounting
- A single global patient medical record (patient records stay clinic-scoped; only login identity is global)
- Live notification delivery (email/SMS/push/WhatsApp)
- Address validation, geocoding, distance-based search
- True atomic reschedule
- Platform billing/subscription model for clinics
- Multi-clinic travel-time scheduling checks
- Grievance Officer public workflow
- Multi-language localization
