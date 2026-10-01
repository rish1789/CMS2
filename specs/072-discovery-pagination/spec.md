# Feature Specification: Discovery Pagination

**Feature Branch**: `claude/072-discovery-paging`

**Created**: 2026-10-01

**Status**: Draft

**Input**: Phase 2R.4 of `docs/NEXT_PHASES_ACTION_PLAN.md`; finding 6 of `docs/LIVE_SOFTWARE_AUDIT_2026-10-01.md` ("Discovery silently stops after 20 results").

## Context

`GET /api/v1/discovery/search` already accepts `page` and `size`. The default size is 20, and the size is capped at 50 (047 FR-001). It returns a bare JSON array, with no total and no "has next" indicator. The discovery page sends neither parameter and has no paging controls, so matching doctors beyond the first 20 cannot be reached.

There is a second, quieter problem: the only ordering is the chosen sort field, for example doctor name. With `LIMIT`/`OFFSET`, rows that tie on that field (two doctors with the same name, or one doctor at several clinics) have no defined order. They can be skipped or repeated across pages.

**What changes:**
- Results are ordered by the chosen field, then by a unique key: doctor profile id, then clinic id.
- The response carries the total number of matches in an `X-Total-Count` header, which browsers can read.
- The page has paging controls, using the shared `PaginationControls`.

**What does not change:**
- The JSON body: still an array of the same objects.
- The eligibility rules, filters, sort options, the default size (20) and the cap (50).

## Decisions

- **Metadata goes in a header, not the body.** The body's bare-array shape is the published contract (specs 010, 035 and 047), and many tests rely on it. A header adds the missing total without breaking any client. `X-Total-Count` is added to the shared CORS exposed headers. That list is introduced by spec 071, so this branch builds on it.
- **Total, not just `hasNext`.** The shared `PaginationControls` shows "Page X of Y" and a record range, and both need the total.
- **No snapshot consistency.** If eligible doctors change between page requests, pages can shift. The tie-break guarantees no duplicates or omissions only for an unchanged dataset (plan acceptance).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Every match is reachable (Priority: P1)

A patient searches and 25 doctors match. The first page shows 20 and "Page 1 of 2". Next shows the remaining 5. Previous returns to the first 20.

**Independent Test:** Seed 25 eligible doctors, all with the same name. Page 0 and page 1 together return 25 distinct (doctor, clinic) pairs, and the header reports 25 on both.

**Acceptance Scenarios**:

1. **Given** 25 matches, **When** page 0 and then page 1 are fetched, **Then** together they contain each match exactly once, and `X-Total-Count` is 25.
2. **Given** the same unchanged data, **When** a page is fetched twice, **Then** the order is identical.
3. **Given** a page past the end, **When** it is fetched, **Then** the body is an empty array and the total is still reported.
4. **Given** filters, **When** a page is fetched, **Then** the total counts only filtered matches.
5. **Given** an allowed browser origin, **When** it searches, **Then** `X-Total-Count` is listed in `Access-Control-Expose-Headers`.

### User Story 2 - Paging and filters work together (Priority: P1)

**Acceptance Scenarios**:

1. **Given** the patient is on page 2, **When** they change a filter, the sort or the search text, **Then** the page resets to 1 and the filters are kept.
2. **Given** the patient pages forward, **When** the next page loads, **Then** the current filters are sent with the page number.
3. **Given** an older request finishes after a newer one, **When** both resolve, **Then** only the newer result is shown.
4. **Given** a request fails, **When** the patient presses Retry, **Then** the same page and filters are requested again.
5. **Given** no matches, **Then** the empty state shows and the paging controls do not.
6. **Given** the last page, **Then** Next is disabled; **given** the first page, **Then** Previous is disabled.

## Requirements *(mandatory)*

- **FR-001**: Search results MUST be ordered by the selected sort field, then by `doctorProfileId` ascending, then by `clinicId` ascending.
- **FR-002**: The search response MUST include `X-Total-Count`: the number of matches for the same filters, across all pages.
- **FR-003**: `X-Total-Count` MUST be exposed to allowed browser origins via CORS.
- **FR-004**: The discovery page MUST send `page` and `size` (20) and render the shared `PaginationControls` when there is at least one match.
- **FR-005**: Changing the search text, any filter or the sort MUST reset the page to the first page.
- **FR-006**: A response for an outdated request MUST NOT replace the results of a newer one.
- **FR-007**: A failed search MUST show an error with a Retry action that repeats the same request.
- **FR-008**: The body shape, eligibility, filters, default size and size cap MUST be unchanged.

## Success Criteria *(mandatory)*

- **SC-001**: With more than 20 matches, 100% of them are reachable from the page.
- **SC-002**: For an unchanged dataset with duplicate names, there are 0 duplicates and 0 omissions across pages.
