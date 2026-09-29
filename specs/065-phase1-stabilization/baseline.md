# Pre-change baseline (T001) — 2026-09-29

Captured before any 065 code change.

| Check | Result |
|---|---|
| `git status --short` | 326 M, 239 RM, 247 ??, 16 D, 9 RD (pre-existing uncommitted work; not touched) |
| Backend `spotlessCheck` | pass |
| Backend `test -x spotlessApply --continue` | 620 entries: 371 pass, 249 fail. All 249 failures are integration-class `initializationError`: "Could not find a valid Docker environment". |
| Frontend `tsc -b` | pass |
| Frontend `npm run lint` | pass, 24 warnings |
| Frontend `npm run test` | **436/436 pass** in this run (53 s total). On 2026-09-28 the same suite produced 6 timeouts (148 s). The failure is **load-dependent and intermittent**. This supports BUG-006's timing root cause; it is not a deterministic test failure. |
