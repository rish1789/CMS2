# Implementation Plan: Discovery Pagination (072)

**Spec**: [spec.md](spec.md)

## Backend

- `DiscoverySearchService.resolveSort`: append `dp.id ASC, c.id ASC` to the chosen sort. These aliases are already in the query, and Spring Data appends them to `ORDER BY` the same way it does for `a.name`.
- `DiscoveryResultRepository.count(...)`: a `SELECT COUNT(ra)` with the **same** `WHERE` clause as `search`.
- `DiscoverySearchService.count(...)`: applies the same input normalisation as `search`, through one shared helper so the two cannot drift.
- `DiscoveryController.search`: returns `ResponseEntity<List<DiscoveryResult>>` with the `X-Total-Count` header. The body is unchanged.
- `CorsConfig`: the exposed headers become `Retry-After` and `X-Total-Count`.

Why a separate count query rather than `Page<…>`: the `SELECT new …` constructor projection over a multi-entity join is a poor fit for Spring Data's derived count. An explicit count with the identical predicate is clearer.

## Frontend

- `searchDiscovery` takes `page` and `size` and returns `{ results, totalCount }`. `totalCount` comes from the header. If the header is unreadable, it falls back to `page * size + results.length`, so the controls never offer a page that may not exist.
- `DiscoverySearch`:
  - holds a `page` state, which is reset whenever the debounced query, a filter or the sort changes;
  - guards against stale responses with a request sequence number, replacing the `cancelled` flag so Retry can reuse it;
  - shows a Retry button on error;
  - renders `PaginationControls` (item label "doctors") below the list when `totalCount > 0`, and moves focus back to the results heading after a page change.

## Tests (first, red)

- **Integration** `DiscoverySearchPagingTest`, with 25 same-name doctors:
  - pages 0 and 1 together contain 25 distinct pairs;
  - `X-Total-Count` is 25 on both;
  - a repeated fetch gives the same order;
  - a page past the end is empty with the total;
  - a filtered total counts only filtered matches;
  - the CORS expose header is present.
- **Unit** `DiscoverySearchServiceTest`: the sort carries the tie-breakers.
- **Vitest** `DiscoveryPaging.test.tsx`:
  - Next and Previous send the page;
  - changing a filter resets the page;
  - a stale response is ignored;
  - Retry repeats the request;
  - no controls are shown for an empty result;
  - Next is disabled on the last page.
- Existing `DiscoverySearch.test.tsx` mocks change to the new return shape. Their assertions stay the same.

## Constitution check

- Test-first: yes.
- No schema change.
- Public endpoint: no tenant scope applies.
- YAGNI: reuses the existing `PaginationControls`, and adds no new endpoint.
