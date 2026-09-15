# `_diagnostics/` — Instructions for an LLM/Agent

You are reading the output of a one-time, point-in-time diagnostic scan of the CMS2 codebase
(`C:\Users\risha\OneDrive\Documents\CMS2`) that traced frontend↔backend↔database data flow for
structural, architectural, and contract desyncs. This file tells you how to use everything else in this
directory. Read this file in full before touching any finding.

## What this is, and what it is not

- This is a **snapshot audit**, not a live linter and not a spec-kit artifact. It does not replace or
  supersede any `specs/NNN-*/` document, and fixing a finding here does not require re-running the
  `speckit-*` pipeline unless the fix is large enough to constitute new feature work in its own right (see
  "How to act on a finding" below).
- Every finding was produced by an LLM agent doing static tracing (reading source, not executing it) —
  see the confidence caveat in `claude_context/03_db_schema.md`'s CRITICAL finding specifically. Treat
  every finding as **high-confidence, not verified-by-execution**, because this development sandbox
  cannot run this project's own integration tests (Testcontainers cannot reach Docker here — a
  pre-existing, documented limitation of the environment itself, unrelated to this scan).
- The codebase may have changed since this scan ran. **Before fixing anything, re-read the cited file at
  the cited line and confirm the finding still describes the current code.** If it doesn't, treat the
  finding as stale/resolved rather than acting on a description of code that no longer exists.

## Directory map

```
_diagnostics/
├── README.md                          ← you are here
├── claude_context/                    ← dense, tagged, file:line-precise — read these to fix things
│   ├── 00_cross_cutting_patterns.md   ← READ FIRST: 3 root causes behind ~40% of all findings
│   ├── 01_frontend_state.md           ← races, staleness, polling, role-aware rendering
│   ├── 02_api_layer.md                ← contract gaps, missing UI, unreachable endpoints
│   ├── 03_db_schema.md                ← migrations vs entities vs enums; the one CRITICAL finding
│   └── 04_integration_gaps.md         ← composition/reachability (no router, orphaned components)
└── human_summary/                     ← plain-language, for the project owner — do not use these to fix code
    ├── 01_executive_summary.md
    └── 02_critical_faults.md
```

## Reading order

1. **`claude_context/00_cross_cutting_patterns.md` first, always.** It documents 3 systemic root causes
   (a message-precedence bug, a missing-default-case silent-failure bug, and a reused wrong-domain error
   message) that together explain ~15 of the ~38 total findings. Fixing each pattern **once**, in the
   shared code shape it appears in, resolves every listed instance — do not patch each instance
   independently without first checking whether it's tagged as an instance of a pattern in this file.
2. Then `01`–`04` in any order, depending on which finding you were asked to address. Each finding not
   already covered by `00` is fully self-contained: tag, exact file:line on both sides, one-sentence
   break description, and a specific fix.
3. Only read `human_summary/` if you need non-technical language to relay to a human — it contains no
   file paths or code and should never be used as your source of truth for what to change.

## Finding tag format

Every finding is tagged `[SEVERITY] - [COMPONENT] - [ISSUE_TYPE]`, e.g.
`[CRITICAL] - [BOOKING] - [FK_INTEGRITY_GAP]`. To locate a specific finding by tag, grep for the bracketed
string across `claude_context/*.md`.

## Severity → suggested handling

- **CRITICAL** (currently: 1 finding, `03_db_schema.md`): stop and fix before any further patient-booking
  work ships. Verify against a real database if at all possible before merging — this is the one finding
  in the whole scan not yet confirmed against a live Postgres.
- **HIGH**: fix promptly; each is either a confirmed silent-failure/wrong-message user-facing defect, or a
  fully-built backend capability with no way for a real user to reach it.
- **MEDIUM**: fix when touching the affected area, or batch several together — none are urgent in
  isolation, but several compound with each other (e.g. `01_frontend_state.md`'s Inbox findings).
- **LOW**: cosmetic, type-accuracy, or currently-masked-but-latent issues. Fine to defer or bundle into
  unrelated work in the same file.

## How to act on a finding

1. Re-open the cited file:line on **both** sides (frontend and backend, or migration and entity) and
   confirm the finding still applies to current code.
2. Apply the **specific fix described** — do not expand scope. If a finding's fix is "add a `default:`
   case," add exactly that; do not refactor the surrounding file, rename things, or "improve" adjacent
   code that wasn't flagged. This project's constitution (`.specify/memory/constitution.md`, Principle II)
   is explicit about avoiding unjustified complexity.
3. This project's constitution (Principle I) requires tests before implementation for new behavior. Most
   fixes here are small enough (a missing `switch` case, a precedence swap, a message string) that the
   existing test suite's assertions on the surrounding function are sufficient — but if a fix changes
   observable behavior (e.g. the `Booking.bookedByAccountId` fix in `03_db_schema.md`, which needs a new
   nullable column and a migration), write or update a test proving the new behavior before/alongside the
   implementation, per that principle.
4. If a fix requires a new Flyway migration: follow existing numbering (`V24__...sql`, the next integer
   after the last one present in `backend/src/main/resources/db/migration/`), and never edit or renumber
   an existing shipped migration.
5. After fixing, do not delete the corresponding finding from these files unless the user explicitly asks
   you to prune this directory — instead, treat this directory as a durable audit record. If you want to
   track resolution status, add a line like `**STATUS: FIXED in <file>:<line>, <date>**` directly under
   the finding rather than removing it.

## What NOT to do

- Do not treat `human_summary/` content as more authoritative than `claude_context/` content — the
  human-facing files intentionally omit code detail and can be imprecise about exact mechanism by design.
- Do not batch-fix everything in one sweep without re-verification per finding — several findings note
  explicitly that they are "currently masked" or "not yet a live bug," and blindly patching them without
  understanding why could introduce a regression in the one case where the current behavior is
  intentional.
- Do not use this directory as a substitute for running this project's real test suite once a real
  Postgres/Docker environment becomes available — several findings, especially the CRITICAL one, need
  actual execution to move from "high-confidence static finding" to "confirmed."
