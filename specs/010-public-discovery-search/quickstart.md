# Quickstart: Public Discovery Search

See [data-model.md](./data-model.md) and [contracts/discovery-search.md](./contracts/discovery-search.md).

## Prerequisites

- Backend running with PostgreSQL and Flyway migrations applied (no new migration in this feature — through 007's `V4` is sufficient).
- A clinic registered (001) and Super-Admin-verified (002).
- A doctor onboarded (004/005/007) at that clinic, with an active Role Assignment, license verified, and `visible = true`.

## Scenario 1 — Fully eligible doctor is discoverable, with no auth

1. `GET /api/v1/discovery/search` with **no `Authorization` header at all**. **Expect**: `200`, and the array includes an entry for the doctor set up in Prerequisites, showing `doctorName`, `specialization`, `clinicName`, `clinicAddress`.

## Scenario 2 — Any one failing condition removes the doctor from results

1. Starting from Scenario 1's eligible doctor, de-verify the clinic (003's admin flow). `GET /api/v1/discovery/search`. **Expect**: the entry is gone.
2. Re-verify the clinic. Reset the doctor's license (edit the license number, 006/008). `GET /api/v1/discovery/search`. **Expect**: the entry is gone again.
3. Re-verify the license (007's verify action). Turn `visible` off (008's edit action). `GET /api/v1/discovery/search`. **Expect**: still gone.
4. Turn `visible` back on. Deactivate the doctor's Role Assignment at that clinic (005's deactivation flow). `GET /api/v1/discovery/search`. **Expect**: still gone.
5. Reactivate the Role Assignment (or onboard fresh). `GET /api/v1/discovery/search`. **Expect**: the entry reappears — confirming eligibility is evaluated live, not cached, in both directions.

## Scenario 3 — Text search narrows results

1. With two or more eligible doctors of different specializations set up, `GET /api/v1/discovery/search?q={one doctor's specialization}`. **Expect**: only doctors whose specialization contains that text (case-insensitive) appear.
2. `GET /api/v1/discovery/search?q={part of one doctor's name}`. **Expect**: only that doctor appears.
3. `GET /api/v1/discovery/search?q={part of a clinic's name or address}`. **Expect**: only doctors at that clinic appear.
4. `GET /api/v1/discovery/search?q=doesnotmatchanything`. **Expect**: `200` with an empty array, not an error.
5. `GET /api/v1/discovery/search?q=%20` (whitespace only). **Expect**: identical to Scenario 1's unfiltered result.

## Scenario 4 — Multi-clinic doctor only appears for their verified clinic

1. Give one doctor an active Role Assignment at two different clinics — Clinic A (verified) and Clinic B (not yet verified) — with license verified and visible on.
2. `GET /api/v1/discovery/search`. **Expect**: exactly one entry for this doctor, with `clinicId`/`clinicName` matching Clinic A; no entry referencing Clinic B.

## Scenario 5 — Frontend search page

1. Open the Discovery Search page (`frontend/src/features/discovery/DiscoverySearch.tsx`) with no login/session of any kind.
2. Type a specialization into the search box. **Expect**: the result list updates to only matching, eligible providers, with no login prompt at any point.
