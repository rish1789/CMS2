import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useOutletContext, useParams } from 'react-router-dom'
import { getDaySheet, type CancelledRange, type SessionDaySheet, type SlotDetail } from './api'
import { loadStaffSession } from '../staff-login/token'
import { ListSkeleton } from '../../components/ListSkeleton'
import { ActionMenu } from '../../components/ActionMenu'
import { useClientPagination } from '../../components/useClientPagination'
import { ShowMoreButton } from '../../components/ShowMoreButton'
import { CancelSessionButton } from '../session-cancellation/CancelSessionButton'
import { DeleteSessionButton } from '../session-cancellation/DeleteSessionButton'
import { CancelFromCutoffForm } from '../partial-session-cancellation/CancelFromCutoffForm'
import { BatchCancelBar } from '../booking-cancellation/BatchCancelBar'
import { visitReasonLabel } from '../front-desk-walk-in/visitReasons'
import type { ClinicShellOutletContext } from '../../routes/staff/ClinicShell'

const SLOTS_PAGE_SIZE = 25

function slotLabel(slot: SlotDetail): string {
  if (slot.tokenNumber !== null) return `Token ${slot.tokenNumber}`
  if (slot.startTime && slot.endTime) return `${slot.startTime.slice(0, 5)}–${slot.endTime.slice(0, 5)}`
  return 'Slot'
}

const STATUS_LABEL: Record<SlotDetail['status'], string> = {
  OPEN: 'Open',
  BOOKED: 'Booked',
  APPEARED: 'Appeared',
  COMPLETED: 'Completed',
  NO_SHOW: 'No-show',
}

const STATUS_BADGE_CLASS: Record<SlotDetail['status'], string> = {
  OPEN: 'bg-gray-100 text-gray-600',
  BOOKED: 'bg-cobalt-100 text-cobalt-700',
  APPEARED: 'bg-amber-100 text-amber-700',
  COMPLETED: 'bg-green-100 text-green-700',
  NO_SHOW: 'bg-red-100 text-red-700',
}

// 065-phase1-stabilization: an open slot the session's cancellation takes out of service. Not a
// SlotStatus - slot status is unchanged; this is derived from the session's cancellation record.
const CANCELLED_BADGE_CLASS = 'bg-white text-gray-600 ring-1 ring-inset ring-gray-300'

function formatSessionDate(isoDate: string): string {
  const parsed = new Date(`${isoDate}T00:00:00`)
  if (Number.isNaN(parsed.getTime())) return isoDate
  return parsed.toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' })
}

// real-bug-fix 2026-09-17: mirrors SlotCompletionService's own "not before scheduledStart" guard
// (LocalDateTime.of(session.sessionDate, slot.startTime)) client-side, so "Mark complete" doesn't
// sit there as a live-looking action that only ever bounces back with SLOT_NOT_YET_STARTED - the
// backend still enforces this regardless, this is purely about not inviting a doomed click.
function hasSlotStarted(sessionDate: string, startTime: string | null): boolean {
  if (!startTime) return true
  const scheduledStart = new Date(`${sessionDate}T${startTime}`)
  if (Number.isNaN(scheduledStart.getTime())) return true
  return Date.now() >= scheduledStart.getTime()
}

// 065-phase1-stabilization: mirrors the server's bookability rule for a timed slot - a range is
// [fromTime, toTime), a null toTime running to the end of the session. Times are compared as
// "HH:MM:SS" strings (normalised, since a zero-seconds time may arrive as "HH:MM").
function normaliseTime(time: string): string {
  return time.length === 5 ? `${time}:00` : time.slice(0, 8)
}

function isInCancelledRange(startTime: string | null, ranges: CancelledRange[]): boolean {
  if (!startTime) return false
  const start = normaliseTime(startTime)
  return ranges.some(
    (range) =>
      normaliseTime(range.fromTime) <= start && (range.toTime === null || start < normaliseTime(range.toTime)),
  )
}

// "Cancelled 11:00–12:00 and from 15:00" - each range as staff entered it.
function describeCancelledRanges(ranges: CancelledRange[]): string {
  const parts = ranges.map((range) =>
    range.toTime === null
      ? `from ${range.fromTime.slice(0, 5)}`
      : `${range.fromTime.slice(0, 5)}–${range.toTime.slice(0, 5)}`,
  )
  const list = parts.length === 1 ? parts[0] : `${parts.slice(0, -1).join(', ')} and ${parts[parts.length - 1]}`
  return `Cancelled ${list}.`
}

