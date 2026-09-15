import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { listQueueSessions, type QueueSession } from './api'
import { loadPatientSession } from '../patient-account/token'
import { PaginationControls } from '../../components/PaginationControls'
import { ListSkeleton } from '../../components/ListSkeleton'
import { IconBadge, SessionIcon, ArrowIcon } from '../../components/adminIcons'
import { todayIsoDate } from './DateStrip'

const SESSIONS_PAGE_SIZE = 15

// "10:15:00" -> "10:15" - the seconds are never meaningful to a patient.
function formatTime(time: string): string {
  return time.slice(0, 5)
}

// Mirrors DateStrip's own Today/Tomorrow labeling so the two browse flows read consistently.
function formatSessionDate(iso: string): string {
  const today = todayIsoDate()
  if (iso === today) return 'Today'
  const tomorrow = new Date(`${today}T00:00:00`)
  tomorrow.setDate(tomorrow.getDate() + 1)
  if (iso === `${tomorrow.getFullYear()}-${String(tomorrow.getMonth() + 1).padStart(2, '0')}-${String(tomorrow.getDate()).padStart(2, '0')}`) {
    return 'Tomorrow'
  }
  return new Date(`${iso}T00:00:00`).toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' })
}

export interface QueueSessionListProps {
  clinicId: string
  doctorId?: string
}

// Groups already-sorted (by sessionDate, then startTime) sessions by doctor, mirroring
// OpenSlotList's identical grouping - a multi-doctor browse (no doctorId filter) reads as
// "Dr. A's sessions, then Dr. B's", not one long list repeating each doctor's name per row.
function groupByDoctor(sessions: QueueSession[]): { doctorProfileId: string; doctorName: string; sessions: QueueSession[] }[] {
  const groups: { doctorProfileId: string; doctorName: string; sessions: QueueSession[] }[] = []
  for (const session of sessions) {
    const existing = groups.find((g) => g.doctorProfileId === session.doctorProfileId)
    if (existing) {
      existing.sessions.push(session)
    } else {
      groups.push({ doctorProfileId: session.doctorProfileId, doctorName: session.doctorName, sessions: [session] })
    }
  }
  return groups
}

// patient-booking-flow-rebuild: the Queue-mode analog of OpenSlotList - browsing Sessions
// (tokens are issued at booking time, not pre-listed) instead of Slots. Picking a session
// carries its already-fetched appointmentTypes forward via router state, so the queue-booking
// form never needs its own raw "Appointment Type ID" field.
export function QueueSessionList({ clinicId, doctorId }: QueueSessionListProps) {
  const [session] = useState(() => loadPatientSession())
  const [sessions, setSessions] = useState<QueueSession[] | null>(null)
  const [totalCount, setTotalCount] = useState(0)
  const [page, setPage] = useState(0)
  const [loadError, setLoadError] = useState<string | null>(null)
  const navigate = useNavigate()

  useEffect(() => {
    setPage(0)
  }, [clinicId, doctorId])

  useEffect(() => {
    if (!session) return
    let cancelled = false

    listQueueSessions(clinicId, session.token, doctorId, { page, size: SESSIONS_PAGE_SIZE })
      .then((result) => {
        if (cancelled) return
        setSessions(result.sessions)
        setTotalCount(result.totalCount)
      })
      .catch(() => {
        if (!cancelled) setLoadError('Could not load queue sessions. Please try again.')
      })

    return () => {
      cancelled = true
    }
  }, [clinicId, doctorId, page, session])

  function handleJoin(queueSession: QueueSession) {
    navigate(`/patient/clinics/${clinicId}/sessions/${queueSession.sessionId}/queue-book`, {
      // patient-booking-visual-polish: doctorName/sessionDate/startTime/endTime ride along too
      // (already fetched here, same as appointmentTypes always has) so QueueBookSlotForm can
      // show a real summary panel instead of a bare form with no idea what it's booking.
      state: {
        appointmentTypes: queueSession.appointmentTypes,
        doctorName: queueSession.doctorName,
        sessionDate: queueSession.sessionDate,
        startTime: queueSession.startTime,
        endTime: queueSession.endTime,
      },
    })
  }

  if (!session) {
    return (
      <div className="max-w-md rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
        <p className="text-sm text-gray-600">Please log in to see and join queue sessions.</p>
      </div>
    )
  }

  if (loadError) {
    return (
      <div className="max-w-md rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
        <p role="alert" className="text-sm text-red-700">
          {loadError}
        </p>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-lg font-semibold text-gray-900">Queue sessions</h1>
        <p className="mt-0.5 text-sm text-gray-600">Pick a session to join its queue and receive a token.</p>
      </div>

      {sessions === null ? (
        <ListSkeleton rows={3} />
      ) : sessions.length === 0 ? (
        <p className="max-w-md rounded-xl border border-gray-200 bg-white p-6 text-sm text-gray-600 shadow-sm">
          No upcoming queue sessions right now.
        </p>
      ) : (
        <>
          <div className="space-y-3">
            {groupByDoctor(sessions).map((group) => (
              <div key={group.doctorProfileId} className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
                <div className="mb-3 flex items-center gap-3">
                  <IconBadge>
                    <SessionIcon />
                  </IconBadge>
                  <p className="text-sm font-semibold text-gray-900">{group.doctorName}</p>
                </div>
                <ul className="space-y-2">
                  {group.sessions.map((queueSession) => (
                    <li key={queueSession.sessionId}>
                      <button
                        type="button"
                        onClick={() => handleJoin(queueSession)}
                        className="flex w-full items-center justify-between gap-3 rounded-lg border border-gray-200 bg-gray-50 px-3.5 py-2.5 text-left transition-all duration-200 ease-out hover:-translate-y-0.5 hover:border-indigo-300 hover:bg-white hover:shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2 active:scale-[0.99]"
                      >
                        <div className="min-w-0">
                          <p className="text-sm font-semibold text-gray-900">{formatSessionDate(queueSession.sessionDate)}</p>
                          <p className="text-sm text-gray-600 tabular-nums">
                            {formatTime(queueSession.startTime)}–{formatTime(queueSession.endTime)}
                          </p>
                        </div>
                        <span className="flex shrink-0 items-center gap-1 text-sm font-semibold text-indigo-600">
                          Join
                          <ArrowIcon />
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
          <PaginationControls
            page={page}
            pageSize={SESSIONS_PAGE_SIZE}
            totalCount={totalCount}
            onPageChange={setPage}
            itemLabel="queue sessions"
          />
        </>
      )}
    </div>
  )
}
