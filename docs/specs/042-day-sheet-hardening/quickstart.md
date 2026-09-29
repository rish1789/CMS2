# Quickstart: Day Sheet Hardening

Manual validation scenarios for this feature, once implemented. Full request/response shapes are in [contracts/day-sheet-hardening.md](contracts/day-sheet-hardening.md); field sourcing is in [data-model.md](data-model.md). Backend Testcontainers-based tests are subject to this project's documented sandbox limitation (written and compiling, execution deferred to a real dev/CI environment) — these scenarios are the manual/browser-level proof.

**Prerequisites**: backend running (`gradlew.bat bootRun`), frontend running (`npm run dev`), a clinic staffed with at least 2 doctors, each with a Schedule and at least one generated Session in the 14-day window (enough sessions that the list spans more than one page at the default page size).

## Scenario 1 — Whole-session cancellation requires confirmation (FR-001/002/003)

1. Open a session's detail page, click "Cancel whole session."
2. **Expect**: a confirmation step appears describing the consequence; no booking is yet cancelled.
3. Click Cancel on that step. **Expect**: nothing changed, no request sent.
4. Click "Cancel whole session" again, then Confirm. **Expect**: cancellation proceeds exactly as it did before this feature (same success message, same booking-cancelled count).

## Scenario 2 — Doctor filter narrows the session list (FR-004)

1. Open the Day Sheet for a clinic with sessions from 2+ doctors in the window.
2. Select one doctor from the filter.
3. **Expect**: only that doctor's sessions remain visible; the record count reflects only their sessions; the filter's own option list did not change based on which page you were on before filtering.

## Scenario 3 — Session list is paginated, not fully downloaded (FR-005/SC-002)

1. With more sessions in the window than the default page size, open the Day Sheet and inspect the network request for the session list.
2. **Expect**: the response contains only one page's worth of `sessions`, plus `page`/`pageSize`/`totalCount` — not every session in the window.
3. Page forward. **Expect**: a new request is made for the next page; the previously-shown sessions are not silently already present in memory from the first load.

## Scenario 4 — `ActionMenu` exclusivity and boundary handling (FR-008/FR-009)

1. On a session's detail page with several booked slots, open the "More" menu on one slot.
2. Open the "More" menu on a different slot. **Expect**: the first one closes automatically.
3. Scroll to the last visible slot and open its "More" menu. **Expect**: the menu is fully visible (flips upward if there isn't room below), never clipped or requiring a scroll to reach.

## Scenario 5 — Session detail survives a direct link/refresh (FR-010/SC-004)

1. Copy a session detail page's URL, open it in a fresh tab (no prior navigation through the Day Sheet list in that tab).
2. **Expect**: the correct doctor name and session date are shown, not a generic "Session" header.
3. Refresh the page. **Expect**: doctor name and date remain correct.

## Scenario 6 — Per-session fullness indicator (FR-011/SC-006)

1. On the Day Sheet list, compare a session card's booked/total indicator against that same session's actual slot list (open its detail page and count `status != OPEN` slots with an active booking).
2. **Expect**: the numbers match.
3. Find (or create) a session with zero generated slots. **Expect**: it shows an explicit "no slots yet" state, not "0 of 0".

## Scenario 7 — Visual consistency (FR-012)

1. View the Roster page and the Day Sheet page back to back.
2. **Expect**: consistent palette, card treatment, avatar style, and hover/interaction states across both.
