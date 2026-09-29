import { useEffect, useRef, useState } from 'react'
import { listSessions, type SessionSummary } from '../day-sheet/api'
import { listDoctorBookingReadiness, type DoctorBookingReadiness } from '../doctor-picker/api'
import { getSessionLiveStatusAsStaff, type LiveScheduleStatus } from '../session-delay/api'
import { STATUS_LABELS } from '../session-delay/liveStatusLabels'

// 061's live-status cadence, reused for the whole front-desk board (FR-006: no new status logic).
const POLL_INTERVAL_MS = 20000

function todayIso(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

function formatTime(time: string): string {
  const [h, m] = time.split(':').map(Number)
  const d = new Date()
  d.setHours(h, m, 0, 0)
  return d.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })
}

// Spec edge case "Session over": a session whose scheduled end has passed drops off the list,
// unless someone is still with the doctor or waiting in its walk-in line.
function isOver(session: SessionSummary): boolean {
  // 065-phase1-stabilization: a cancelled session takes no walk-ins (the server refuses them).
  if (session.cancelled) return true
  if (session.sessionDate !== todayIso()) return false
  const [h, m] = session.endTime.split(':').map(Number)
  const end = new Date()
  end.setHours(h, m, 0, 0)
  return end.getTime() < Date.now() && session.walkInsWaiting === 0 && !session.inWithDoctor
}

function notReadyReason(readiness: DoctorBookingReadiness | undefined): string | null {
  if (!readiness || readiness.bookingReady) return null
  if (!readiness.hasAppointmentTypes) return 'Booking setup incomplete: no appointment types yet.'
  return 'Booking setup incomplete: no fee set.'
}

export interface SessionStepProps {
  clinicId: string
  token: string
  value: string
  onChange: (session: SessionSummary) => void
  // Bumped by the page after a registration so counts refresh immediately, not on the next poll.
  refreshKey: number
  // Lets the page resolve a session pre-selected from the URL (FR-020) once the list has loaded.
  onLoaded?: (sessions: SessionSummary[]) => void
}

export function SessionStep({ clinicId, token, value, onChange, refreshKey, onLoaded }: SessionStepProps) {
  const [sessions, setSessions] = useState<SessionSummary[] | null>(null)
  const [readiness, setReadiness] = useState<Map<string, DoctorBookingReadiness>>(new Map())
  const [liveStatus, setLiveStatus] = useState<Map<string, LiveScheduleStatus>>(new Map())
  const [failed, setFailed] = useState(false)
  // Kept in a ref so a new callback identity on every page render doesn't restart the polling.
  const onLoadedRef = useRef(onLoaded)
  useEffect(() => {
    onLoadedRef.current = onLoaded
  })

  useEffect(() => {
    let cancelled = false
    function load() {
      const today = todayIso()
      Promise.all([
        listSessions(clinicId, token, { from: today, to: today, size: 50 }),
        listDoctorBookingReadiness(clinicId, token),
      ])
        .then(([result, readinessList]) => {
          if (cancelled) return
          setFailed(false)
          setSessions(result.sessions)
          onLoadedRef.current?.(result.sessions)
          setReadiness(new Map(readinessList.map((r) => [r.doctorProfileId, r])))
          result.sessions
            .filter((s) => s.mode === 'FIXED_TIME')
            .forEach((s) => {
              getSessionLiveStatusAsStaff(clinicId, s.sessionId, token)
                .then((status) => {
                  if (cancelled || !status.applicable || !status.status) return
                  setLiveStatus((prev) => new Map(prev).set(s.sessionId, status.status as LiveScheduleStatus))
                })
                .catch(() => {
                  // The live status is a hint on the card; the session stays selectable without it.
                })
            })
        })
        .catch(() => {
          if (!cancelled) setFailed(true)
        })
    }
    load()
    const intervalId = setInterval(load, POLL_INTERVAL_MS)
    return () => {
      cancelled = true
      clearInterval(intervalId)
    }
  }, [clinicId, token, refreshKey])

  if (failed && sessions === null) {
    return (
      <p role="alert" className="text-sm text-red-700">
        Couldn't load today's doctors. It will retry automatically.
      </p>
    )
  }

  if (sessions === null) {
    return (
      <output className="block space-y-2">
        <span className="sr-only">Loading today's doctors…</span>
        <div aria-hidden="true" className="h-16 animate-pulse rounded-lg bg-gray-100" />
        <div aria-hidden="true" className="h-16 animate-pulse rounded-lg bg-gray-100" />
      </output>
    )
  }

  const visible = sessions.filter((s) => !isOver(s) || s.sessionId === value)

  if (visible.length === 0) {
    return <p className="text-sm text-gray-600">No doctor has a session running or starting later today.</p>
  }

  return (
    <div role="radiogroup" aria-label="Doctor session" className="space-y-2">
      {visible.map((session) => {
        const reason = notReadyReason(readiness.get(session.doctorProfileId))
        const selected = session.sessionId === value
        const status = liveStatus.get(session.sessionId)
        return (
          <label
            key={session.sessionId}
            className={`flex items-start gap-3 rounded-lg border p-3 text-sm transition-colors ${
              reason
                ? 'cursor-not-allowed border-gray-200 bg-gray-50 text-gray-500'
                : selected
                  ? 'cursor-pointer border-indigo-600 bg-indigo-50'
                  : 'cursor-pointer border-gray-200 bg-white hover:border-indigo-300'
            }`}
          >
            <input
              type="radio"
              name="walk-in-session"
              className="mt-1"
              checked={selected}
              disabled={reason !== null}
              onChange={() => onChange(session)}
            />
            <span className="min-w-0 flex-1">
              <span className="flex flex-wrap items-baseline justify-between gap-x-3">
                <span className="font-medium text-gray-900">{session.doctorName}</span>
                <span className="tabular-nums text-gray-600">
                  {formatTime(session.startTime)}–{formatTime(session.endTime)}
                </span>
              </span>
              <span className="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-gray-600">
                <span>{session.mode === 'QUEUE' ? 'Token queue' : 'Appointments'}</span>
                {status && <span className="font-medium text-gray-800">{STATUS_LABELS[status]}</span>}
                {session.mode === 'FIXED_TIME' ? (
                  <>
                    <span className="tabular-nums">
                      {session.bookedSlotCount} of {session.totalSlotCount} booked
                    </span>
                    <span className="tabular-nums">
                      {session.walkInsWaiting} walk-in{session.walkInsWaiting === 1 ? '' : 's'} waiting
                    </span>
                  </>
                ) : (
                  // 064-queue-send-in-complete: a Queue session's line is every waiting token.
                  <span className="tabular-nums">{session.walkInsWaiting} waiting</span>
                )}
                <span className={session.inWithDoctor ? 'text-amber-700' : 'text-green-700'}>
                  {session.inWithDoctor ? 'Doctor busy' : 'Doctor free now'}
                </span>
              </span>
              {reason && <span className="mt-1 block text-red-700">{reason}</span>}
            </span>
          </label>
        )
      })}
    </div>
  )
}
