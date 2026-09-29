# Research: Shared UI Component Library

## Decision 1: `Modal` as a headless shell, not a rigid template

**Decision**: `Modal` provides only the dialog mechanics: a `<dialog>` ref wired to `showModal()` on mount, backdrop-click-to-close (`event.target === dialogRef.current`), `onClose` passthrough, and a `className`/`ariaLabel` prop for the content wrapper. It renders `children` directly — no forced header/body/footer slots. A separate `ModalHeader` sub-component (title + close button, exactly the markup duplicated identically in `DeleteConfirmModal`/`RejectConfirmModal`) is available for callers whose layout fits it.

**Rationale**: Verified by reading all 3 consumers in full: `DeleteConfirmModal`/`RejectConfirmModal` share an almost identical header+body+footer shape, but `EmployeeModal` has a genuinely different layout (a side tab-rail, `sm:flex-row`, no simple single-column body). Forcing a rigid `Modal` API (fixed header/body/footer props) would either not fit `EmployeeModal` at all or require an escape hatch that defeats the abstraction's purpose. A headless shell + an optional, separately-composable `ModalHeader` lets all 3 genuinely share the actual duplicated part (dialog mechanics, ~15 lines each) without forcing a false uniformity onto their different content shapes.

**Alternatives considered**: One rigid `Modal` with `header`/`body`/`footer` props — rejected per the layout mismatch above; a compound-component API (`Modal.Header`, `Modal.Body`) — rejected as more API surface than 3 real consumers (one of which doesn't even use a shared header shape) justify (Constitution Principle II).

## Decision 2: Toast system design

**Decision**: A `ToastProvider` (React Context) wrapping the app, exposing a `useToast()` hook returning `showToast(message: string, variant?: 'success' | 'error')`. Toasts render in a fixed-position container (`fixed bottom-4 right-4`, stacked), auto-dismiss after 4 seconds, each individually dismissible. No portal library — a fixed-position div within the existing React tree is sufficient (no z-index/stacking-context conflict exists today, verified: no other fixed/absolute full-viewport overlay exists outside the native `<dialog>`'s own top-layer rendering, which toasts don't need to compete with).

**Rationale**: Matches FR-002 exactly — transient, non-blocking, distinct from the permanent inline `role="alert"` pattern. Context + hook is idiomatic React with zero new dependency, consistent with this codebase's existing zero-UI-library, zero-state-library (no Redux/Zustand) convention.

**Alternatives considered**: A toast library (react-hot-toast, sonner) — rejected, unnecessary dependency for a simple, well-understood pattern (Principle II); a global event-bus without React context — rejected, context+hook is more idiomatic for this codebase's existing patterns (e.g. `patient-account/token.ts`'s module-level functions are the closest precedent, but toasts need to trigger a re-render, which context naturally provides).

**Provider placement**: `App.tsx`, wrapping all three role shells and the public routes — one provider, one toast stack, regardless of which shell triggers it.

## Decision 3: Button/Input/Select — formalizing existing conventions, not inventing new ones

**Decision**: `Button` takes a `variant: 'primary' | 'secondary' | 'destructive'` prop, rendering exactly `DESIGN.md`'s documented classes for each (`bg-indigo-600`/`bg-gray-100`/`bg-red-600` + the shared `rounded-lg font-semibold transition-all duration-150 ease-out active:scale-[0.98] focus-visible:ring-2` treatment already used identically across every button in the codebase). `Input`/`Select` are thin wrappers around the existing `.input` CSS class (`frontend/src/index.css`) plus an optional `label` prop rendering the existing `block text-sm font-medium text-gray-700` label pattern.

**Rationale**: `DESIGN.md`'s own "Components" section already documents these exact class combinations as the established convention — this feature codifies what's already consistent, per FR-003's own framing, not designs anything new.

**Alternatives considered**: A full field-level validation system on `Input`/`Select` (error message slot, validation state) — explicitly out of scope, deferred to 051 (forms) per this wave's own sequencing; kept here to the minimal label+input pairing.

## Decision 4: `Card`/`Badge`/`EmptyState`/`LoadingState` — sourced from real duplication

**Decision**: `Card` renders the exact copy-pasted tile treatment (`rounded-xl border border-gray-200 bg-white p-4 shadow-sm transition-all duration-150 ease-out hover:shadow-md active:scale-[0.98]`) confirmed duplicated across `AdminDashboard.tsx`/`PatientDashboard.tsx`/`ClinicToolsDashboard.tsx`/`PendingClinicsList.tsx`/`ScheduleForm.tsx`/`BookSlotForm.tsx` — as a plain content card (no link/click behavior baked in; callers wrap it in a `<Link>`/`<button>` as needed, matching how it's used differently across those 6 files). `Badge` is a small pill (`rounded-full px-2 py-0.5 text-xs font-medium`) parameterized by color, distinct from `RoleBadge` (which encodes role-specific logic and stays as its own component, now presumably built on `Badge` if convenient but not required by this feature). `EmptyState` takes `message` + optional `action` (button/link), replacing the 6+ ad hoc empty-state wrapper duplications. `LoadingState` wraps the existing `ListSkeleton` for list contexts and provides a simple centered spinner/text variant for the ad hoc "Loading…" text scattered across `AppointmentTypeSelect.tsx`/`ConsultationNoteForm.tsx`/`ExternalRecordReferenceForm.tsx`/`DoctorSelect.tsx`.

**Rationale**: Every value here traces to a real, already-identified duplication from this project's own prior audit (this session's earlier frontend inventory) — not speculative design.

**Alternatives considered**: A single generic `Panel` component for both Card and EmptyState's wrapper — rejected, their content/purpose differ enough (a Card holds arbitrary content; an EmptyState always pairs a message with an optional action) that separate, purpose-named components read better at call sites.

## Decision 5: Migration targets for FR-004/SC-003

**Decision**: `AdminDashboard.tsx` and `PatientDashboard.tsx`'s tile grids, migrated onto `Card`+`Button` — 2 real screens, confirmed to currently duplicate the exact copy-pasted tile markup this feature's `Card` codifies. `ClinicToolsDashboard.tsx` (the 3rd file sharing this pattern) is deliberately left for 048 (staff dashboard), which will substantially redesign it anyway — migrating it here first would mean redoing the work twice.
