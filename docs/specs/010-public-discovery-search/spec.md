# Feature Specification: Public Discovery Search

**Feature Branch**: `010-public-discovery-search`

**Created**: 2026-09-03

**Status**: Draft

**Input**: User description: "Public Discovery Search — an unauthenticated visitor can search for clinics and doctors, with the underlying query itself (not response-level filtering) excluding any clinic that is not Super-Admin-verified and any doctor who is not simultaneously license-verified, visibility-toggled-on, and attached to a verified clinic via an active staff assignment. Listing only — no address validation, geocoding, distance ranking, localization, or sponsored placement. (Full source: backlog/035-public-discovery-search.md)"

## Scope Decisions Made During Drafting

No prior conversation turn resolved these — they're research-backed defaults made while writing this spec, not a `/speckit-clarify` session (which runs next and may revisit any of them).

- **007 (doctor-profile-license-queue) already built the exact data-layer gate this feature needs.** `DoctorProfileRepository.findDiscoveryEligible()` returns doctors where `licenseVerified = true AND visible = true AND` an active Role Assignment exists at a `verified` clinic — the conjunction of all three conditions the backlog source and this spec both require, already enforced as a query predicate (not response filtering), already exercised by four passing integration tests (`DiscoveryEligibility*GateTest`). This feature's job is exposing that guarantee — and the equivalent clinic-level guarantee — through a public, unauthenticated search surface, not re-deriving the gating logic. See Assumptions.
- **"Search by specialization, name, or location text" (from the acceptance criteria) is read as: match against doctor specialization, doctor name (via `Account.name`), clinic name, and clinic address — case-insensitive substring matching**, since no backlog text specifies exact match semantics, structured filters, or a fielded query syntax, and substring/case-insensitive text search is the standard default for a v1 "search box" experience explicitly scoped as listing-only (no geocoding/ranking).
- **An empty or absent search term returns the full eligible listing** (every verified clinic/visible-and-verified doctor), rather than an error or empty result — consistent with "browse all legitimate providers" being a reasonable entry point, and nothing in the source material suggests a search term is mandatory.
- **Results are doctor-centric rows carrying their clinic's identity**, not two separate clinic/doctor result sets, because every acceptance criterion is phrased as "that clinic/doctor" together and a doctor is only ever meaningful in the context of the (verified) clinic they're actually assigned to via their active Role Assignment.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Only Fully-Verified Providers Are Discoverable (Priority: P1)

A prospective patient searches the public directory. Regardless of what they search for, they only ever see clinics that Super Admin has verified, and only doctors at those clinics whose license is verified and who have chosen to be publicly visible. The guarantee holds even though nothing in the request identifies who is asking — it's enforced by what the query is allowed to return, not by hiding rows after the fact.

**Why this priority**: This is the entire reason Discovery exists as a gated feature rather than a plain directory listing — the platform's core trust promise (never point a real patient at an unverified clinic or an unverified doctor) lives here. Every other capability in this feature is worthless without it.

**Independent Test**: Seed a mix of clinics/doctors in every combination of {clinic verified/unverified} × {doctor license verified/unverified} × {doctor visible/hidden} × {Role Assignment active/inactive}, call the search endpoint with no filter and with no authentication, and confirm the result set contains exactly the rows where all four conditions hold — verifiable directly against the seeded data without needing to inspect any application-layer filtering code.

**Acceptance Scenarios**:

1. **Given** a clinic that is not yet Super-Admin-verified, **When** a public user searches with no filters, **Then** that clinic and every doctor assigned to it are absent from results, regardless of the doctors' own license/visibility status.
2. **Given** a verified clinic with a doctor whose license is not yet verified, **When** a public user searches, **Then** that doctor is absent from results even though the clinic itself appears (via its other, eligible doctors, if any).
3. **Given** a verified clinic with a doctor who has a verified license but has turned their visibility toggle off, **When** a public user searches, **Then** that doctor is absent from results.
4. **Given** a verified clinic with a doctor who has a verified license, visibility on, and an active Role Assignment at that clinic, **When** a public user searches, **Then** that doctor appears in results, shown together with their clinic's details.
5. **Given** a doctor who meets every visibility condition but whose Role Assignment at that clinic has been deactivated, **When** a public user searches, **Then** that doctor is absent from results.
6. **Given** a clinic or doctor that previously appeared in results is later de-verified (clinic de-verified, or doctor license reset to unverified per 006/008), **When** the search is run again, **Then** they no longer appear — the result reflects current status on every call, not a cached snapshot from before the change.
7. **Given** the search request carries no authentication token of any kind, **When** the request is made, **Then** it still succeeds and still applies every verification condition in full — omitting auth never widens what is visible.

---

### User Story 2 - Finding a Specific Provider by Specialization, Name, or Location (Priority: P2)

A prospective patient has something specific in mind — a type of doctor, a doctor's name they were referred to, or a clinic near a place they know — and narrows the eligible listing down with a search term instead of scanning every verified provider.

**Why this priority**: Without any way to narrow results, the directory is only usable at small scale; filtering is what makes it a "search" rather than a static list. It builds directly on User Story 1's eligible set and adds no new visibility rule of its own.

