# Research: Frontend Shared API Client

## Decision 1: Client function signature

**Decision**:

```ts
// frontend/src/lib/apiClient.ts
export class ApiError extends Error {
  readonly status: number
  readonly body: unknown
  constructor(status: number, message: string, body: unknown) { ... }
}

export async function apiRequest<T>(
  path: string,
  options: { method?: string; token?: string; body?: unknown } = {},
): Promise<T>
```

`apiRequest` builds the URL from `API_BASE_URL` (moved into `apiClient.ts`, no longer redeclared per-file) + `path`, sets `Content-Type: application/json` when a body is present, injects `Authorization: Bearer <token>` when `token` is supplied, and on a non-2xx response parses the body as JSON (falling back to `undefined` if parsing fails) and throws `ApiError` with a message resolved per Decision 2.

**Rationale**: Matches the exact common shape already used by all 31 existing `api.ts` files (verified by sampling `booking-detail`, `waitlist`, `staff-booking`, `scheduling`, `inbox`, `patient-booking`) — same base URL pattern, same bearer-header pattern, same "caller supplies the token" contract (no feature reads token storage itself; that stays each feature/component's own concern, preserving the three-JWT-realm separation).

**Alternatives considered**: A class-based client with configured base URL/interceptors (axios-like) — rejected as more machinery than 31 simple request functions need (Constitution Principle II).

## Decision 2: Error message priority (the actual bug fix)

**Decision**: `ApiError`'s message resolves as: the backend body's own `message` field if present and non-empty → else a caller-supplied per-call fallback (each migrated feature keeps its own `defaultMessageFor`-style switch, now used only as the fallback, not the default) → else a generic `"Request failed (<status>)."`.

**Rationale**: This is the exact inversion of the confirmed bug (`defaultMessageFor(body) ?? body.message`, where `defaultMessageFor` always returns a string, making `body.message` unreachable — confirmed directly in `patient-booking/api.ts:144`). The fix keeps each feature's existing per-error-code fallback messages (they're useful, human-friendly copy) but only as a *fallback*, not the default.

**Alternatives considered**: Dropping the per-feature fallback messages entirely and always showing the raw backend message — rejected; a message-less error body (network failure, a 500 with no body) still needs a sensible fallback, and the existing hand-written fallback copy (e.g. "This slot is no longer available.") is better UX than a raw status code in that case.

## Decision 3: Migration scope for this pass

**Decision**: 4 of 31 features, smallest-first: `booking-detail` (37 lines, simplest — no message parsing at all today, just a status code), `partial-session-cancellation` (69 lines, the exact file the original roadmap named as its own suggested starting point), `patient-booking` (the file with the directly-confirmed live bug — proves the fix on a real, high-traffic case), `waitlist` (another real multi-endpoint file). The remaining 27 stay on their current pattern.

**Rationale**: Verifying a migration carefully (compile, full test suite, and for the bug-fix file, a new test proving the fixed priority) takes real time per file; migrating all 31 in one pass without that care would violate this project's own established verification discipline. 4 files span the full range of complexity found in the codebase (zero-message-parsing → multi-endpoint-with-message-parsing) and include the one file where the fix is externally observable, which is enough to prove the pattern and ship a real user-facing fix now. The remaining 27 are a natural, well-precedented follow-up (not a hidden gap — stated explicitly in spec.md's Assumptions and this plan).

**Alternatives considered**: All 31 in one pass — rejected as impractical to verify with real care in one session; 1 file only — rejected as insufficient to prove the pattern generalizes across the actual variety of shapes found (simple status-only errors vs. multi-case message-driven ones).

## Decision 4: Existing test impact

**Decision**: Component-level tests (e.g. `BookingContextHeader.test.tsx`, `BookSlotForm.test.tsx`) mock each feature's `api.ts` module directly via `vi.mock(...)`, not `fetch` itself — confirmed by reading `tests/booking-detail/BookingContextHeader.test.tsx` and `tests/patient-booking/BookSlotForm.test.tsx`. This means migrating `getBookingDetail`/`bookSlot`'s *internal* implementation to call the shared client has zero effect on these tests, as long as the exported function signature, return type, and thrown error class/shape are unchanged. The existing `BookSlotForm.test.tsx` cases that construct `new BookSlotApiError({ error: 'SLOT_ALREADY_BOOKED' })` (no `message` field) are unaffected by the fix either way, since both old and new priority fall back to the same default when no backend message is present.

**Rationale**: This means the fix needs one *new* test per migrated feature that has message-driven errors (`patient-booking`, `waitlist`) — constructing an error *with* a `message` field and asserting that exact message wins — since no existing test exercises that path today (a real, confirmed test-coverage gap the bug was hiding behind).
