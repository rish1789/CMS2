import { useCallback, useEffect, useState } from 'react'
import { getDaySheet, type SlotDetail } from '../day-sheet/api'
import { completeSlot, markSlotAppeared } from '../session-delay/api'
import { cancelBookingAsStaff } from '../booking-cancellation/api'
import { visitReasonLabel } from './visitReasons'

// 061's live-status cadence, reused so the line stays current without a manual refresh.
const POLL_INTERVAL_MS = 20000

export interface WalkInLinePanelProps {
  clinicId: string
  token: string
  sessionId: string
  mode: 'FIXED_TIME' | 'QUEUE'
  doctorName: string
  refreshKey: number
  // Tells the page the line changed, so the session list's counts and hint refresh too.
  onChanged: () => void
}

function isWalkIn(slot: SlotDetail): boolean {
  return slot.startTime === null && slot.booking !== null && slot.booking.isWalkIn
}

// 064-queue-send-in-complete (research.md Decision 4): in a Queue session every untimed booked token
// is in the line, booked ahead or walked in; in a Fixed-Time session only the walk-ins are.
function isInLine(slot: SlotDetail, mode: WalkInLinePanelProps['mode']): boolean {
  return mode === 'QUEUE' ? slot.startTime === null && slot.booking !== null : isWalkIn(slot)
}

function placeLabel(slot: SlotDetail, mode: WalkInLinePanelProps['mode']): string {
  return mode === 'QUEUE' ? `Token ${slot.tokenNumber}` : `W${slot.tokenNumber}`
}

