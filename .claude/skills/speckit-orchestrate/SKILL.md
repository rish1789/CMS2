---
name: "speckit-orchestrate"
description: "Run one feature through the complete spec-kit lifecycle end-to-end — specify, clarify, plan, tasks, analyze, implement, converge — invoking each installed speckit-* skill in the right order and looping implement/converge until nothing remains. Optionally walks this project's backlog/build-order.md feature by feature."
argument-hint: "A feature description, a path to a backlog file (e.g. backlog/001-clinic-registration.md), or 'backlog' [auto] to work through backlog/build-order.md in sequence"
compatibility: "Requires spec-kit project structure with .specify/ directory, and the speckit-specify, speckit-clarify, speckit-plan, speckit-tasks, speckit-analyze, speckit-implement, and speckit-converge skills installed alongside this one"
metadata:
  author: "project-custom"
  source: "orchestrates templates/commands/{specify,clarify,plan,tasks,analyze,implement,converge}.md"
user-invocable: true
disable-model-invocation: false
---

## User Input

```text
$ARGUMENTS
```

You **MUST** consider the user input before proceeding (if not empty).

## Purpose

This is not a new spec-kit primitive — it's a conductor. It invokes the existing `speckit-specify`, `speckit-clarify`, `speckit-plan`, `speckit-tasks`, `speckit-analyze`, `speckit-implement`, and `speckit-converge` skills, in the one order that respects their own documented prerequisites, so a feature goes from a description to working, converged code without manually chaining seven commands and remembering which gate belongs where.

It does **not** duplicate any sub-skill's logic. Every actual spec-writing, planning, task-breakdown, analysis, coding, and convergence decision happens inside the sub-skill being invoked, exactly as if the user had run it directly. This skill's only job is sequencing, argument-passing, gate-handling, and reporting.

## Resolve the Input

Determine what's being specified, in this order:

1. **A path to an existing file** (e.g. `backlog/001-clinic-registration.md`, or any relative/absolute path that resolves to a real file): read the file's full content and use it verbatim as the feature description passed to `speckit-specify`. This is the primary way to feed one of this project's `backlog/*.md` files into the pipeline — they are already fully groomed (user story, business rules, acceptance criteria, dependencies, out of scope), so pass the whole file, not a summary.
2. **The literal word `backlog`** (optionally followed by `auto`, e.g. `backlog auto`): enter **Batch Mode** (see below) — walk `backlog/build-order.md` in order, running this whole pipeline once per feature.
3. **Anything else**: treat the argument text itself as the feature description, exactly as `speckit-specify` would.

If `$ARGUMENTS` is empty, ask the user which feature (or `backlog`) to run before doing anything else.

## Stage Sequence (single feature)

Run these stages in order. Each stage means: **invoke that skill exactly as the user would type its slash command**, wait for it to fully finish (including any interactive questions it asks — let it ask them, don't pre-empt or answer on the user's behalf), and read its Completion Report before moving on. Do not skip a stage's own gates described below.

### 1. Specify

Invoke `speckit-specify` with the resolved feature description from above.

