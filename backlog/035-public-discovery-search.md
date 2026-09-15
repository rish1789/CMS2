# 035 — Public Discovery Search

**Module:** Discovery & Notifications
**Status:** Ready for spec-kit intake

## User Story
As a prospective patient (unauthenticated visitor), I want to search for verified clinics and doctors, so that I can find a legitimate, publicly-listed provider to book with, without needing to log in first.

## Context
Discovery is the public-facing entry point into the platform — it must only ever surface clinics and doctors that have actually passed verification, and it must enforce that at the data layer so an unverified record can never leak through by accident. BDD §2, §3.7.

## Business Rules
- The search endpoint is **public and unauthenticated** — no login or Patient Account required to search or view results.
- Discovery enforces verification **at the data level, not just response filtering** — the underlying query itself excludes unverified records; it is not a matter of fetching everything and filtering the response afterward (which would be a weaker, bypassable guarantee).
- A clinic must be Super-Admin-verified (see 002) to appear in results.
- A doctor must have BOTH a verified license (see 005/008) AND their public-visibility toggle turned on to appear in results — all three conditions (clinic verified, doctor license-verified, doctor visibility on) must hold simultaneously.
- Search results are informational/listing only in this feature — no address validation, geocoding, or distance-based ranking (explicitly out of scope, see below).
- All result content is in English only for v1 (no localization).

## Acceptance Criteria
- Given a clinic that is not yet Super-Admin-verified, when a public user searches, then that clinic and all its doctors are excluded from results, regardless of any other field values.
- Given a verified clinic with a doctor whose license is not yet verified, when a public user searches, then that doctor is excluded from results even though the clinic itself is verified.
- Given a verified clinic with a doctor who has a verified license but has turned their public-visibility toggle off, when a public user searches, then that doctor is excluded from results.
- Given a verified clinic with a doctor who has a verified license and visibility on, when a public user searches (e.g. by specialization, name, or location text), then that clinic/doctor appears in results.
- Given the search endpoint is called without any authentication token, when the request is made, then it succeeds (no auth required) and still applies the full verification filter.
- Given a clinic or doctor is de-verified after previously appearing in results (see 008), when the search is run again, then they no longer appear.

## Dependencies
- Depends on: 001-clinic-registration, 002-super-admin-clinic-verification — clinic verification status gates visibility.
- Depends on: 005-doctor-profile-auto-creation-license-queue, 008-deverification-cascade-auto-cancel-bookings — doctor license verification/de-verification status gates visibility.
- Feeds into: 016-staff-assisted-fixed-time-booking, 017-patient-self-service-fixed-time-booking, 018-queue-token-booking — discovery is typically the entry point before a patient books.

## Explicitly Out of Scope
- Address validation, geocoding, or distance-based search/ranking — explicitly out of scope per the source doc.
- Any authentication or Patient Account requirement to search.
- Multi-language/localized search results — English-only for v1.
- Sponsored/paid ranking or any monetized placement — no billing model exists in this system (see README scope notes).

## Source References
- BDD §2 (in-scope: "Public, unauthenticated discovery search"; out-of-scope: "Address validation, geocoding, or distance-based search")
- BDD §3.7 (Discovery & Notifications: "Discovery enforces verification... at the data level, not just response filtering")
