import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getDaySheet, type SessionDaySheet, type SlotDetail } from './api'
import { loadStaffSession } from '../staff-login/token'
import { ListSkeleton } from '../../components/ListSkeleton'
import { ActionMenu } from '../../components/ActionMenu'
import { useClientPagination } from '../../components/useClientPagination'
import { ShowMoreButton } from '../../components/ShowMoreButton'
import { CancelSessionButton } from '../session-cancellation/CancelSessionButton'
import { CancelFromCutoffForm } from '../partial-session-cancellation/CancelFromCutoffForm'

const SLOTS_PAGE_SIZE = 25

function slotLabel(slot: SlotDetail): string {
  if (slot.tokenNumber !== null) return `Token ${slot.tokenNumber}`
  if (slot.startTime && slot.endTime) return `${slot.startTime.slice(0, 5)}–${slot.endTime.slice(0, 5)}`
  return 'Slot'
}

const STATUS_LABEL: Record<SlotDetail['status'], string> = {
  OPEN: 'Open',
  BOOKED: 'Booked',
  COMPLETED: 'Completed',
  NO_SHOW: 'No-show',
}

const STATUS_BADGE_CLASS: Record<SlotDetail['status'], string> = {
  OPEN: 'bg-gray-100 text-gray-600',
  BOOKED: 'bg-cobalt-100 text-cobalt-700',
  COMPLETED: 'bg-green-100 text-green-700',
  NO_SHOW: 'bg-red-100 text-red-700',
}

function formatSessionDate(isoDate: string): string {
  const parsed = new Date(`${isoDate}T00:00:00`)
  if (Number.isNaN(parsed.getTime())) return isoDate
  return parsed.toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' })
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
function SlotRow({
  index,
  clinicId,
  sessionId,
  doctorProfileId,
  mode,
  slot,
}: {
  index: number
  clinicId: string
  sessionId: string
  doctorProfileId: string
  mode: SessionDaySheet['mode']
  slot: SlotDetail
}) {
  const base = `/staff/clinics/${clinicId}`

  return (
    <tr className="transition-colors duration-150 hover:bg-gray-50">
      <td className="px-4 py-2.5 text-sm tabular-nums text-gray-400">{index + 1}</td>
      <td className="px-4 py-2.5 text-sm font-medium tabular-nums text-gray-900">{slotLabel(slot)}</td>
      <td className="px-4 py-2.5 text-sm text-gray-600">
        {slot.isBuffer ? (
          <span className="text-gray-400">Reserved capacity</span>
        ) : (
          (slot.booking?.patientName ?? <span className="text-gray-300">—</span>)
        )}
      </td>
      <td className="px-4 py-2.5">
        <span
          className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ${STATUS_BADGE_CLASS[slot.status]}`}
        >
          <span aria-hidden="true" className="h-1.5 w-1.5 rounded-full bg-current" />
          {STATUS_LABEL[slot.status]}
        </span>
      </td>
      <td className="px-4 py-2.5 text-right">
        <div className="flex shrink-0 items-center justify-end gap-4">
          {slot.isBuffer && (
            <span className="text-sm text-gray-500">No direct booking — use "Insert a walk-in"</span>
          )}

          {!slot.isBuffer && slot.status === 'OPEN' && mode === 'FIXED_TIME' && (
            <Link
              className={actionLinkClass()}
              to={`${base}/slots/${slot.slotId}/book?doctorProfileId=${doctorProfileId}`}
            >
              Book
            </Link>
          )}

          {slot.booking && (
            <>
              {slot.status === 'BOOKED' && (
                <>
                  <Link
                    className={actionLinkClass()}
                    to={`${base}/sessions/${sessionId}/operations?slotId=${slot.slotId}&bookingId=${slot.booking.bookingId}`}
                  >
                    Mark complete
                  </Link>
                  <Link className={actionLinkClass('danger')} to={`${base}/bookings/${slot.booking.bookingId}/cancel`}>
                    Cancel
                  </Link>
                </>
              )}
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
  const [daySheet, setDaySheet] = useState<SessionDaySheet | null>(null)
  const [error, setError] = useState<string | null>(null)
  const { visibleItems: visibleSlots, hasMore, remaining, showMore } = useClientPagination(
    daySheet?.slots ?? null,
    SLOTS_PAGE_SIZE,
  )

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
            <div className="rounded-xl border border-gray-200 bg-white shadow-sm">
              <div className="flex flex-wrap items-center justify-between gap-2 p-3">
                <div className="flex items-center gap-2">
                  {daySheet.mode === 'QUEUE' && (
                    <Link
                      className={panelButtonClass()}
                      to={`${base}/sessions/${sessionId}/queue-book?doctorProfileId=${daySheet.doctorProfileId}`}
                    >
                      <PlusIcon />
                      Book into queue
                    </Link>
                  )}
                  {daySheet.mode === 'FIXED_TIME' && (
                    <Link
                      className={panelButtonClass()}
                      to={`${base}/sessions/${sessionId}/walk-in?doctorProfileId=${daySheet.doctorProfileId}`}
                    >
                      <PlusIcon />
                      Insert a walk-in
                    </Link>
                  )}
                </div>
                {daySheet.mode === 'FIXED_TIME' && (
                  <CancelSessionButton
                    clinicId={clinicId}
                    sessionId={sessionId}
                    hasActiveBookings={daySheet.slots.some((s) => s.status === 'BOOKED')}
                    onCancelled={refreshAfterCancellation}
                  />
                )}
              </div>
              {daySheet.mode === 'FIXED_TIME' && (
                <div className="border-t border-gray-100 p-3">
                  <CancelFromCutoffForm clinicId={clinicId} sessionId={sessionId} onCancelled={refreshAfterCancellation} />
                </div>
              )}
            </div>

            <div className="rounded-xl border border-gray-200 bg-white shadow-sm">
              <div className="flex items-center justify-between border-b border-gray-100 px-4 py-2.5">
                <h2 className="text-sm font-semibold text-gray-900">Slots</h2>
                {daySheet.slots.length > 0 && (
                  <span className="text-xs font-medium text-gray-500">
                    {daySheet.slots.length} total · {daySheet.slots.filter((s) => s.status === 'BOOKED').length} booked
                  </span>
                )}
              </div>

              {daySheet.slots.length === 0 ? (
                <p className="p-6 text-sm text-gray-500">No slots yet for this session.</p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full min-w-[560px] text-left">
                    <thead>
                      <tr className="border-b border-gray-100 bg-gray-50 text-xs font-semibold uppercase tracking-wide text-gray-400">
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
                          doctorProfileId={daySheet.doctorProfileId}
                          mode={daySheet.mode}
                          slot={slot}
                        />
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>

          {hasMore && <ShowMoreButton remaining={remaining} pageSize={SLOTS_PAGE_SIZE} onClick={showMore} />}
        </>
      )}
    </div>
  )
}