**Independent Test**: Seed several discovery-eligible doctors (per US1's gating) with distinct specializations, account names, and clinic names/addresses; issue searches with each kind of term in isolation; confirm each returns only the matching subset, still restricted to the eligible set.

**Acceptance Scenarios**:

1. **Given** multiple eligible doctors across different specializations, **When** a public user searches by a specialization term, **Then** only eligible doctors whose specialization matches (case-insensitive, partial match) appear.
2. **Given** multiple eligible doctors, **When** a public user searches by (part of) a doctor's name, **Then** only eligible doctors whose name matches appear.
3. **Given** multiple eligible clinics, **When** a public user searches by (part of) a clinic's name or address text, **Then** only eligible doctors at clinics whose name or address matches appear.
4. **Given** a search term that matches no eligible doctor or clinic, **When** the search is run, **Then** an empty result set is returned (not an error).
5. **Given** no search term is supplied, **When** the search is run, **Then** every currently eligible doctor/clinic pairing is returned.

---

### Edge Cases

- What happens when a clinic has zero eligible doctors (verified clinic, but every doctor there is unverified, hidden, or inactively assigned)? → The clinic contributes no rows to the doctor-centric result set; it is not shown as an empty placeholder entry.
- What happens when the same doctor holds active Role Assignments at two different clinics, one verified and one not? → The doctor appears once per eligible (verified) clinic assignment; the unverified clinic's assignment contributes no row.
- What happens when a search term is only whitespace? → Treated the same as no search term (full eligible listing), not as a literal-whitespace match.
- What happens when a doctor's license is later re-verified after being reset (006/008), or a de-verified clinic is later re-verified? → They become eligible again on the very next search call, with no separate re-listing step — eligibility is always evaluated live.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a public search capability that requires no authentication and no Patient Account to invoke or to view results.
- **FR-002**: System MUST return, for any search (filtered or unfiltered), only doctor/clinic pairings where all of the following hold simultaneously: the clinic is Super-Admin-verified; the doctor's license is verified; the doctor's public-visibility toggle is on; the doctor has an active staff (Role) assignment at that specific clinic.
- **FR-003**: System MUST enforce the FR-002 conditions as part of the data query itself (a filtering predicate evaluated by the persistence layer), not as an in-memory or response-stage filter applied after an unfiltered fetch.
- **FR-004**: System MUST evaluate eligibility fresh on every search call — a clinic or doctor that no longer meets FR-002 (e.g. following a de-verification) MUST stop appearing on the next call, with no separate cache-invalidation or re-indexing step required by this feature.
- **FR-005**: System MUST allow searching with an optional free-text term that matches (case-insensitive, partial/substring) against doctor specialization, doctor name, clinic name, or clinic address; a request with no term (or a whitespace-only term) MUST return the full current eligible set.
- **FR-006**: For each result row, system MUST return enough clinic and doctor identity/detail to let a prospective patient recognize and choose a provider (at minimum: clinic name, clinic address, doctor name, doctor specialization) — no clinical, contact-credential, or account-security data of any kind.
- **FR-007**: System MUST NOT apply address validation, geocoding, distance-based ranking, sponsored/paid placement, or any language other than English to search or results.

### Key Entities

- **Clinic** (from 001/002, read-only here): supplies `name`, `address`, and the `verified` flag this feature's clinic-side gate reads.
- **DoctorProfile** (from 005/006/008, read-only here): supplies `specialization` and the `licenseVerified` / `visible` flags this feature's doctor-side gate reads; identified via its linked `Account` for the doctor's display name.
- **Account** (from 004, read-only here): supplies the doctor's display `name`.
- **Role Assignment** (from 004, read-only here): the active, clinic-scoped staff assignment linking a Doctor's Account to a specific Clinic — the join this feature uses to know *which* verified clinic a discoverable doctor belongs to.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of search results, across every tested combination of clinic/doctor verification and visibility state, contain zero ineligible clinic/doctor pairings — verified directly against seeded data, independent of any specific search term.
- **SC-002**: 100% of a representative set of de-verification events (clinic de-verified, doctor license reset) are reflected by the very next search call — no eligible-then-ineligible row ever appears after the underlying status changes.
- **SC-003**: 100% of search requests carrying no authentication succeed and return correctly gated results — zero requests are rejected for lack of auth, and zero requests return a broader result set than an authenticated equivalent would.
- **SC-004**: A prospective patient can locate a specific known provider (by specialization, name, or location text) among a directory of 100+ eligible entries in a single search call, with only matching entries returned.
- **SC-005**: 100% of returned result rows contain only listing-appropriate identity fields (clinic name/address, doctor name/specialization) — zero rows expose account credentials, contact-security data, or any clinical information.

## Assumptions

- **This feature reuses 007's `DoctorProfileRepository.findDiscoveryEligible()` gate rather than re-implementing doctor-side eligibility.** That method already encodes exactly the three-condition doctor-side gate (license verified, visible, active Role Assignment at a verified clinic) required here, as a query-level predicate, with existing passing tests. This feature's own scope is the clinic-side verified gate (trivial — `Clinic.verified`), the public/unauthenticated search endpoint itself, and free-text filtering on top of the already-eligible set — not re-deriving eligibility logic that already exists and is already tested (Constitution Principle II: no speculative re-abstraction of a solved problem).
- **No new entity or schema is introduced.** Every field this feature reads (`Clinic.verified/name/address`, `DoctorProfile.licenseVerified/visible/specialization`, `Account.name`, `RoleAssignment.active`) already exists from prior features; this feature only adds a read-only, public-facing query/endpoint over them.
- **The search endpoint is genuinely public** — per Constitution's Technology & Platform Constraints (multi-tenancy note), this is one of the intentional exceptions to clinic-scoping: it deliberately reads *across* all clinics (that's the point of a discovery directory), gated by verification status rather than by tenant.
- **No pagination, sorting, or rate-limiting behavior is specified** by the source material; a reasonable default (return the full matching set for v1 scale, standard web-service abuse protections at the infrastructure level) applies and is not re-specified here as a functional requirement.
- **"Location text" match is limited to the clinic's stored `address` string** (substring match) — there is no separate structured city/region/pincode field anywhere in the existing `Clinic` schema to search against instead, and introducing one is explicitly out of scope per the backlog ("no geocoding").
