# Tasks: Discovery Pagination (072)

**Input**: [spec.md](spec.md), [plan.md](plan.md)

- [X] T001 Branch from current `main`, merged with the 071 branch: `X-Total-Count` must join 071's exposed-headers list.
- [X] T002 Write `DiscoverySearchPagingTest` (integration) and a unit sort tie-break test. Expect RED.
- [X] T003 Implement the tie-break sort, the count query, the header and the CORS exposure. T002 should then be GREEN; every `discovery` test should still pass.
- [X] T004 Write Vitest `DiscoveryPaging.test.tsx`. Expect RED.
- [X] T005 Update the API client and `DiscoverySearch`, and adjust the existing tests' mocks to the new shape. T004 should then be GREEN.
- [X] T006 Gates: the full backend suite with `spotlessCheck`; `tsc`, lint (24 baseline), Vitest and build.
- [X] T007 Runtime check: seed 25 or more synthetic eligible doctors (including duplicate names), then browse every page in headless Chromium using the keyboard. Confirm a filter change resets to page 1.
- [X] T008 Docs: a progress row, the audit finding 6 status and the plan status. Then commit, push and open the PR.

## Observed results (2026-10-01, IST)

- **T001:** branched from `main` @ `d1e0afe` (#36), merged with `claude/071-readable-429` (#37). That merge is needed because `X-Total-Count` joins 071's exposed-headers line. Once #37 merges, this PR's diff is 072 alone.
- **T002 (red):** 5 of 6 new tests failed:
  - 4 integration tests (no `X-Total-Count`, nothing exposed);
  - the unit tie-break test.

  `theSameRequestReturnsTheSameOrder` already passed, because Postgres happened to return ties in a stable order on this small table. It stays as a guard. The unit test is the one that pins the tie-break itself.
- **T003 (green):** all 37 `com.cms.discovery.*` tests and the 7 `RateLimitCorsIntegrationTest` tests pass.
- **T004 (red):** 7 of 7 failed.
- **T005 (green):** 7 of 7 pass.
  - The existing `DiscoverySearch.test.tsx` mocks moved to the `{ results, totalCount }` shape: 8 lines, with no assertion changed.
- **T006:**
  - Backend: full suite **1,146 passed, 0 failed, 0 skipped** (1,140 + 6), with `spotlessCheck` green, in 23 min.
  - Frontend: `tsc` clean; lint exit 0 with the 24 baseline warnings (the `DiscoverySearch` effect warning was already in the baseline); Vitest **503/503 in 83 files**; build OK.
- **T007 (runtime):** fresh Postgres 16, the 072 jar on port 8090, Vite and headless Chromium.
  - **Seed (synthetic):** 27 eligible doctors, 25 of them named "Dr. Same Name": 17 in Pune and 10 in Noida.
  - **curl** (page 1, allowed origin): `X-Total-Count: 27` and `Access-Control-Expose-Headers: Retry-After, X-Total-Count`.
  - **Browser:**
    - Page 1 shows 20 results ("Page 1 of 2"). Focus on Next plus Enter (keyboard only) shows page 2 with 7 results.
    - Across both pages: **27 distinct, 0 duplicates**.
    - After the page change, focus is on the "Search results" heading, and Next is disabled on the last page.
    - Choosing City = Noida resets to page 1 with "1-10 of 10 doctors", and the filter is kept.
- **Live-audit status:** finding 6 (discovery stops after 20 results) is **fixed**.