function actionLinkClass(variant: 'default' | 'danger' = 'default') {
  const color = variant === 'danger' ? 'text-red-600 hover:text-red-700' : 'text-indigo-600 hover:text-indigo-700'
  return `text-sm font-medium transition-colors duration-150 hover:underline ${color}`
}

// Session-level actions (as opposed to per-slot row actions above) are the primary calls to
// action on this page - plain text links understate that, so they get real button treatment.
function panelButtonClass() {
  return 'inline-flex h-9 items-center gap-1.5 rounded-lg bg-indigo-600 px-3.5 text-sm font-semibold text-white shadow-sm transition-colors duration-150 hover:bg-indigo-500'
}

function menuItemClass(variant: 'default' | 'danger' = 'default') {
  const color = variant === 'danger' ? 'text-red-600 hover:bg-red-50' : 'text-gray-700 hover:bg-gray-50'
  return `block rounded-md px-2.5 py-1.5 text-sm font-medium transition-colors duration-150 ${color}`
}

function PlusIcon() {
  return (
    <svg aria-hidden="true" width="14" height="14" viewBox="0 0 14 14" fill="none">
      <path d="M7 1v12M1 7h12" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
    </svg>
  )
}

// staff-console-redesign-2026-09-10: a real Sr/Time/Patient/Status table, not a flat row list -
// borrows the layout from a provided design reference (mockup's session-console.html) rebuilt
// in this app's own indigo/gray-* design tokens and existing table convention (Roster/Day
// Sheet/Doctors all already share this exact thead/tbody treatment), rather than a new one.
// 057-day-sheet-status-overhaul FR-011: only a BOOKED or APPEARED slot with a real booking can
// be selected for the batch-cancel checkbox - matches BookingCancellationService's own widened
// eligibility (research.md Decision 4) exactly, so a checkbox never offers something the batch
// endpoint would reject.
function isSelectableForCancel(slot: SlotDetail): boolean {
  return slot.booking !== null && (slot.status === 'BOOKED' || slot.status === 'APPEARED')
}