Capture `SPECIFY_FEATURE_DIRECTORY` and `SPEC_FILE` from its Completion Report — every later stage operates on this same feature (the prerequisite scripts each sub-skill runs will resolve it automatically via `.specify/feature.json`, so there's no need to pass the path explicitly, but keep it in your own working notes for the final report).

### 2. Clarify

Invoke `speckit-clarify` with no arguments (let it derive its own questions from the spec just written). Let its interactive Q&A run to completion — up to 5 questions, one at a time, exactly as it's designed to. If it reports "No critical ambiguities detected," that's a valid, successful outcome, not a skipped stage.

### 3. Plan

Invoke `speckit-plan`.

**If this is the first feature ever planned in this project** (no prior `specs/*/plan.md` exists, or `.specify/memory/constitution.md` is still an unfilled template), the Technical Context this stage fills in — language, framework, storage, hosting — becomes the project's de facto foundational stack, since nothing constrains it yet. Flag this explicitly to the user in your stage summary before moving on: *"This plan establishes the project's technical foundation — review it before continuing, since every later feature will build on these choices."* Do not silently block waiting for approval unless the plan itself surfaced a `NEEDS CLARIFICATION` it couldn't resolve on its own — just make sure the user has actually seen it.

### 4. Tasks

Invoke `speckit-tasks`.

### 5. Analyze

Invoke `speckit-analyze` (read-only — it will not modify files).

- If it reports **zero CRITICAL findings**: note any HIGH/MEDIUM/LOW findings in your running summary and proceed to Implement.
- If it reports **one or more CRITICAL findings**: STOP. Present them to the user and ask whether to fix them now (by editing the spec/plan/tasks, or re-running the relevant upstream stage) before continuing. Do not invoke Implement past unresolved CRITICAL findings without an explicit go-ahead.

### 6. Implement

Invoke `speckit-implement`. It runs its own checklist gate (if `checklists/` has unchecked items) and its own task-by-task execution loop — let it run to completion or to its own stop condition.

### 7. Converge (loop)

Invoke `speckit-converge`.

- If it reports **`converged`**: the feature is done. Go to Final Report.
- If it reports **`tasks_appended`**: go back to **Stage 6 (Implement)** to complete the newly appended `## Phase N: Convergence` tasks, then re-run **Stage 7 (Converge)** again.
- Cap this loop at **5 total converge passes** for a single feature. If still not converged after 5 passes, stop, report the remaining findings, and let the user decide whether to keep going manually — don't loop silently forever.

### 8. Final Report (single feature)

Report, concisely:

- Feature directory and the backlog source file (if any)
- Clarify: questions asked/answered
- Plan: whether this run established the project's foundational tech stack
- Analyze: findings by severity, and how any CRITICAL ones were resolved
- Implement + Converge: number of converge passes, final status
- Update `backlog/progress.md` (see Progress Tracking below)

## Progress Tracking

Maintain `backlog/progress.md` (create it on first use if it doesn't exist, with columns `Feature | Status | Spec Dir | Notes`, one row per backlog feature in `build-order.md`'s order, all starting `Not Started`). After every stage completes for a feature, update that feature's row's `Status` to the furthest stage reached (`Specified` → `Clarified` → `Planned` → `Tasked` → `Analyzed` → `Implementing` → `Converged`) and fill in `Spec Dir` once known. This is the only file this skill writes outside of what the sub-skills themselves write — it exists purely so a batch run (or a resumed single-feature run) can pick up where it left off without re-deriving state from scratch.

## Batch Mode (`backlog`)

When the argument resolves to Batch Mode:

1. Read `backlog/build-order.md` for the ordered feature list, and `backlog/progress.md` (create if missing) for what's already done.
2. Find the first feature in build order whose status in `progress.md` is not `Converged`.
3. Run the full single-feature Stage Sequence above against that feature's `backlog/NNN-*.md` file.
4. After that feature reaches `Converged` (or stops early on an unresolved CRITICAL/loop-cap, per the gates above):
   - If the argument included `auto` (i.e. `backlog auto`): automatically continue to the next not-yet-converged feature in build order, without asking.
   - Otherwise: stop and ask the user whether to continue to the next feature, showing which one is next and why build order says it's next (its dependencies are now satisfied). Recommend continuing if the feature just finished converged cleanly with no unresolved findings; recommend pausing for review if it didn't.
5. Never reorder or skip ahead of `build-order.md` — if the next unconverged feature's listed dependencies aren't yet `Converged` in `progress.md`, stop and tell the user rather than proceeding out of order.

Batch mode is a long-running, code-writing operation across many features. Even in `auto` mode, still honor every per-stage gate above (clarify questions, CRITICAL analyze findings, checklist gates) — `auto` only removes the *between-feature* confirmation, never the *within-stage* ones.

## What This Skill Does Not Do

- It does not write spec/plan/tasks content itself — that's each sub-skill's job.
- It does not bypass any gate a sub-skill defines (clarify's questions, analyze's CRITICAL stop, implement's checklist gate).
- It does not commit or push anything — that remains a separate, explicit action outside this pipeline unless a `speckit.git.*` extension hook is configured (this project currently has no `.specify/extensions.yml`, so none fire).
- It does not invoke `speckit-checklist` or `speckit-taskstoissues` automatically — both are optional, standalone tools with their own interactive scope-setting questions that don't fit a linear pipeline. Mention them to the user as available extras (checklist for a requirements-quality gate before planning, taskstoissues for turning `tasks.md` into GitHub issues after tasks are generated), but only run them if asked.

## Done When

- [ ] The requested feature (or, in Batch Mode, at least one feature) has been driven through Specify → Clarify → Plan → Tasks → Analyze → Implement → Converge, or has stopped cleanly at a documented gate with the reason reported
- [ ] `backlog/progress.md` reflects the current true status of every feature touched this run
- [ ] Final Report given to the user with enough detail to know exactly what state the feature/project is in and what to do next