// 063-front-desk-walk-in US2 (FR-012-FR-015): one Fixed-Time session's walk-in line. Everything here
// reuses existing sources: the Day Sheet read, and the existing Appeared ("Send in"), Complete and
// staff-cancel ("Remove") actions. The Doctor free/busy hint is the same APPEARED status the Day
// Sheet shows - no new doctor-status logic. 064-queue-send-in-complete: a Queue session's waiting
// line works the same way, over all of its tokens.
export function WalkInLinePanel({ clinicId, token, sessionId, mode, doctorName, refreshKey, onChanged }: WalkInLinePanelProps) {
  const lineName = mode === 'QUEUE' ? 'waiting line' : 'walk-in line'
  const [slots, setSlots] = useState<SlotDetail[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busySlotId, setBusySlotId] = useState<string | null>(null)
  const [confirmRemoveId, setConfirmRemoveId] = useState<string | null>(null)
  const [localRefresh, setLocalRefresh] = useState(0)

  const load = useCallback(() => {
    return getDaySheet(clinicId, sessionId, token)
      .then((daySheet) => {
        setSlots(daySheet.slots)
        setError(null)
      })
      .catch(() => setError(`Couldn't load the ${lineName}. It will retry automatically.`))
  }, [clinicId, sessionId, token, lineName])

  useEffect(() => {
    void load()
    const intervalId = setInterval(() => void load(), POLL_INTERVAL_MS)
    return () => clearInterval(intervalId)
  }, [load, refreshKey, localRefresh])

  async function act(slotId: string, action: () => Promise<unknown>, failure: string) {
    setBusySlotId(slotId)
    setError(null)
    try {
      await action()
      setConfirmRemoveId(null)
      setLocalRefresh((k) => k + 1)
      onChanged()
    } catch (err) {
      setError(err instanceof Error && err.message ? err.message : failure)
    } finally {
      setBusySlotId(null)
    }
  }

  const waiting = (slots ?? [])
    .filter((s) => isInLine(s, mode) && s.status === 'BOOKED')
    .sort((a, b) => (a.tokenNumber ?? 0) - (b.tokenNumber ?? 0))
  const inWithDoctor = (slots ?? []).find((s) => s.status === 'APPEARED') ?? null
  const lineWithDoctor = inWithDoctor && isInLine(inWithDoctor, mode) ? inWithDoctor : null

  return (
    <section aria-label={`${doctorName}’s ${lineName}`} className="space-y-4 rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-sm font-semibold text-gray-900">
          {doctorName}’s {lineName}
        </h2>
        {slots && (
          <span
            className={`rounded-full px-2 py-0.5 text-xs font-medium ${
              inWithDoctor ? 'bg-amber-50 text-amber-800' : 'bg-green-50 text-green-800'
            }`}
          >
            {inWithDoctor ? 'Doctor busy' : 'Doctor free now'}
          </span>
        )}
      </div>

      {error && (
        <p role="alert" className="text-sm text-red-700">
          {error}
        </p>
      )}

      {slots === null && !error && <div aria-hidden="true" className="h-24 animate-pulse rounded-lg bg-gray-100" />}

      {slots && inWithDoctor && (
        <div className="rounded-lg border border-gray-200 bg-gray-50 p-3 text-sm">
          <p className="text-xs text-gray-500">In with the doctor</p>
          <div className="mt-1 flex items-center justify-between gap-2">
            <span className="font-medium text-gray-900">
              {lineWithDoctor ? `${placeLabel(lineWithDoctor, mode)} · ` : ''}
              {inWithDoctor.booking?.patientName ?? 'Patient'}
            </span>
            {lineWithDoctor && lineWithDoctor.booking && (
              <button
                type="button"
                disabled={busySlotId !== null}
                aria-label={`Complete ${lineWithDoctor.booking.patientName}`}
                onClick={() =>
                  void act(lineWithDoctor.slotId, () => completeSlot(clinicId, lineWithDoctor.slotId, token), "Couldn't complete the visit.")
                }
                className="rounded-md border border-gray-300 bg-white px-2.5 py-1 text-xs font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
              >
                Complete
              </button>
            )}
          </div>
        </div>
      )}

      {slots && waiting.length === 0 && (
        <p className="text-sm text-gray-600">{mode === 'QUEUE' ? 'No one waiting.' : 'No walk-ins waiting.'}</p>
      )}

      {waiting.length > 0 && (
        <ol className="space-y-2">
          {waiting.map((slot, index) => {
            const booking = slot.booking!
            const reason = visitReasonLabel(booking.visitReason, booking.visitReasonDetail)
            return (
              <li key={slot.slotId} className="rounded-lg border border-gray-200 p-3 text-sm">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="shrink-0 font-semibold tabular-nums text-gray-900">{placeLabel(slot, mode)}</span>
                      <span className="truncate text-gray-900">{booking.patientName}</span>
                      {mode === 'QUEUE' && booking.isWalkIn && (
                        <span className="shrink-0 rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-700">Walk-in</span>
                      )}
                      {index === 0 && (
                        <span className="shrink-0 rounded-full bg-indigo-50 px-2 py-0.5 text-xs font-medium text-indigo-700">Next</span>
                      )}
                    </div>
                    {reason && <p className="mt-0.5 text-gray-600">{reason}</p>}
                  </div>
                  <button
                    type="button"
                    disabled={busySlotId !== null}
                    aria-label={`Send in ${booking.patientName}`}
                    onClick={() =>
                      void act(slot.slotId, () => markSlotAppeared(clinicId, slot.slotId, token), "Couldn't send the patient in.")
                    }
                    className="shrink-0 rounded-md bg-indigo-600 px-2.5 py-1 text-xs font-semibold text-white hover:bg-indigo-500 disabled:opacity-50"
                  >
                    Send in
                  </button>
                </div>
                {confirmRemoveId === slot.slotId ? (
                  <div className="mt-2 flex items-center gap-2">
                    <span className="text-gray-700">Remove from the line?</span>
                    <button
                      type="button"
                      disabled={busySlotId !== null}
                      onClick={() =>
                        void act(slot.slotId, () => cancelBookingAsStaff(clinicId, booking.bookingId, token), "Couldn't remove the patient from the line.")
                      }
                      className="rounded-md bg-red-600 px-2 py-0.5 text-xs font-semibold text-white hover:bg-red-500 disabled:opacity-50"
                    >
                      Yes, remove
                    </button>
                    <button
                      type="button"
                      onClick={() => setConfirmRemoveId(null)}
                      className="text-xs font-medium text-gray-700 hover:text-gray-900"
                    >
                      Keep
                    </button>
                  </div>
                ) : (
                  <button
                    type="button"
                    aria-label={`Remove ${booking.patientName}`}
                    onClick={() => setConfirmRemoveId(slot.slotId)}
                    className="mt-2 text-xs font-medium text-gray-600 hover:text-red-700"
                  >
                    Remove
                  </button>
                )}
              </li>
            )
          })}
        </ol>
      )}
    </section>
  )
}