function SlotRow({
  index,
  clinicId,
  sessionId,
  sessionDate,
  doctorProfileId,
  mode,
  slot,
  cancelled,
  isDoctor,
  selected,
  onToggleSelect,
}: {
  index: number
  clinicId: string
  sessionId: string
  sessionDate: string
  doctorProfileId: string
  mode: SessionDaySheet['mode']
  slot: SlotDetail
  // 065-phase1-stabilization: the whole session, or a range covering this slot, is cancelled - an
  // open slot here is not bookable, so it reads "Cancelled" and offers no "Book".
  cancelled: boolean
  isDoctor: boolean
  selected: boolean
  onToggleSelect: (bookingId: string) => void
}) {
  const base = `/staff/clinics/${clinicId}`
  const cancelledOpenSlot = cancelled && slot.status === 'OPEN'

  return (
    <tr className="transition-colors duration-150 hover:bg-gray-50">
      {/* 057-day-sheet-status-overhaul FR-009/FR-015: replaces the old per-row inline "Cancel"
          link - never shown to a Doctor caller, and only for a slot the batch endpoint would
          actually accept. */}
      <td className="px-4 py-2.5">
        {!isDoctor && slot.booking && isSelectableForCancel(slot) && (
          <input
            type="checkbox"
            aria-label={`Select ${slotLabel(slot)} for cancellation`}
            checked={selected}
            onChange={() => onToggleSelect(slot.booking!.bookingId)}
            className="h-4 w-4 rounded border-gray-300 text-red-600 focus:ring-red-500"
          />
        )}
      </td>
      <td className="px-4 py-2.5 text-sm tabular-nums text-gray-400">{index + 1}</td>
      <td className="px-4 py-2.5 text-sm font-medium tabular-nums text-gray-900">{slotLabel(slot)}</td>
      <td className="px-4 py-2.5 text-sm text-gray-600">
        {slot.booking ? (
          <span className="inline-flex items-center gap-1.5">
            {slot.booking.patientName}
            {slot.booking.isWalkIn && (
              <span className="rounded-full bg-gray-100 px-1.5 py-0.5 text-xs font-medium text-gray-500">
                Walk-in
              </span>
            )}
            {slot.booking.isWalkIn && slot.booking.visitReason && (
              <span className="text-xs text-gray-500">
                {visitReasonLabel(slot.booking.visitReason, slot.booking.visitReasonDetail)}
              </span>
            )}
          </span>
        ) : (
          <span className="text-gray-300">—</span>
        )}
      </td>
      <td className="px-4 py-2.5">
        <span
          className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ${
            cancelledOpenSlot ? CANCELLED_BADGE_CLASS : STATUS_BADGE_CLASS[slot.status]
          }`}
        >
          <span aria-hidden="true" className="h-1.5 w-1.5 rounded-full bg-current" />
          {cancelledOpenSlot ? 'Cancelled' : STATUS_LABEL[slot.status]}
        </span>
      </td>
      <td className="px-4 py-2.5 text-right">
        <div className="flex shrink-0 items-center justify-end gap-4">
          {slot.status === 'OPEN' && mode === 'FIXED_TIME' && !cancelled && (
            <Link
              className={actionLinkClass()}
              to={`${base}/slots/${slot.slotId}/book?doctorProfileId=${doctorProfileId}`}
            >
              Book
            </Link>
          )}

          {slot.booking && (
            <>
              {/* 057-day-sheet-status-overhaul: BOOKED/NO_SHOW both use the same "Mark
                  appeared" action (NO_SHOW's is the mistaken-auto-No-Show correction path,
                  FR-003a) - Appeared itself is never time-gated (a patient can arrive before
                  the slot's own scheduled start), unlike Completed below. FR-007: never shown
                  or reachable in a doctor's view - Appeared/No-Show stay staff-only. */}
              {!isDoctor && (slot.status === 'BOOKED' || slot.status === 'NO_SHOW') && (
                <Link
                  className={actionLinkClass()}
                  to={`${base}/sessions/${sessionId}/operations?slotId=${slot.slotId}&bookingId=${slot.booking.bookingId}`}
                >
                  Appeared
                </Link>
              )}
              {/* FR-008: ClinicAdmin/Operations' pre-057 direct BOOKED->COMPLETED path stays
                  reachable, additively alongside the new Appeared action above - staff
                  aren't forced through Appeared first. A doctor only ever reaches this from
                  APPEARED (FR-006), never from BOOKED (matches SlotCompletionService's own
                  eligibleStatus check). Unlike APPEARED below, a not-yet-started BOOKED row
                  shows no "Starts at" placeholder - Appeared is already its one clean
                  primary action, so a same-status completion placeholder would just be clutter. */}
              {!isDoctor && slot.status === 'BOOKED' && hasSlotStarted(sessionDate, slot.startTime) && (
                <Link
                  className={actionLinkClass()}
                  to={`${base}/sessions/${sessionId}/operations?slotId=${slot.slotId}&bookingId=${slot.booking.bookingId}`}
                >
                  Complete
                </Link>
              )}
              {slot.status === 'APPEARED' &&
                (hasSlotStarted(sessionDate, slot.startTime) ? (
                  <Link
                    className={actionLinkClass()}
                    to={`${base}/sessions/${sessionId}/operations?slotId=${slot.slotId}&bookingId=${slot.booking.bookingId}`}
                  >
                    Complete
                  </Link>
                ) : (
                  <span className="text-sm text-gray-400">Starts at {slotLabel(slot).split('–')[0]}</span>
                ))}
              <ActionMenu label="More" name={`slot-actions-${sessionId}`}>
                {slot.status === 'BOOKED' && (
                  <Link
                    className={menuItemClass()}
                    to={`${base}/bookings/${slot.booking.bookingId}/queue-position`}
                  >
                    Queue position
                  </Link>
                )}
                <Link className={menuItemClass()} to={`${base}/bookings/${slot.booking.bookingId}/consultation-note`}>
                  Consultation note
                </Link>
                <Link className={menuItemClass()} to={`${base}/bookings/${slot.booking.bookingId}/prescription`}>
                  Prescription
                </Link>
                <Link className={menuItemClass()} to={`${base}/bookings/${slot.booking.bookingId}/external-record`}>
                  External record
                </Link>
              </ActionMenu>
            </>
          )}
        </div>
      </td>
    </tr>
  )
}

export function SessionSlotsView() {
  const { clinicId, sessionId } = useParams<{ clinicId: string; sessionId: string }>()
  // 057-day-sheet-status-overhaul FR-007: the "Appeared" action is hidden entirely for a
  // Doctor caller - the read-only status badge (Appeared/No-show text) stays visible to every
  // role, since withholding real patient-attendance state from the treating doctor would be a
  // worse outcome than this FR's wording strictly requires; only the actionable control is
  // role-gated.
  const { role } = useOutletContext<ClinicShellOutletContext>()
  const isDoctor = role === 'Doctor'
  const navigate = useNavigate()
  const [daySheet, setDaySheet] = useState<SessionDaySheet | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [selectedBookingIds, setSelectedBookingIds] = useState<string[]>([])
  // 063-front-desk-walk-in (FR-019): a Fixed-Time session's walk-ins have no scheduled time - they
  // wait in the session's walk-in line, shown on its own below the timed appointments. A Queue
  // session's walk-ins are ordinary tokens and stay in the one list.
  // Memoized: useClientPagination resets whenever its list's identity changes, so a fresh filter
  // on every render would loop.
  const timedSlots = useMemo(
    () =>
      daySheet === null
        ? null
        : daySheet.mode === 'FIXED_TIME'
          ? daySheet.slots.filter((s) => s.startTime !== null)
          : daySheet.slots,
    [daySheet],
  )
  const walkInLine = useMemo(
    () =>
      daySheet?.mode === 'FIXED_TIME'
        ? daySheet.slots
            .filter((s) => s.startTime === null && s.booking?.isWalkIn)
            .sort((a, b) => (a.tokenNumber ?? 0) - (b.tokenNumber ?? 0))
        : [],
    [daySheet],
  )
  const { visibleItems: visibleSlots, hasMore, remaining, showMore } = useClientPagination(timedSlots, SLOTS_PAGE_SIZE)

  function toggleSelect(bookingId: string) {
    setSelectedBookingIds((prev) =>
      prev.includes(bookingId) ? prev.filter((id) => id !== bookingId) : [...prev, bookingId],
    )
  }

  const eligibleBookingIds = (timedSlots ?? [])
    .filter((s) => isSelectableForCancel(s))
    .map((s) => s.booking!.bookingId)
  const allEligibleSelected =
    eligibleBookingIds.length > 0 && eligibleBookingIds.every((id) => selectedBookingIds.includes(id))

  function toggleSelectAll() {
    setSelectedBookingIds(allEligibleSelected ? [] : eligibleBookingIds)
  }

  function refreshAfterBatchCancel() {
    setSelectedBookingIds([])
    refreshAfterCancellation()
  }

  useEffect(() => {
    if (!clinicId || !sessionId) return
    const session = loadStaffSession()
    if (!session) return
    getDaySheet(clinicId, sessionId, session.token)
      .then(setDaySheet)
      .catch(() => setError('Failed to load this session.'))
  }, [clinicId, sessionId])

  // Either cancellation flow below changes Slot statuses server-side - re-fetch rather than
  // hand-patch local state, so the list reflects exactly what the server now has.
  function refreshAfterCancellation() {
    if (!clinicId || !sessionId) return
    const session = loadStaffSession()
    if (!session) return
    getDaySheet(clinicId, sessionId, session.token).then(setDaySheet).catch(() => setError('Failed to load this session.'))
  }

  if (!clinicId || !sessionId) return null
  const base = `/staff/clinics/${clinicId}`

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      {daySheet && (
        <div>
          {/* staff-console-redesign-2026-09-10: continues ClinicShell's own "Clinic dashboard /
              {clinic name}" breadcrumb bar (rendered once, above the Outlet, on every clinic
              page) with the two segments specific to this page - same styling, so the two read
              as one unbroken trail rather than a duplicate second breadcrumb. */}
          <nav aria-label="Breadcrumb" className="mb-1 flex items-center gap-2 text-sm text-gray-500">
            <Link
              to={`${base}/doctors`}
              className="font-medium text-gray-500 transition-colors duration-150 hover:text-indigo-600"
            >
              Doctors
            </Link>
            <span aria-hidden="true">/</span>
            <span className="font-semibold text-gray-900">{daySheet.doctorName}</span>
          </nav>
          <h1 className="text-lg font-semibold text-gray-900">{daySheet.doctorName}</h1>
          <p className="mt-0.5 text-sm text-gray-500">{formatSessionDate(daySheet.sessionDate)}</p>
        </div>
      )}

      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {error}
        </p>
      )}

      {daySheet === null && !error && <ListSkeleton rows={4} />}

      {daySheet && (
        <>
          {/* staff-console-redesign-2026-09-10: back to two separate cards (toolbar, then slots),
              superseding the earlier "one merged panel" decision made in this same file - that
              merge was a direct fix for a real complaint about a bulkier, differently-structured
              toolbar (an oversized "Danger Zone" box); this toolbar is its own tight two-row
              card, so the earlier bulkiness concern doesn't reapply the same way. Routine actions
              (never wraps) and destructive actions (the cutoff-range form + whole-session cancel)
              still get their own row apiece - a single shared row wrapped unpredictably at
              laptop-width viewports, per the fix already proven here. */}
          <div className="space-y-4">
            {/* 065-phase1-stabilization (owner decision 3): say why the session or part of it takes
                no bookings, rather than letting staff find out from a refused booking. */}
            {daySheet.cancelled ? (
              <p role="status" className="rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm text-gray-700">
                This session is cancelled. It takes no new bookings or walk-ins.
              </p>
            ) : (
              daySheet.cancelledRanges.length > 0 && (
                <p role="status" className="rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm text-gray-700">
                  {`${describeCancelledRanges(daySheet.cancelledRanges)} Those times take no new bookings.`}
                </p>
              )
            )}
            <div className="rounded-xl border border-gray-200 bg-white shadow-sm">
              <div className="flex flex-wrap items-center justify-between gap-2 p-3">
                <div className="flex items-center gap-2">
                  {daySheet.mode === 'QUEUE' && !daySheet.cancelled && (
                    <Link
                      className={panelButtonClass()}
                      to={`${base}/sessions/${sessionId}/queue-book?doctorProfileId=${daySheet.doctorProfileId}`}
                    >
                      <PlusIcon />
                      Book into queue
                    </Link>
                  )}
                  {!isDoctor && !daySheet.cancelled && (
                    <Link className={panelButtonClass()} to={`${base}/walk-in?sessionId=${sessionId}`}>
                      <PlusIcon />
                      Register walk-in
                    </Link>
                  )}
                </div>
                <div className="flex items-center gap-2">
                  {/* Stays mounted once the session is cancelled (it then renders only its own
                      just-finished result, or nothing), so a confirmed cancellation's outcome
                      survives the day-sheet refresh that follows it. */}
                  {daySheet.mode === 'FIXED_TIME' && (
                    <CancelSessionButton
                      clinicId={clinicId}
                      sessionId={sessionId}
                      alreadyCancelled={daySheet.cancelled}
                      onCancelled={refreshAfterCancellation}
                    />
                  )}
                  {/* A cancelled session is kept on record and can't be deleted (the server refuses). */}
                  {!daySheet.cancelled && (
                    <DeleteSessionButton
                      clinicId={clinicId}
                      sessionId={sessionId}
                      onDeleted={() => navigate(`${base}/day-sheet`)}
                    />
                  )}
                </div>
              </div>
              {daySheet.mode === 'FIXED_TIME' && !daySheet.cancelled && (
                <div className="border-t border-gray-100 p-3">
                  <CancelFromCutoffForm clinicId={clinicId} sessionId={sessionId} onCancelled={refreshAfterCancellation} />
                </div>
              )}
            </div>

            <div className="rounded-xl border border-gray-200 bg-white shadow-sm">
              <div className="flex items-center justify-between border-b border-gray-100 px-4 py-2.5">
                <h2 className="text-sm font-semibold text-gray-900">Slots</h2>
                {(timedSlots ?? []).length > 0 && (
                  <span className="text-xs font-medium text-gray-500">
                    {(timedSlots ?? []).length} total ·{' '}
                    {(timedSlots ?? []).filter((s) => s.status !== 'OPEN').length} booked
                  </span>
                )}
              </div>

              {(timedSlots ?? []).length === 0 ? (
                <p className="p-6 text-sm text-gray-500">No slots yet for this session.</p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full min-w-[560px] text-left">
                    <thead>
                      <tr className="border-b border-gray-100 bg-gray-50 text-xs font-semibold uppercase tracking-wide text-gray-400">
                        <th scope="col" className="px-4 py-2 font-semibold">
                          {!isDoctor && eligibleBookingIds.length > 0 && (
                            <input
                              type="checkbox"
                              aria-label="Select all eligible slots"
                              checked={allEligibleSelected}
                              onChange={toggleSelectAll}
                              className="h-4 w-4 rounded border-gray-300 text-red-600 focus:ring-red-500"
                            />
                          )}
                        </th>
                        <th scope="col" className="px-4 py-2 font-semibold">
                          Sr
                        </th>
                        <th scope="col" className="px-4 py-2 font-semibold">
                          Time
                        </th>
                        <th scope="col" className="px-4 py-2 font-semibold">
                          Patient
                        </th>
                        <th scope="col" className="px-4 py-2 font-semibold">
                          Status
                        </th>
                        <th scope="col" className="px-4 py-2 font-semibold">
                          <span className="sr-only">Actions</span>
                        </th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {(visibleSlots ?? []).map((slot, index) => (
                        <SlotRow
                          key={slot.slotId}
                          index={index}
                          clinicId={clinicId}
                          sessionId={sessionId}
                          sessionDate={daySheet.sessionDate}
                          doctorProfileId={daySheet.doctorProfileId}
                          mode={daySheet.mode}
                          slot={slot}
                          cancelled={daySheet.cancelled || isInCancelledRange(slot.startTime, daySheet.cancelledRanges)}
                          isDoctor={isDoctor}
                          selected={!!slot.booking && selectedBookingIds.includes(slot.booking.bookingId)}
                          onToggleSelect={toggleSelect}
                        />
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              {!isDoctor && clinicId && sessionId && (
                <div className="border-t border-gray-100 p-3">
                  <BatchCancelBar
                    clinicId={clinicId}
                    sessionId={sessionId}
                    selectedBookingIds={selectedBookingIds}
                    onCancelled={refreshAfterBatchCancel}
                    onClear={() => setSelectedBookingIds([])}
                  />
                </div>
              )}
            </div>
          </div>

          {hasMore && <ShowMoreButton remaining={remaining} pageSize={SLOTS_PAGE_SIZE} onClick={showMore} />}

          {daySheet.mode === 'FIXED_TIME' && (
            <section aria-label="Walk-in line" className="rounded-xl border border-gray-200 bg-white shadow-sm">
              <div className="flex items-center justify-between border-b border-gray-100 px-4 py-2.5">
                <h2 className="text-sm font-semibold text-gray-900">Walk-in line</h2>
                {!isDoctor && !daySheet.cancelled && (
                  <Link className="text-xs font-medium text-indigo-700 hover:text-indigo-600" to={`${base}/walk-in?sessionId=${sessionId}`}>
                    Open front desk
                  </Link>
                )}
              </div>
              {walkInLine.length === 0 ? (
                <p className="p-4 text-sm text-gray-500">No walk-ins for this session.</p>
              ) : (
                <ul className="divide-y divide-gray-100">
                  {walkInLine.map((slot) => (
                    <li key={slot.slotId} className="flex flex-wrap items-center gap-x-3 gap-y-1 px-4 py-2.5 text-sm">
                      <span className="font-semibold tabular-nums text-gray-900">{`W${slot.tokenNumber}`}</span>
                      <span className="text-gray-900">{slot.booking!.patientName}</span>
                      <span className="rounded-full bg-gray-100 px-1.5 py-0.5 text-xs font-medium text-gray-500">Walk-in</span>
                      {slot.booking!.visitReason && (
                        <span className="text-gray-600">
                          {visitReasonLabel(slot.booking!.visitReason, slot.booking!.visitReasonDetail)}
                        </span>
                      )}
                      <span
                        className={`ml-auto inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ${STATUS_BADGE_CLASS[slot.status]}`}
                      >
                        <span aria-hidden="true" className="h-1.5 w-1.5 rounded-full bg-current" />
                        {slot.status === 'BOOKED' ? 'Waiting' : STATUS_LABEL[slot.status]}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          )}
        </>
      )}
    </div>
  )
}
